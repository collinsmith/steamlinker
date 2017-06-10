package com.gmail.collinsmith70.steamlinker;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.util.StringConverter;

import static com.gmail.collinsmith70.steamlinker.Main.PREFERENCES;

public class MainController implements Initializable {
  private static final boolean DEBUG_PROPERTY_CHANGES = Main.DEBUG_MODE && true;
  private static final boolean DEBUG_STEAM_NOT_FOUND = Main.DEBUG_MODE && false;

  private static final Logger LOG = Logger.getLogger(MainController.class);
  static {
    PatternLayout layout = new PatternLayout("[%-5p] %c::%M - %m%n");
    LOG.addAppender(new ConsoleAppender(layout, ConsoleAppender.SYSTEM_OUT));
  }

  @FXML private TableView<Game> jfxGames;
  @FXML private ListView<Path> jfxLibs;
  @FXML private ListView<Path> jfxRepos;

  private final ListProperty<Game> games = new SimpleListProperty<>(FXCollections.observableArrayList());
  {
    games.addListener((ListChangeListener<Game>) c -> {
      if (!c.next()) {
        return;
      }

      List<? extends Game> games = c.getAddedSubList();
      Task updateSizeTask = new Task() {
        @Override
        protected Object call() throws Exception {
          for (Game game : games) {
            Path path = game.path.get();
            if (path == null) {
              continue;
            }

            File file = path.toFile();
            game.size.set(FileUtils.sizeOfDirectory(file));
            Platform.runLater(() -> jfxGames.refresh());
          }

          return null;
        }
      };
      new Thread(updateSizeTask).start();
    });
  }
  private final ListProperty<Path> libs = new SimpleListProperty<>(FXCollections.observableArrayList());
  private final ListProperty<Path> repos = new SimpleListProperty<>(FXCollections.observableArrayList());
  private static final StringConverter<ObservableList<Path>> PATHS_CONVERTER = new StringConverter<ObservableList<Path>>() {
    @Override
    @NotNull
    public String toString(@Nullable ObservableList<Path> paths) {
      return paths != null
          ? String.join(";", paths.stream()
            .map(Path::toString)
            .collect(Collectors.joining(";")))
          : "";
    }

    @Override
    @NotNull
    public ObservableList<Path> fromString(@Nullable String string) {
      return string != null && !string.isEmpty()
          ? FXCollections.observableList(Arrays.stream(string.split(";"))
              .map(item -> Paths.get(item))
              .collect(Collectors.toList()))
          : FXCollections.observableArrayList();
    }
  };
  private static final StringConverter<Path> PATH_CONVERTER = new StringConverter<Path>() {
    @Override
    @NotNull
    public String toString(@Nullable Path path) {
      return path != null ? path.toString() : "";
    }

    @Override
    @Nullable
    public Path fromString(@Nullable String string) {
      return string != null && !string.isEmpty() ? Paths.get(string) : null;
    }
  };

  private Window window;
  private Scene scene;

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    Callback<ListView<Path>, ListCell<Path>> cellFactory = param -> new ListCell<Path>() {
      {
        // TODO: .subtract(2) should reference actual border width
        prefWidthProperty().bind(jfxLibs.widthProperty().subtract(2));
        setMaxWidth(Control.USE_PREF_SIZE);
      }

      @Override
      protected void updateItem(Path path, boolean empty) {
        super.updateItem(path, empty);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        if (empty) {
          setText(null);
          setGraphic(null);
        } else if (path != null) {
          // TODO: Configure space properties to work based off of directory watcher so they
          // update as file system changes
          RepoControl repo = new RepoControl();
          repo.textProperty().bind(textProperty());

          File asFile = path.toFile();
          repo.useableSpaceProperty().set(asFile.getUsableSpace());
          repo.totalSpaceProperty().set(asFile.getTotalSpace());

          setText(path.toString());
          setGraphic(repo);
        } else {
          setText(null);
          setGraphic(null);
        }
      }
    };

    jfxLibs.setCellFactory(cellFactory);
    jfxLibs.setItems(libs);

