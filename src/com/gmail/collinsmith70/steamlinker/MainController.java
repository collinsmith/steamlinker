package com.gmail.collinsmith70.steamlinker;

import com.google.common.collect.Sets;

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
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
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
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.converter.DefaultStringConverter;

import static com.gmail.collinsmith70.steamlinker.Main.PREFERENCES;
import static com.gmail.collinsmith70.steamlinker.Utils.PATHS_CONVERTER;
import static com.gmail.collinsmith70.steamlinker.Utils.PATH_CONVERTER;

public class MainController implements Initializable {
  private static final boolean DEBUG_PROPERTY_CHANGES = Main.DEBUG_MODE && true;
  private static final boolean FORCE_STEAM_NOT_FOUND = Main.DEBUG_MODE && false;
  private static final boolean DEBUG_AUTO_ADD_REPOS = Main.DEBUG_MODE && true;
  private static final boolean DEBUG_TRANSFERS = Main.DEBUG_MODE && true;

  private static final boolean USE_TOOLTIP_FOR_BROKEN_LINKS = true;

  private static final Logger LOG = Logger.getLogger(MainController.class);
  static {
    PatternLayout layout = new PatternLayout("[%-5p] %c::%M - %m%n");
    LOG.addAppender(new ConsoleAppender(layout, ConsoleAppender.SYSTEM_OUT));
  }

  private Stage stage;
  private Scene scene;

  @FXML private ReposControl jfxLibs;
  @FXML private ReposControl jfxRepos;
  @FXML private TransfersControl jfxTransfers;

  @FXML private TableView<Game> jfxGames;
  @FXML private TableColumn<Game, String> jfxGamesTitleColumn;
  @FXML private TableColumn<Game, Path> jfxGamesPathColumn;
  @FXML private TableColumn<Game, Number> jfxGamesSizeColumn;

