package com.gmail.collinsmith70.steamlinker;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;

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
import javafx.beans.binding.Bindings;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;

import static com.gmail.collinsmith70.steamlinker.Main.PREFERENCES;

public class MainController implements Initializable {
  private static final boolean DEBUG_PROPERTY_CHANGES = Main.DEBUG_MODE && true;
  private static final boolean DEBUG_STEAM_NOT_FOUND = Main.DEBUG_MODE && false;

  private static final Logger LOG = Logger.getLogger(MainController.class);
  static {
    PatternLayout layout = new PatternLayout("[%-5p] %c::%M - %m%n");
    LOG.addAppender(new ConsoleAppender(layout, ConsoleAppender.SYSTEM_OUT));
  }

  @FXML private ReposControl jfxLibs;
  @FXML private ReposControl jfxRepos;

  @FXML private TableView<Game> jfxGames;
  @FXML private TableColumn<Game, String> jfxGamesTitleColumn;
  @FXML private TableColumn<Game, Path> jfxGamesPathColumn;
  @FXML private TableColumn<Game, Number> jfxGamesSizeColumn;

  private final ListProperty<Game> games = new SimpleListProperty<>(FXCollections.observableArrayList()); {
    games.addListener((ListChangeListener<Game>) c -> {
      while (c.next()) {
        if (c.wasAdded()) {
          List<? extends Game> games = c.getAddedSubList();
          Task updateSizeTask = new Task() {
            @Override
            protected Object call() throws Exception {
              for (Game game : games) {
                if (game.brokenJunction.get()) {
                  continue;
                }

                File file = game.path.get().toFile();
                game.size.set(FileUtils.sizeOfDirectory(file));
                Platform.runLater(() -> jfxGames.refresh());
              }

              return null;
            }
          };
          new Thread(updateSizeTask).start();
        }
      }
    });
  }
  private final ListProperty<ScrollBarMark> marks = new SimpleListProperty<>(FXCollections.observableArrayList()); {
    games.addListener((ListChangeListener<? super Game>) c -> {
      while (c.next()) {
        if (c.wasAdded() || c.wasReplaced()) {
          if (c.wasReplaced()) {
            marks.forEach(ScrollBarMark::detach);
            marks.clear();
          }

          final ScrollBar scrollBar = (ScrollBar) jfxGames.lookup(".scroll-bar:vertical");
          for (int i = c.getFrom(); i < c.getTo(); i++) {
            final Game game = games.get(i);
            if (!game.brokenJunction.get()) {
              continue;
            }

            ScrollBarMark mark = new ScrollBarMark();
            mark.setPosition((double) (i-1) / games.size());
            mark.attach(scrollBar);
            marks.add(mark);
          }
        } else if (c.wasRemoved()) {
          marks.forEach(ScrollBarMark::detach);
          marks.clear();

          final ScrollBar scrollBar = (ScrollBar) jfxGames.lookup(".scroll-bar:vertical");
          final ObservableList<? extends Game> games = c.getList();
          final int size = games.size();
          for (int i = 0; i < size; i++) {
            final Game game = games.get(i);
            if (!game.brokenJunction.get()) {
              continue;
            }

            ScrollBarMark mark = new ScrollBarMark();
            mark.setPosition((double) i / games.size());
            mark.attach(scrollBar);
            marks.add(mark);
          }
        }
      }
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
    jfxLibs.setItems(libs);
    jfxLibs.getListView().prefHeightProperty().bind(
        Bindings.max(Bindings.min(3, Bindings.size(libs)), 1)
            .multiply(63));

    jfxRepos.setItems(repos);

    initializeColumns();
    jfxGames.setTableMenuButtonVisible(true);
    jfxGames.setItems(games);
    jfxGames.setEditable(true);
    jfxGames.setRowFactory(callback -> {
      TableRow<Game> row = new TableRow<Game>() {
        private static final String jfxBrokenJunctionRow = "jfxBrokenJunctionRow";

        @Override
        protected void updateItem(Game item, boolean empty) {
          super.updateItem(item, empty);
          List<String> styleClass = getStyleClass();
          if (empty || item == null || !item.brokenJunction.get()) {
            styleClass.remove(jfxBrokenJunctionRow);
            setTooltip(null);
            return;
          }

          if (!styleClass.contains(jfxBrokenJunctionRow)) {
            styleClass.add(jfxBrokenJunctionRow);
            Tooltip tooltip = new Tooltip(Bundle.get("tooltip.broken.junction", item.path.get()));
            Tooltip.install(this, tooltip);
          }
        }
      };
      return row;
    });
  }

  @SuppressWarnings("unchecked")
  private void initializeColumns() {
    setupCellValueFactory(jfxGamesTitleColumn, Game::titleProperty);
    jfxGamesTitleColumn.setCellFactory(Callback -> new TextFieldTableCell<Game, String>(new DefaultStringConverter()) {
      @Override
      public void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
          setGraphic(null);
          setEditable(true);
        }

        TableRow<Game> row = getTableRow();
        Game game = row.getItem();
        if (game != null && game.brokenJunction.get()) {
          Node graphic = new FontIcon("gmi-warning:20:yellow");
          /*graphic.setOnMouseClicked(event -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText(null);
            alert.setContentText(Bundle.get("tooltip.broken.junction", game.path.get()));
            alert.initOwner(window);
            alert.show();

            //tooltip.show(graphic, event.getScreenX(), event.getScreenY());
            //tooltip.setAutoHide(true);
            event.consume();
          });*/
          setGraphic(graphic);
          setEditable(false);
        } else {
          setGraphic(null);
          setEditable(true);
        }
      }
    });
    jfxGamesTitleColumn.setOnEditCommit(event -> {
      Game game = event.getRowValue();
      String name = event.getNewValue();
      game.title.setValue(name);
      PREFERENCES.put(Main.Prefs.GAME_TITLE + game.folder.get(), name);
    });

    setupCellValueFactory(jfxGamesPathColumn, Game::repoProperty);
    jfxGamesPathColumn.setCellFactory(value -> new TableCell<Game, Path>() {
      @Override
      protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);
        updateRepoItem(this, libs, item, empty);
      }
    });

    setupCellValueFactory(jfxGamesSizeColumn, Game::sizeProperty);
    jfxGamesSizeColumn.setCellFactory(value -> new TableCell<Game, Number>() {
      ProgressIndicator spinner = new ProgressIndicator();
      {
        spinner.prefWidthProperty().set(16);
        spinner.prefHeightProperty().set(16);
      }

      @Override
      protected void updateItem(Number item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
        } else {
          long value = item.longValue();
          setText(value >= 0L ? Utils.bytesToString(value) : null);
          setGraphic(null);
        }

        TableRow<Game> row = getTableRow();
        Game game = row.getItem();
        if (getText() == null && game != null && !game.brokenJunction.get()) {
          setGraphic(spinner);
        }
      }
    });

    jfxGamesTitleColumn.prefWidthProperty().bind(jfxGames.widthProperty().multiply(0.375));
    jfxGamesPathColumn.prefWidthProperty().bind(jfxGames.widthProperty().multiply(0.5));
    jfxGamesSizeColumn.prefWidthProperty().bind(jfxGames.widthProperty().multiply(0.125).subtract(15));
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
                    try {
                      return result.init(name, Utils.toRealPath(path), true);
                    } catch (Exception ex) {
                      LOG.error(ex.getMessage(), ex);
                    }

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