    jfxRepos.setCellFactory(cellFactory);
    jfxRepos.setItems(repos);

    jfxGames.setTableMenuButtonVisible(true);
    jfxGames.getColumns().setAll(createColumns());
    jfxGames.setItems(games);
  }

  @SuppressWarnings("unchecked")
  private TableColumn<Game, ?>[] createColumns() {
    TableColumn<Game, String> titleColumn = new TableColumn<>();
    titleColumn.setText(Bundle.get("game.title"));
    titleColumn.setCellFactory(TextFieldTableCell.forTableColumn());
    titleColumn.setOnEditCommit(event -> {
      Game game = event.getRowValue();
      String name = event.getNewValue();
      game.title.setValue(name);
      PREFERENCES.put(Main.Prefs.GAME_TITLE + game.folder.get(), name);
    });

    TableColumn<Game, Path> pathColumn = new TableColumn<>();
    pathColumn.setText(Bundle.get("game.path"));
    pathColumn.setStyle("-fx-alignment: CENTER-LEFT;");
    //pathColumn.setStyle("-fx-text-overrun: LEADING-ELLIPSIS;");
    pathColumn.setCellFactory(value -> new TableCell<Game, Path>() {
      @Override
      protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);
        updateRepoItem(this, libs, item, empty);
      }
    });

    TableColumn<Game, Number> sizeColumn = new TableColumn<>();
    sizeColumn.setText(Bundle.get("game.size"));
    sizeColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    sizeColumn.setCellFactory(value -> new TableCell<Game, Number>() {
      @Override
      protected void updateItem(Number item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
        } else {
          long value = item.longValue();
          setText(value >= 0L ? Utils.bytesToString(value) : null);
        }
      }
    });

    setupCellValueFactory(titleColumn, Game::titleProperty);
    setupCellValueFactory(pathColumn, Game::repoProperty);
    setupCellValueFactory(sizeColumn, Game::sizeProperty);

    titleColumn.prefWidthProperty().bind(jfxGames.widthProperty().multiply(0.375));
    pathColumn.prefWidthProperty().bind(jfxGames.widthProperty().multiply(0.5));
    sizeColumn.prefWidthProperty().bind(jfxGames.widthProperty().multiply(0.125).subtract(15));

    return (TableColumn<Game, ?>[]) new TableColumn[] { titleColumn, pathColumn, sizeColumn };
  }

  private static <T> void setupCellValueFactory(@NotNull TableColumn<Game, T> column, @NotNull Function<Game, ObservableValue<T>> mapper) {
    column.setCellValueFactory(param -> mapper.apply(param.getValue()));
  }

  public static void updateRepoItem(@NotNull IndexedCell<Path> cell, @NotNull ObservableList<Path> libs, Path item, boolean empty) {
    if (empty || item == null) {
      cell.setGraphic(null);
      cell.setText(null);
    } else if (libs.contains(item)) {
      cell.setGraphic(new ImageView("/mipmap/icon_16x16.png"));
      cell.setText(PATH_CONVERTER.toString(item));
    } else {
      cell.setGraphic(null);
      cell.setText(PATH_CONVERTER.toString(item));
    }
  }

  void bindProperties(@NotNull Preferences prefs) {
    scene = jfxGames.getScene();
    window = scene.getWindow();

    if (DEBUG_PROPERTY_CHANGES) {
      libs.addListener((observable, oldValue, newValue) -> LOG.debug("libs->" + newValue));
      repos.addListener((observable, oldValue, newValue) -> LOG.debug("repos->" + newValue));
    }

    libs.addListener((observable, oldValue, newValue) -> prefs.put(Main.Prefs.LIBS, PATHS_CONVERTER.toString(newValue)));
    libs.addListener((ListChangeListener<Path>) c -> {
      if (!c.next()) {
        return;
      }

      for (Path removed : c.getRemoved()) {
        games.removeIf(game -> game.repo.get().equals(removed));
      }

      for (Path added : c.getAddedSubList()) {
        Task<List<Game>> addGamesTask = new Task<List<Game>>() {
          @Override
          protected List<Game> call() throws Exception {
            return Files.list(added)
                .map(path -> {
                  String fileName = path.getFileName().toString();
                  String name = PREFERENCES.get(Main.Prefs.GAME_TITLE + fileName, fileName);
                  long size = PREFERENCES.getLong(Main.Prefs.GAME_SIZE + fileName, Long.MIN_VALUE);
                  Game result = new Game();
                  try {
                    return result.init(name, path.toRealPath(), size);
                  } catch (IOException e) {
                    LOG.warn("Broken link detected: " + path);
                    return result.init(name, null);
                  }
                })
                .collect(Collectors.toList());
          }
        };
        addGamesTask.exceptionProperty().addListener((observable, oldValue, e) -> {
          LOG.error(e.getMessage(), e);
          Utils.newExceptionAlert(window, e);
        });
        addGamesTask.setOnSucceeded(event -> {
          @SuppressWarnings("unchecked")
          Task<List<Game>> task = (Task<List<Game>>) event.getSource();
          List<Game> games = task.getValue();
          this.games.addAll(games);
        });
        new Thread(addGamesTask).start();
      }
    });
    libs.set(PATHS_CONVERTER.fromString(PREFERENCES.get(Main.Prefs.LIBS, ((Supplier<String>) () -> {
      Path steamDir = Utils.tryFindSteam();
      if (steamDir != null && !DEBUG_STEAM_NOT_FOUND) {
        steamDir = alertSteamFound(window, steamDir);
      } else {
        steamDir = alertSteamNotFound(window);
      }

      return steamDir != null ? steamDir.toString() : "";
    }).get())));

    repos.addListener((observable, oldValue, newValue) -> prefs.put(Main.Prefs.REPOS, PATHS_CONVERTER.toString(newValue)));
    repos.set(PATHS_CONVERTER.fromString(PREFERENCES.get(Main.Prefs.REPOS, "")));
  }

  @Nullable
  private static Path alertSteamFound(@NotNull Window owner, @NotNull Path steamDir) {
    final ButtonType BROWSE = new ButtonType(Bundle.get("button.browse"), ButtonBar.ButtonData.NO);

    Alert steamDetected = new Alert(Alert.AlertType.CONFIRMATION);
    steamDetected.setTitle(Bundle.get("alert.steam.located.title"));
    steamDetected.setHeaderText(null);
    steamDetected.setContentText(Bundle.get("alert.steam.located", steamDir));
    steamDetected.getButtonTypes().add(BROWSE);
    steamDetected.initOwner(owner);
    return steamDetected.showAndWait()
        .map(buttonType -> {
          if (buttonType == BROWSE) {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            return Optional.ofNullable(directoryChooser.showDialog(owner))
                .map(File::toPath)
                .orElse(steamDir);
          } else if (buttonType == ButtonType.OK) {
            return steamDir;
          } else {
            return null;
          }
        })
        .orElse(null);
  }

  @Nullable
  private static Path alertSteamNotFound(@NotNull Window owner) {
    final ButtonType BROWSE = new ButtonType(Bundle.get("button.browse"), ButtonBar.ButtonData.NO);

    Alert steamNotDetected = new Alert(Alert.AlertType.CONFIRMATION);
    steamNotDetected.setTitle(Bundle.get("alert.steam.located.failed.title"));
    steamNotDetected.setHeaderText(null);
    steamNotDetected.setContentText(Bundle.get("alert.steam.located.failed"));
    steamNotDetected.getButtonTypes().setAll(ButtonType.CANCEL, BROWSE);
    steamNotDetected.initOwner(owner);
    return steamNotDetected.showAndWait()
        .map(buttonType -> {
          if (buttonType == BROWSE) {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            return Optional.ofNullable(directoryChooser.showDialog(owner))
                .map(File::toPath)
                .orElse(null);
          } else {
            return null;
          }
        })
        .orElse(null);
  }
}