  private final ListProperty<Game> games = new SimpleListProperty<>(FXCollections.observableArrayList()); {
    games.addListener((ListChangeListener<Game>) c -> {
      while (c.next()) {
        if (c.wasAdded()) {
          List<Game> emptyDirectories = new CopyOnWriteArrayList<>();
          List<? extends Game> games = c.getAddedSubList();
          Task updateSizeTask = new Task() {
            @Override
            protected Object call() throws Exception {
              for (Game game : games) {
                if (game.brokenJunction.get()) {
                  continue;
                }

                File file = game.path.get().toFile();
                long size = FileUtils.sizeOfDirectory(file);
                if (PREFERENCES.getLong(Main.Prefs.GAME_SIZE + game.folder.get(), Long.MIN_VALUE) != size) {
                  PREFERENCES.putLong(Main.Prefs.GAME_SIZE + game.folder.get(), size);
                }
                game.size.set(size);
                if (size == 0L) {
                  emptyDirectories.add(game);
                }
                Platform.runLater(() -> jfxGames.refresh());
              }

              return null;
            }
          };
          updateSizeTask.setOnSucceeded(event -> {
            if (!emptyDirectories.isEmpty()) {
              //alertEmptyDirectories(emptyDirectories);
            }
          });
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
  private final ListProperty<Game.Transfer> transfers = new SimpleListProperty<>(FXCollections.observableArrayList());

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
            return;
          }

          if (!styleClass.contains(jfxBrokenJunctionRow)) {
            styleClass.add(jfxBrokenJunctionRow);
          }
        }
      };
      row.setOnDragDetected(event -> {
        event.consume();
        TableView.TableViewSelectionModel<Game> selectionModel = jfxGames.getSelectionModel();
        List<Game> selectedItems = selectionModel.getSelectedItems();
        if (selectedItems.isEmpty()) {
          return;
        }

        List<Game> serializableList = selectedItems.stream()
            .filter(game -> !game.brokenJunction.get())
            .collect(Collectors.toList());
        if (serializableList.isEmpty()) {
          return;
        }

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);

        ClipboardContent content = new ClipboardContent();
        content.put(Game.AS_LIST, serializableList);

        Dragboard db = jfxGames.startDragAndDrop(TransferMode.ANY);
        db.setDragView(row.snapshot(params, null));
        db.setContent(content);
      });
      return row;
    });
    jfxGames.setOnMouseClicked(event -> {
      if (event.getButton() == MouseButton.PRIMARY && games.isEmpty()) {
        event.consume();
        jfxLibs.fireAddRepo();
      }
    });

    jfxTransfers.libsProperty().bind(libs);
    jfxTransfers.setItems(transfers);
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
          Node graphic = new FontIcon("gmi-warning:18:yellow");
          Tooltip tooltip = new Tooltip(Bundle.get("tooltip.broken.junction", game.path.get()));
          tooltip.setFont(new Font(tooltip.getFont().getName(), 12));
          Tooltip.install(graphic, tooltip);
          graphic.setOnMouseClicked(event -> {
            if (USE_TOOLTIP_FOR_BROKEN_LINKS) {
              tooltip.show(graphic, event.getScreenX(), event.getScreenY());
              tooltip.setAutoHide(true);
              event.consume();
            } else {
              Alert alert = new Alert(Alert.AlertType.INFORMATION);
              alert.setHeaderText(null);
              alert.setContentText(Bundle.get("tooltip.broken.junction", game.path.get()));
              alert.initOwner(stage);
              alert.show();
            }
          });
          setGraphic(graphic);
          setEditable(false);
        } else if (game != null && game.size.get() == 0L) {
          Node graphic = new FontIcon("gmi-warning:18:orange");
          Tooltip tooltip = new Tooltip(Bundle.get("tooltip.empty.directory", game.path.get()));
          tooltip.setFont(new Font(tooltip.getFont().getName(), 12));
          Tooltip.install(graphic, tooltip);
          graphic.setOnMouseClicked(event -> {
            if (USE_TOOLTIP_FOR_BROKEN_LINKS) {
              tooltip.show(graphic, event.getScreenX(), event.getScreenY());
              tooltip.setAutoHide(true);
              event.consume();
            } else {
              Alert alert = new Alert(Alert.AlertType.INFORMATION);
              alert.setHeaderText(null);
              alert.setContentText(Bundle.get("tooltip.empty.directory", game.path.get()));
              alert.initOwner(stage);
              alert.show();
            }
          });
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
        configureRepoCell(this, libs.get(), item, empty);
        if (item != null) {
          Game game = (Game) getTableRow().getItem();
          if (game != null && game.brokenJunction.get()) {
            setText(null);
          }
        }
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

    jfxGamesTitleColumn.prefWidthProperty().bind(jfxGames.widthProperty().multiply(0.35));
    jfxGamesPathColumn.prefWidthProperty().bind(jfxGames.widthProperty().multiply(0.5));
    jfxGamesSizeColumn.prefWidthProperty().bind(jfxGames.widthProperty().multiply(0.15).subtract(15));
  }

  private static <T> void setupCellValueFactory(@NotNull TableColumn<Game, T> column, @NotNull Function<Game, ObservableValue<T>> mapper) {
    column.setCellValueFactory(param -> mapper.apply(param.getValue()));
  }

  public static void configureRepoCell(@NotNull IndexedCell<Path> cell, @NotNull ObservableList<Path> libs, Path item, boolean empty) {
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

  void configure(@NotNull Stage stage, @NotNull Scene scene) {
    this.stage = stage;
    this.scene = scene;

    if (DEBUG_PROPERTY_CHANGES) {
      libs.addListener((observable, oldValue, newValue) -> LOG.debug("libs->" + newValue));
      repos.addListener((observable, oldValue, newValue) -> LOG.debug("repos->" + newValue));
    }
  }

  void bindPreferences(@NotNull Preferences prefs) {
    libs.addListener((observable, oldValue, newValue) -> prefs.put(Main.Prefs.LIBS, PATHS_CONVERTER.toString(newValue)));
    libs.addListener((ListChangeListener<Path>) c -> {
      if (!c.next()) {
        return;
      }

      for (Path removed : c.getRemoved()) {
        games.removeIf(game -> game.repo.get().equals(removed));
      }

      for (Path added : c.getAddedSubList()) {
        List<Game> brokenJunctions = new CopyOnWriteArrayList<>();
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
                      result.init(name, path, true);
                      brokenJunctions.add(result);
                      return result;
                    } catch (Throwable t) {
                      LOG.error(t.getMessage(), t);
                    }

                    return result.init(name, null);
                  }
                })
                .collect(Collectors.toList());
          }
        };
        addGamesTask.exceptionProperty().addListener((observable, oldValue, e) -> {
          LOG.error(e.getMessage(), e);
          Utils.newExceptionAlert(stage, e);
        });
        addGamesTask.setOnSucceeded(event -> {
          @SuppressWarnings("unchecked")
          Task<List<Game>> task = (Task<List<Game>>) event.getSource();
          List<Game> games = task.getValue();
          this.games.addAll(games);

          if (DEBUG_AUTO_ADD_REPOS) {
            LOG.info("automatically adding repositories...");
          }

          Set<Path> repos = Sets.newHashSet(this.repos.get());
          games.stream()
              .map(game -> game.repo.get())
              .filter(path -> !libs.contains(path))
              .forEach(junction -> {
                try {
                  repos.add(junction.toRealPath());
                } catch (IOException e) {
                  LOG.error(e.getMessage(), e);
                }
              });

          if (this.repos.size() < repos.size()) {
            this.repos.set(FXCollections.observableArrayList(repos));
          }

          if (!brokenJunctions.isEmpty()) {
            alertBrokenJunctions(brokenJunctions);
          }
        });
        new Thread(addGamesTask).start();
      }
    });
    String value = PREFERENCES.get(Main.Prefs.LIBS, null);
    if (value == null || value.isEmpty()) {
      value = ((Supplier<String>) () -> {
        Path lib = Main.service().findSteam();
        if (lib != null && !FORCE_STEAM_NOT_FOUND) {
          lib = alertLibFound(stage, lib);
        } else {
          lib = alertLibNotFound(stage);
        }

        return lib != null ? lib.toString() : "";
      }).get();
    }
    libs.set(PATHS_CONVERTER.fromString(value));

    repos.addListener((observable, oldValue, newValue) -> prefs.put(Main.Prefs.REPOS, PATHS_CONVERTER.toString(newValue)));
    repos.set(PATHS_CONVERTER.fromString(PREFERENCES.get(Main.Prefs.REPOS, "")));
  }

  @Nullable
  private static Path alertLibFound(@NotNull Window owner, @NotNull Path lib) {
    final AtomicReference<Path> result = new AtomicReference<>(lib);
    final ButtonType BROWSE = new ButtonType(Bundle.get("button.browse"), ButtonBar.ButtonData
        .LEFT);

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle(Bundle.get("alert.steam.located.title"));
    alert.setHeaderText(null);
    alert.setContentText(Bundle.get("alert.steam.located", lib));
    alert.getButtonTypes().add(BROWSE);
    alert.initOwner(owner);
    alert.getDialogPane().lookupButton(BROWSE).addEventFilter(ActionEvent.ACTION, event -> {
      DirectoryChooser directoryChooser = new DirectoryChooser();
      Path selectedPath = Optional.ofNullable(directoryChooser.showDialog(owner))
          .map(File::toPath)
          .orElse(null);
      if (selectedPath == null) {
        event.consume();
      } else {
        result.set(selectedPath);
        alert.setResult(ButtonType.OK);
      }
    });
    return alert.showAndWait()
        .map(buttonType -> {
          if (buttonType == ButtonType.OK || buttonType == BROWSE) {
            return result.get();
          } else {
            return null;
          }
        })
        .orElse(null);
  }

  @Nullable
  private static Path alertLibNotFound(@NotNull Window owner) {
    final AtomicReference<Path> result = new AtomicReference<>();
    final ButtonType BROWSE = new ButtonType(Bundle.get("button.browse"), ButtonBar.ButtonData
        .LEFT);

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle(Bundle.get("alert.steam.located.failed.title"));
    alert.setHeaderText(null);
    alert.setContentText(Bundle.get("alert.steam.located.failed"));
    alert.getButtonTypes().setAll(ButtonType.CANCEL, BROWSE);
    alert.initOwner(owner);
    alert.getDialogPane().lookupButton(BROWSE).addEventFilter(ActionEvent.ACTION, event -> {
      DirectoryChooser directoryChooser = new DirectoryChooser();
      Path selectedPath = Optional.ofNullable(directoryChooser.showDialog(owner))
          .map(File::toPath)
          .orElse(null);
      if (selectedPath == null) {
        event.consume();
      } else {
        result.set(selectedPath);
      }
    });
    return alert.showAndWait()
        .map(buttonType -> {
          if (buttonType == BROWSE) {
            return result.get();
          } else {
            return null;
          }
        })
        .orElse(null);
  }

  private void alertBrokenJunctions(@NotNull List<Game> brokenJunctions) {
    StringBuilder sb = new StringBuilder();
    for (Game broken : brokenJunctions) {
      sb.append(broken.path.get());
      sb.append("\n");
    }

    sb.deleteCharAt(sb.length() - 1);

    final ButtonType REPAIR = new ButtonType(Bundle.get("button.repair"), ButtonBar.ButtonData.APPLY);

    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle(Bundle.get("alert.broken.junctions.title"));
    alert.setContentText(Bundle.get("alert.broken.junctions"));
    alert.setHeaderText(null);
    alert.getButtonTypes().setAll(ButtonType.CANCEL, REPAIR);
    alert.initOwner(stage);
    alert.getDialogPane().setExpandableContent(new TextArea(
        Bundle.get("alert.broken.junctions.expanded", sb.toString())
    ));
    alert.showAndWait()
        .filter(buttonType -> buttonType == REPAIR)
        .ifPresent(buttonType -> {
          LOG.info("Repairing broken junctions...");
          for (Game game : brokenJunctions) {
            try {
              Main.service().deleteJunction(game.path.get());
              games.remove(game);
            } catch (Throwable t) {
              LOG.error(t.getMessage(), t);
            }
          }
        });
  }

  private void alertEmptyDirectories(@NotNull List<Game> emptyDirectories) {
    StringBuilder sb = new StringBuilder();
    for (Game broken : emptyDirectories) {
      sb.append(broken.path.get());
      sb.append("\n");
    }

    sb.deleteCharAt(sb.length() - 1);

    final ButtonType REPAIR = new ButtonType(Bundle.get("button.repair"), ButtonBar.ButtonData.APPLY);

    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle(Bundle.get("alert.empty.directories.title"));
    alert.setContentText(Bundle.get("alert.empty.directories"));
    alert.setHeaderText(null);
    alert.getButtonTypes().setAll(ButtonType.CANCEL, REPAIR);
    alert.initOwner(stage);
    alert.getDialogPane().setExpandableContent(new TextArea(
        Bundle.get("alert.empty.directories.expanded", sb.toString())
    ));
    alert.showAndWait()
        .filter(buttonType -> buttonType == REPAIR)
        .ifPresent(buttonType -> {
          LOG.info("Repairing empty directories...");
          for (Game game : emptyDirectories) {
            try {
              Path path = game.path.get();
              if (libs.contains(game.repo.get())) {
                tryDelete(path);
              } else {
                tryDelete(path);
                // FIXME: Deleting in this case causing problems - can't get real path (of junction)
                //Main.service().deleteJunction(path);
              }
              games.remove(game);
            } catch (Throwable t) {
              LOG.error(t.getMessage(), t);
            }
          }
        });
  }

  @FXML
  private void onTransfer(@NotNull Game.TransferEvent event) {
    final List<Game> theseGames = this.games.get();
    event.games.forEach(game -> LOG.info(game.path.get() + "->" + event.dstRepo));
    event.games.stream()
        .map(game -> theseGames.stream()
            .filter(tmp -> tmp.path.get().equals(game.path.get()))
            .findFirst()
            .orElseThrow(AssertionError::new))
        .forEach(game -> {
          Game.Transfer transfer = game.createTransfer(event.dstRepo, PREFERENCES.getBoolean(Main.Prefs.VERIFY, true));
          transfers.add(transfer);

          final Path src = transfer.src.get();
          final Path srcRepo = transfer.srcRepo.get();
          final Path dst = transfer.dst.get();
          final Path dstRepo = transfer.dstRepo.get();

          transfer.setOnFailed(onFailed -> {
            Throwable throwable = transfer.getException();
            LOG.error(throwable.getMessage(), throwable);
            if (throwable instanceof NotEnoughSpaceException) {
              Alert alert = new Alert(Alert.AlertType.ERROR);
              alert.setTitle(Bundle.get("alert.transfer.wont.fit.title"));
              alert.setContentText(Bundle.get("alert.transfer.wont.fit"));
              alert.setHeaderText(null);
              alert.getButtonTypes().setAll(ButtonType.OK);
              alert.initOwner(stage);
              alert.getDialogPane().setExpandableContent(new TextArea(
                  Bundle.get("alert.transfer.wont.fit.expanded",
                      Utils.bytesToString(transfer.totalSize.get()),
                      Utils.bytesToString(transfer.dstRepo.get().toFile().getUsableSpace()),
                      transfer.game.title.get())
              ));
              alert.show();
            } else {
              Utils.newExceptionAlert(stage, throwable).show();
            }
          });

          if (libs.contains(srcRepo) && libs.contains(dstRepo)) {
            // TODO: Support lib to lib transfers
            Utils.newExceptionAlert(stage, new UnsupportedOperationException(
                "Cannot transfer games from one Steam Library to another yet!"))
                .show();
            return;
          } else if (repos.contains(srcRepo) && repos.contains(dstRepo)) {
            // TODO: Support repo to repo transfers
            Utils.newExceptionAlert(stage, new UnsupportedOperationException(
                "Cannot transfer games from one repository to another yet!"))
                .show();
            return;
          }

          transfer.status.addListener((observable, oldValue, newValue) -> {
            if (!newValue.equals("copying")) {
              return;
            }

            if (libs.contains(dstRepo)) {
              // Transferring from repo to lib
              if (!repos.contains(srcRepo)) {
                Utils.newExceptionAlert(stage, new UnsupportedOperationException(
                    "Expected repo to lib transfer, but " + srcRepo + " is not a registered repo."))
                    .show();
                return;
              }

              try {
                if (Main.service().isJunction(dst)) {
                  if (DEBUG_TRANSFERS) LOG.info("removing junction in steam lib: " + dst);
                  Main.service().deleteJunction(dst);
                }
              } catch (Exception e) {
                Utils.newExceptionAlert(stage, e).show();
              }

              Platform.runLater(() -> transfer.setOnSucceeded(onSucceeded -> {
                boolean deleteRepoCopy = PREFERENCES.getBoolean(Main.Prefs.DELETE_REPO_COPY, true);
                if (!deleteRepoCopy) {
                  tryDelete(src);
                } else if (DEBUG_TRANSFERS) LOG.info("preserving repo copy: " + dst);
                jfxRepos.lookup(srcRepo).refresh();
                ((RepoControl) event.getTarget()).refresh();
                stage.toFront();
              }));
            } else {
              // Transferring from lib to repo
              if (!repos.contains(dstRepo)) {
                Utils.newExceptionAlert(stage, new UnsupportedOperationException(
                    "Expected lib to repo transfer, but " + dstRepo + " is not a registered repo."))
                    .show();
                return;
              }

              Platform.runLater(() -> transfer.setOnSucceeded(onSucceeded -> {
                createJunction(src, dst);
                jfxLibs.lookup(srcRepo).refresh();
                ((RepoControl) event.getTarget()).refresh();
                stage.toFront();
              }));
            }
          });
          new Thread(transfer).start();
        });
  }

  private void createJunction(@NotNull Path path, @NotNull Path target) {
    try {
      if (path.toFile().exists()) {
        if (DEBUG_TRANSFERS) LOG.info("junction path exists: " + path);
        tryDelete(path);
      }

      Main.service().createJunction(path, target);
      LOG.info(path + " <<===>> " + target);
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
      Utils.newExceptionAlert(stage, e).show();
    }
  }

  private void tryDelete(@NotNull Path dir) {
    assert Files.isDirectory(dir);
    try {
      if (DEBUG_TRANSFERS) LOG.info("deleting " + dir);
      FileUtils.deleteDirectory(dir.toFile());
    } catch (IOException e) {
      LOG.error(e.getMessage(), e);
      Utils.newExceptionAlert(stage, e).show();
    }
  }
}
