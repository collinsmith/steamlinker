package com.gmail.collinsmith70.steamlinker;

import com.google.common.collect.Sets;

import com.gmail.collinsmith70.steamlinker.Game.Transfer;

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
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ListPropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class Main extends Application {

  private static final boolean RESET_PREFERENCES = false;
  private static final boolean DEBUG_PREFERENCES = true;
  private static final boolean DEBUG_PROPERTIES = true;
  private static final boolean DEBUG_REPO_AUTOCONFIG = true;
  private static final boolean DEBUG_GAMES_REFRESH = true;
  private static final boolean DEBUG_GAMES_QUEUE = true;

  private static final Logger LOG = Logger.getLogger(Main.class);
  static {
    PatternLayout layout = new PatternLayout("[%-5p] %c::%M - %m%n");
    LOG.addAppender(new ConsoleAppender(layout, ConsoleAppender.SYSTEM_OUT));
  }

  private static final Preferences PREFERENCES = Preferences.userNodeForPackage(Main.class);
  static {
    if (RESET_PREFERENCES) {
      try {
        LOG.debug("Clearing preferences...");
        PREFERENCES.clear();
      } catch (BackingStoreException e) {
        LOG.error(e.getMessage(), e);
      }
    }

    if (DEBUG_PREFERENCES) {
      PREFERENCES.addPreferenceChangeListener(event -> LOG.debug(event.getKey() + "->" + event.getNewValue()));
    }
  }
  private interface Prefs {
    String STEAM_DIR = "config.dirs.steam";
    String REPOS = "config.repos";
    String GAME_TITLE = "game.title.";
    String GAME_SIZE = "game.size.";
    String MAX_TRANSFERS = "max.concurrent.transfers";
  }

  public static void main(String[] args) {
    launch(args);
  }

  // FIXME: this doesn't resolve correctly in lambdas if not static final
  private static final ObjectProperty<Path> steamDir = new ObjectPropertyBase<Path>() {
    @Override
    public Object getBean() {
      return null;
    }

    @Override
    public String getName() {
      return "steamDir";
    }
  };
  private static final StringConverter<Path> PATH_STRING_CONVERTER = new StringConverter<Path>() {
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

  // FIXME: this doesn't resolve correctly in lambdas if not static final
  private static final ListProperty<Path> repos = new ListPropertyBase<Path>() {
    @Override
    public Object getBean() {
      return null;
    }

    @Override
    public String getName() {
      return "repos";
    }
  };
  private static final StringConverter<ObservableList<Path>> PATHS_STRING_CONVERTER = new StringConverter<ObservableList<Path>>() {
    @Override
    @NotNull
    public String toString(@Nullable ObservableList<Path> repos) {
      return repos != null
          ? String.join(";", repos.stream()
              .map(Path::toString)
              .collect(Collectors.joining(";")))
          : "";
    }

    @Override
    @Nullable
    public ObservableList<Path> fromString(@Nullable String string) {
      return string != null && !string.isEmpty()
          ? FXCollections.observableList(
              Arrays.stream(string.split(";"))
                  .map(item -> Paths.get(item))
                  .collect(Collectors.toList()))
          : FXCollections.observableArrayList();
    }
  };

  @NotNull
  private final ThreadPoolExecutor executors;
  {
    int nThreads = maxTransfers.get();
    executors = new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    maxTransfers.addListener((observable, oldValue, newValue) -> executors.setMaximumPoolSize(newValue.intValue()));
  }

  private static final IntegerProperty maxTransfers = new SimpleIntegerProperty(1);
  static {
    Main.maxTransfers.setValue(PREFERENCES.getInt(Prefs.MAX_TRANSFERS, 1));
    Main.maxTransfers.addListener((observable, oldValue, newValue) -> PREFERENCES.putInt(Prefs.MAX_TRANSFERS, newValue != null ? newValue.intValue() : 1));
  }

  @Override
  public void start(Stage stage) throws Exception {
    URL location = Main.class.getResource("/layout/main_layout.fxml");
    Parent root = FXMLLoader.load(location, Bundle.BUNDLE);
    Scene scene = new Scene(root);
    doStyleSheets(scene);

    stage.setTitle(Bundle.get("name"));
    doIcons(stage.getIcons());
    stage.setScene(scene);
    stage.show();

    bindProperties();

    configureSteamDir(scene);
    configureEditSteamDir(scene);
    configureRefreshSteamDir(scene);
    configureSteamRepo(scene);
    configureRepos(scene);
    configureGamesTable(scene);
    configureTransfers(scene);
  }

  private void doStyleSheets(@NotNull Scene scene) {
    ObservableList<String> styleSheets = scene.getStylesheets();
    styleSheets.add("/style/listview.css");
  }

  @NotNull
  private ObservableList<Image> doIcons(@NotNull ObservableList<Image> icons) {
    icons.add(new Image("mipmap/icon_16x16.png"));
    icons.add(new Image("mipmap/icon_32x32.png"));
    return icons;
  }

  private void bindProperties() {
    LOG.debug("Binding properties...");
    if (DEBUG_PROPERTIES) {
      steamDir.addListener(((observable, oldValue, newValue) -> LOG.debug("steamDir->" + steamDir.get())));
      repos.addListener(((observable, oldValue, newValue) -> LOG.debug("repos->" + repos.get())));
    }

    steamDir.set(PATH_STRING_CONVERTER.fromString(PREFERENCES.get(Prefs.STEAM_DIR, "")));
    repos.set(PATHS_STRING_CONVERTER.fromString(PREFERENCES.get(Prefs.REPOS, "")));

    // Some change-listeners set by controllers
    //steamDir.addListener((observable, oldValue, newValue) -> PREFERENCES.put(Prefs.STEAM_DIR, PATH_STRING_CONVERTER.toString(newValue)));
    repos.addListener((observable, oldValue, newValue) -> PREFERENCES.put(Prefs.REPOS, PATHS_STRING_CONVERTER.toString(newValue)));
  }

  @Nullable
  private Path configureSteamDir(@NotNull Scene scene) {
    TextInputControl tfSteamDir = (TextInputControl) scene.lookup("#tfSteamDir");
    tfSteamDir.textProperty().bindBidirectional(this.steamDir, PATH_STRING_CONVERTER);
    tfSteamDir.setOpacity(1.0);
    PREFERENCES.addPreferenceChangeListener(event -> {
      if (event.getKey().equals(Prefs.STEAM_DIR)) {
        Platform.runLater(() -> tfSteamDir.setText(event.getNewValue()));
      }
    });

    return this.steamDir.get();
  }

  private void configureEditSteamDir(@NotNull Scene scene) {
    Button btnEditSteamDir = (Button) scene.lookup("#btnEditSteamDir");
    btnEditSteamDir.setOnAction(event -> {
      DirectoryChooser directoryChooser = new DirectoryChooser();
      Path steamDir = this.steamDir.get();
      if (steamDir != null) {
        directoryChooser.setInitialDirectory(steamDir.toFile());
      }

      Optional.ofNullable(directoryChooser.showDialog(scene.getWindow()))
          .filter(dir -> DEBUG_REPO_AUTOCONFIG || !dir.toPath().equals(steamDir))
          .ifPresent(dir -> PREFERENCES.put(Prefs.STEAM_DIR, dir.getAbsolutePath()));
      event.consume();
    });
  }

  private void configureRefreshSteamDir(@NotNull Scene scene) {
    Button btnRefreshSteamDir = (Button) scene.lookup("#btnRefreshSteamDir");
    btnRefreshSteamDir.setOnAction(event -> updateGames(scene));
    if (this.steamDir.get() != null) {
      btnRefreshSteamDir.setDisable(false);
    } else {
      this.steamDir.addListener((observable, oldValue, newValue) -> btnRefreshSteamDir.setDisable(false));
    }
  }

  private void configureSteamRepo(@NotNull Scene scene) {
    Path steamDir = this.steamDir.get();

    Pane repoItem = (Pane) scene.lookup("#repoItem");
    scene.getStylesheets().add("style/repo_layout.css");

    Label label = (Label) repoItem.lookup("#label");
    label.setText(Bundle.get("path.to.common"));

    ProgressBar progressBar = (ProgressBar) repoItem.lookup("#progressBar");
    Label progressBarText = (Label) repoItem.lookup("#progressBarText");
    ChangeListener<Path> pathChangeListener = (observable, oldValue, newValue) -> {
      File steamDirFile = newValue.toFile();
      long usableSpace = steamDirFile.getUsableSpace();
      long totalSpace = steamDirFile.getTotalSpace();
      progressBar.setProgress(1.0 - ((double) usableSpace / totalSpace));
      progressBarText.setText(Bundle.get("repo.size",
          Utils.humanReadableByteCount(usableSpace, true),
          Utils.humanReadableByteCount(totalSpace, true)));
      updateGames(scene);
    };
    progressBar.progressProperty().addListener(((observable, oldValue, newValue) -> {
      double progress = newValue == null ? 0 : newValue.doubleValue();
      if (progress < 0.95) {
        progressBar.setStyle("-fx-accent: #0094c5;");
      } else {
        progressBar.setStyle("-fx-accent: #d00000;");
      }
    }));

    if (steamDir != null) {
      pathChangeListener.changed(null, null, steamDir);
    }

    this.steamDir.addListener(pathChangeListener);
    this.steamDir.addListener(((observable, oldValue, newValue) -> {
      Alert autoConfig = new Alert(Alert.AlertType.CONFIRMATION);
      autoConfig.setTitle(Bundle.get("autoconfig.title"));
      autoConfig.setContentText(Bundle.get("autoconfig.message"));
      autoConfig.setHeaderText(null);
      autoConfig.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
      autoConfig.initModality(Modality.WINDOW_MODAL);
      autoConfig.initOwner(scene.getWindow());
      autoConfig.showAndWait()
          .filter(result -> result == ButtonType.YES)
          .ifPresent(result -> autoConfigRepositories(scene));
    }));

    EventHandler<DragEvent> dragEventHandler = new RepoDragEventHandler(this.steamDir);
    repoItem.setOnDragOver(dragEventHandler);
    repoItem.setOnDragDropped(dragEventHandler);
    repoItem.setOnDragEntered(dragEventHandler);
    repoItem.setOnDragExited(dragEventHandler);
    repoItem.setOnMouseClicked(new RepoContextMenu(this.steamDir, repoItem));
  }

  private void autoConfigRepositories(@NotNull Scene scene) {
    Task task = new Task() {
      @Override
      protected Object call() throws Exception {
        Path steamDir = Main.this.steamDir.get();
        Set<Path> repos = Sets.newHashSet(Main.this.repos.get());
        try {
          Files.list(steamDir)
              .filter(path -> {
                try {
                  return JunctionSupport.isJunctionOrSymlink(path);
                } catch (IOException e) {
                  LOG.error(e.getMessage(), e);
                  return false;
                }
              })
              .forEach(symlink -> {
                try {
                  repos.add(symlink.toRealPath().getParent());
                } catch (IOException e) {
                  LOG.error(e.getMessage(), e);
                }
              });
        } catch (IOException e) {
          LOG.error(e.getMessage(), e);
        }

        if (Main.this.repos.size() < repos.size()) {
          Main.this.repos.set(FXCollections.observableArrayList(repos));
        }

        return null;
      }
    };

    new Thread(task).start();
  }

  private void configureRepos(@NotNull Scene scene) {
    ListView<Path> repos = (ListView<Path>) scene.lookup("#repos");
    repos.setCellFactory(param -> {
      ListCell<Path> cell = new ListCell<Path>() {
        @Override
        public void updateItem(Path path, boolean empty) {
          super.updateItem(path, empty);
          if (empty) {
            return;
          }

          try {
            URL location = Main.class.getResource("/layout/repo_layout.fxml");
            Pane repoItem = FXMLLoader.load(location);

            Label label = (Label) repoItem.lookup("#label");
            label.setText(path.toString());

            ProgressBar progressBar = (ProgressBar) repoItem.lookup("#progressBar");
            Label progressBarText = (Label) repoItem.lookup("#progressBarText");
            progressBar.progressProperty().addListener(((observable, oldValue, newValue) -> {
              double progress = newValue == null ? 0 : newValue.doubleValue();
              if (progress < 0.95) {
                progressBar.setStyle("-fx-accent: #0094c5;");
              } else {
                progressBar.setStyle("-fx-accent: #d00000;");
              }
            }));

            File repoFile = path.toFile();
            long usableSpace = repoFile.getUsableSpace();
            long totalSpace = repoFile.getTotalSpace();
            progressBar.setProgress(1.0 - ((double) usableSpace / totalSpace));
            progressBarText.setText(Bundle.get("repo.size",
                Utils.humanReadableByteCount(usableSpace, true),
                Utils.humanReadableByteCount(totalSpace, true)));

            setGraphic(repoItem);
            prefWidthProperty().bind(getListView().widthProperty().subtract(2));
            setMaxWidth(Control.USE_PREF_SIZE);
            setStyle("-fx-padding: 4px;");
          } catch (IOException e) {
            LOG.error(e.getMessage(), e);
          }
        }
      };

      EventHandler<DragEvent> dragEventHandler = new RepoDragEventHandler(cell.itemProperty());
      cell.setOnDragDropped(dragEventHandler);
      cell.setOnDragEntered(dragEventHandler);
      cell.setOnDragOver(dragEventHandler);
      cell.setOnDragExited(dragEventHandler);
      cell.setOnMouseClicked(new RepoContextMenu(cell.itemProperty(), cell));
      return cell;
    });
    ChangeListener<ObservableList<Path>> reposChangeListener = (observable, oldValue, newValue) -> repos.setItems(newValue);

    reposChangeListener.changed(null, null, this.repos.get());
    this.repos.addListener(reposChangeListener);

    MultipleSelectionModel<Path> selectionModel = repos.getSelectionModel();
    selectionModel.selectedIndexProperty().addListener(((observable, oldValue, newValue) -> {
      Button btnRemoveRepo = (Button) scene.lookup("#btnRemoveRepo");
      btnRemoveRepo.setDisable(newValue.intValue() == -1);
    }));

    repos.setOnMouseClicked((event -> {
      if (event.getButton() == MouseButton.PRIMARY && Main.this.repos.isEmpty()) {
        Button btnAddRepo = (Button) scene.lookup("#btnAddRepo");
        btnAddRepo.fire();
        event.consume();
      }
    }));

    Node placeholder = repos.getPlaceholder();
    Label label = (Label) placeholder.lookup("#label");
    label.setText(Bundle.get("repo.empty"));
  }

  private static class RepoContextMenu implements EventHandler<MouseEvent> {
    @NotNull final ContextMenu contextMenu;
    @NotNull final Node anchor;

    RepoContextMenu(@NotNull ObjectProperty<Path> repo, @NotNull Node anchor) {
      this.anchor = anchor;
      MenuItem browse = new MenuItem(Bundle.get("browse"));
      browse.setOnAction(event -> {
        try {
          Runtime.getRuntime().exec("explorer.exe " + repo.get());
        } catch (IOException e) {
          LOG.error(e.getMessage(), e);
        }
      });

      contextMenu = new ContextMenu();
      contextMenu.getItems().add(browse);
    }

    @Override
    public void handle(@NotNull MouseEvent event) {
      if (event.getButton() != MouseButton.SECONDARY) {
        return;
      }

      contextMenu.show(anchor, event.getScreenX(), event.getScreenY());
      event.consume();
    }
  }

  private class RepoDragEventHandler implements EventHandler<DragEvent> {
    private Tooltip draggingTooltip;

    @NotNull final ObjectProperty<Path> repo;

    RepoDragEventHandler(@NotNull ObjectProperty<Path> repo) {
      this.repo = repo;
    }

    @Override
    public void handle(@NotNull DragEvent event) {
      final Dragboard dragboard = event.getDragboard();
      if (!dragboard.hasContent(Game.DATA_FORMAT)) {
        event.consume();
        return;
      }

      Path repo = this.repo.get();
      //noinspection unchecked
      List<Game> games = (List<Game>) dragboard.getContent(Game.DATA_FORMAT);
      if (games.stream().allMatch(game -> repo.equals(game.repo.get()))) {
        event.consume();
        return;
      }

      final EventType<DragEvent> eventType = event.getEventType();
      if (eventType == DragEvent.DRAG_OVER) {
        event.acceptTransferModes(TransferMode.ANY);
      } else if (eventType == DragEvent.DRAG_DROPPED) {
        List<Game> validGames = games.stream()
            .filter(game -> !repo.equals(game.repo.get()))
            .collect(Collectors.toList());

        // FIXME: This operation blocks while calculating the size (although it's negligible on my machine). At least add a spinner. Noticeable when drag n dropping
        /**
         * To fix this, the calculation needs to be performed on another thread which will return the
         * size result. This can be done by the transfer object itself, i.e., add the transfer objects
         * to the list (maybe as a cluster, but that's for a later version), and then each item will
         * initialize (i.e., check the size). That being said though, this will only work if all the
         * batched transfers are added, unless each checked individually and transferred until there
         * is a problem.
         */
        long spaceRequired = validGames.stream()
            .mapToLong(game -> FileUtils.sizeOfDirectory(game.path.get().toFile()))
            .sum();

        // TODO: add proper messages
        long usableSpace = repo.toFile().getUsableSpace();
        if (usableSpace <= spaceRequired) {
          Alert alert = new Alert(Alert.AlertType.WARNING);
          alert.setTitle(Bundle.get("files.too.big.title"));
          alert.setContentText(Bundle.get("files.too.big.message"));
          alert.setHeaderText(null);
          alert.getButtonTypes().setAll(ButtonType.OK);
          alert.initOwner(((Node) event.getSource()).getScene().getWindow());
          alert.getDialogPane().setExpandableContent(new TextArea(
              Bundle.get("files.too.big.expanded",
                  Utils.humanReadableByteCount(spaceRequired, true),
                  Utils.humanReadableByteCount(usableSpace, true),
                  validGames.stream()
                      .map(Game::toString)
                      .collect(Collectors.joining("\n")))));
          alert.show();
        } else {
          Scene scene = ((Node) event.getSource()).getScene();
          enqueueTransfer(scene, validGames, repo);
        }
      } else if (eventType == DragEvent.DRAG_ENTERED) {
        ((Node) event.getTarget()).setStyle(
            "-fx-padding: 4px; " +
            "-fx-border-color: lightskyblue; " +
            "-fx-border-insets: -1; " +
            "-fx-background-color: aliceblue;");
      } else if (eventType == DragEvent.DRAG_EXITED) {
        ((Node) event.getTarget()).setStyle(
            "-fx-padding: 4px;");
      }

      event.consume();
    }
  }

  @FXML
  private void onAddRepo(@NotNull ActionEvent event) {
    Scene scene = ((Node) event.getSource()).getScene();
    DirectoryChooser directoryChooser = new DirectoryChooser();
    Optional.ofNullable(directoryChooser.showDialog(scene.getWindow()))
        .ifPresent(file -> repos.get().add(file.toPath()));
    event.consume();
  }

  @FXML
  private void onRemoveRepo(@NotNull ActionEvent event) {
    Scene scene = ((Node) event.getSource()).getScene();
    //noinspection unchecked
    ListView<Path> repos = (ListView<Path>) scene.lookup("#repos");
    MultipleSelectionModel model = repos.getSelectionModel();
    repos.getItems().remove(model.getSelectedIndex());
    model.clearSelection();
    event.consume();
  }

  private static <T> void setupCellValueFactory(TreeTableColumn<Game, T> column,
                                                Function<Game, ObservableValue<T>> mapper) {
    column.setCellValueFactory(param -> mapper.apply(param.getValue().getValue()));
  }

  private void configureGamesTable(@NotNull Scene scene) {
    //noinspection unchecked
    TreeTableView<Game> games = (TreeTableView<Game>) scene.lookup("#games");
    games.setTableMenuButtonVisible(true);
    games.setEditable(true);

    TreeTableColumn<Game, String> titleColumn = new TreeTableColumn<>();
    titleColumn.setText(Bundle.get("table.title"));
    titleColumn.setCellFactory(TextFieldTreeTableCell.forTreeTableColumn());
    titleColumn.setOnEditCommit(event -> {
      Game game = event.getRowValue().getValue();
      String name = event.getNewValue();
      game.title.setValue(name);
      PREFERENCES.put(Prefs.GAME_TITLE + game.folder.get().toString(), name);
    });

    TreeTableColumn<Game, Path> pathColumn = new TreeTableColumn<>();
    pathColumn.setText(Bundle.get("table.path"));
    pathColumn.setStyle("-fx-alignment: CENTER-LEFT;");
    //pathColumn.setStyle("-fx-text-overrun: LEADING-ELLIPSIS;");
    pathColumn.setCellFactory(value -> new TreeTableCell<Game, Path>() {
      @Override
      protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);
        updateRepoItem(this, item, empty);
      }
    });

    TreeTableColumn<Game, Number> sizeColumn = new TreeTableColumn<>();
    sizeColumn.setText(Bundle.get("table.size"));
    sizeColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    sizeColumn.setCellFactory(callback -> new TreeTableCell<Game, Number>() {
      @Override
      protected void updateItem(Number item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
        } else {
          long value = item.longValue();
          setText(value >= 0L ? Utils.humanReadableByteCount(value, true) : null);
        }
      }
    });

    setupCellValueFactory(titleColumn, Game::titleProperty);
    setupCellValueFactory(pathColumn, Game::repoProperty);
    setupCellValueFactory(sizeColumn, Game::sizeProperty);

    //games.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
    titleColumn.prefWidthProperty().bind(games.widthProperty().multiply(0.375));
    pathColumn.prefWidthProperty().bind(games.widthProperty().multiply(0.5));
    sizeColumn.prefWidthProperty().bind(games.widthProperty().multiply(0.125).subtract(15));

    //noinspection unchecked
    games.getColumns().addAll(titleColumn, pathColumn, sizeColumn);

    games.setRowFactory(param -> {
      TreeTableRow<Game> row = new TreeTableRow<Game>() {
        /*@Override
        protected void updateItem(Game item, boolean empty) {
          super.updateItem(item, empty);
          if (empty || item == null || item.path.get() != null) {
            setOpacity(1);
            return;
          }

          setOpacity(0.5);
        }*/
      };
      row.setOnDragDetected(event -> {
        List<TreeItem<Game>> selectedItems = games.getSelectionModel().getSelectedItems();
        if (selectedItems == null || selectedItems.isEmpty()) {
          return;
        }

        Dragboard db = games.startDragAndDrop(TransferMode.ANY);
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        //db.setDragView(row.snapshot(sp, null));
        db.setDragView(new Image("/mipmap/ic_insert_link_black.png"), 36, 48);
        ClipboardContent content = new ClipboardContent();
        List<Game> selectedGames = selectedItems.stream()
            .map(TreeItem::getValue)
            .collect(Collectors.toList());
        content.put(Game.DATA_FORMAT, selectedGames);
        db.setContent(content);
        event.consume();
      });

      row.setOnDragDone(event -> {
        event.setDropCompleted(true);
        event.consume();
      });

      return row;
    });

    games.setOnMouseClicked(event -> {
      if (event.getButton() == MouseButton.PRIMARY
          && (games.getRoot() == null || games.getRoot().getChildren().isEmpty())) {
        Button btnEditSteamDir = (Button) scene.lookup("#btnEditSteamDir");
        btnEditSteamDir.fire();
        event.consume();
      }
    });

    Node placeholder = games.getPlaceholder();
    Label label = (Label) placeholder.lookup("#label");
    label.setText(Bundle.get("path.to.common.empty"));

    games.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
  }

  public static void updateRepoItem(@NotNull IndexedCell<Path> cell, Path item, boolean empty) {
    if (empty || item == null) {
      cell.setGraphic(null);
      cell.setText(null);
      return;
    } else if (item.equals(steamDir.get())) {
      cell.setGraphic(new ImageView("/mipmap/icon_16x16.png"));
      cell.setText(Bundle.get("path.to.common"));
    } else {
      cell.setGraphic(null);
      cell.setText(Main.PATH_STRING_CONVERTER.toString(item));
    }
  }

  private synchronized void updateGames(@NotNull Scene scene) {
    //noinspection unchecked
    TreeTableView<Game> games = (TreeTableView<Game>) scene.lookup("#games");
    Node placeholder = games.getPlaceholder();

    Node spinner = placeholder.lookup("#spinner");
    Node label = placeholder.lookup("#label");
    spinner.setVisible(true);
    label.setVisible(false);

    Task task = new Task() {
      @Override
      protected Object call() throws Exception {
        List<Game> gamesList = null;
        try {
          gamesList = Files.list(steamDir.get())
              .map(path -> {
                String fileName = path.getFileName().toString();
                String name = PREFERENCES.get(Prefs.GAME_TITLE + fileName, fileName);
                long size = PREFERENCES.getLong(Prefs.GAME_SIZE + fileName, Long.MIN_VALUE);
                try {
                  return new Game(name, path.toRealPath(), size);
                } catch (IOException e) {
                  LOG.warn("Broken link detected: " + path);
                  return new Game(name, null);
                }
              })
              .collect(Collectors.toList());

          TreeItem<Game> rootItem = new TreeItem<>();
          rootItem.getChildren()
              .setAll(gamesList.stream()
                  .map(TreeItem::new)
                  .collect(Collectors.toList()));
          Platform.runLater(() -> {
            games.setRoot(rootItem);
            games.setShowRoot(false);
          });
        } catch (IOException e) {
          LOG.error(e.getMessage(), e);
        } finally {
          Platform.runLater(() -> {
            spinner.setVisible(false);
            label.setVisible(true);
          });
        }

        if (gamesList != null) {
          if (DEBUG_GAMES_REFRESH) {
            LOG.debug("Refreshing games list...");
          }

          for (Game game : gamesList) {
            Path path = game.path.get();
            if (path == null) {
              continue;
            }

            long size = FileUtils.sizeOfDirectory(path.toFile());
            if (size != game.size.get()) {
              game.size.set(size);
              PREFERENCES.putLong(Prefs.GAME_SIZE + path.getFileName(), size);
              Platform.runLater(games::refresh);
            }
          }

          if (DEBUG_GAMES_REFRESH) {
            LOG.debug("Games list refreshed.");
          }
        }

        return null;
      }
    };

    new Thread(task).start();
  }

  private void configureTransfers(@NotNull Scene scene) {
    Node transfers = scene.lookup("#transfers");
    configureTransfersTable(scene, transfers);
  }

  private void configureTransfersTable(@NotNull Scene scene, @NotNull Node transfers) {
    TableView<Transfer> transfersList = (TableView<Transfer>) transfers.lookup("#transfersList");
    transfersList.setTableMenuButtonVisible(true);
    transfersList.setItems(FXCollections.observableArrayList());

    TableColumn<Transfer, String> titleColumn = new TableColumn<>();
    titleColumn.setText(Bundle.get("table.title"));
    titleColumn.setCellValueFactory(param -> param.getValue().game.title);

    TableColumn<Transfer, Double> progressColumn = new TableColumn<>();
    progressColumn.setText(Bundle.get("table.progress"));
    progressColumn.setCellFactory(param -> new TableCell<Transfer, Double>() {
      private final Node graphic;
      private final ProgressBar transferProgress;
      private final Label transferPercent;
      ObservableValue<Double> observable;

      {
        transferProgress = new ProgressBar();
        transferProgress.setMaxWidth(Double.MAX_VALUE);
        transferPercent = new Label();
        graphic = new StackPane(transferProgress, transferPercent);
        getStylesheets().add("/style/transfer_progress_layout.css");
      }

      @Override
      protected void updateItem(Double item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
          setGraphic(null);
        } else {
          transferProgress.progressProperty().unbind();
          final TableColumn<Transfer, Double> column = getTableColumn();
          observable = column == null ? null : column.getCellObservableValue(getIndex());
          if (observable != null) {
            transferProgress.progressProperty().bind(observable);
            transferPercent.textProperty().bind(transferProgress.progressProperty().multiply(100.0).asString("%.0f%%"));
            transferPercent.visibleProperty().bind(transferProgress.progressProperty().greaterThanOrEqualTo(0.0));
          } else if (item != null) {
            transferProgress.setProgress(item);
            transferPercent.setText(String.format("%.0f%%", item * 100.0));
          }

          setGraphic(graphic);
        }
      }
    });
    progressColumn.setCellValueFactory((TableColumn.CellDataFeatures<Transfer, Double> param) -> param.getValue().progress.asObject());
    progressColumn.setStyle("-fx-alignment: CENTER;");

    TableColumn<Transfer, Path> sourceColumn = new TableColumn<>();
    sourceColumn.setText(Bundle.get("table.source"));
    sourceColumn.setCellFactory(param -> new TableCell<Transfer, Path>() {
      @Override
      protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);
        updateRepoItem(this, item, empty);
      }
    });
    sourceColumn.setStyle("-fx-alignment: CENTER-LEFT;");
    //sourceColumn.setStyle("-fx-text-overrun: LEADING-ELLIPSIS;");
    sourceColumn.setCellValueFactory(param -> param.getValue().src);

    TableColumn<Transfer, Path> destinationColumn = new TableColumn<>();
    destinationColumn.setText(Bundle.get("table.destination"));
    destinationColumn.setCellFactory(param -> new TableCell<Transfer, Path>() {
      @Override
      protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);
        updateRepoItem(this, item, empty);
      }
    });
    destinationColumn.setStyle("-fx-alignment: CENTER-LEFT;");
    //destinationColumn.setStyle("-fx-text-overrun: LEADING-ELLIPSIS;");
    destinationColumn.setCellValueFactory(param -> param.getValue().dst);

    //games.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
    titleColumn.prefWidthProperty().bind(transfersList.widthProperty().multiply(0.25).subtract(15));
    progressColumn.prefWidthProperty().bind(transfersList.widthProperty().multiply(0.25));
    sourceColumn.prefWidthProperty().bind(transfersList.widthProperty().multiply(0.25));
    destinationColumn.prefWidthProperty().bind(transfersList.widthProperty().multiply(0.25));

    //noinspection unchecked
    transfersList.getColumns().setAll(titleColumn, progressColumn, sourceColumn, destinationColumn);
    configureTransfersControls(scene, transfers, transfersList);
  }

  private void configureTransfersControls(@NotNull Scene scene, @NotNull Node transfers, @NotNull TableView<Transfer> transfersTable) {
    MultipleSelectionModel<Transfer> selectionModel = transfersTable.getSelectionModel();
    ObservableList<Transfer> selectedItems = selectionModel.getSelectedItems();

    Button btnRemoveTransfer = (Button) transfers.lookup("#btnRemoveTransfer");
    btnRemoveTransfer.disableProperty().bind(Bindings.size(selectedItems).isEqualTo(0));
    btnRemoveTransfer.setOnAction(event -> transfersTable.getItems().removeAll(selectedItems));

    Button btnClearTransfers = (Button) transfers.lookup("#btnClearTransfers");
    btnClearTransfers.disableProperty().bind(Bindings.size(transfersTable.getItems()).isEqualTo(0));
    btnClearTransfers.setOnAction(event -> transfersTable.getItems().clear());
  }

  private void enqueueTransfer(@NotNull Scene scene, @NotNull List<Game> games, @NotNull Path repo) {
    Node transfers = scene.lookup("#transfers");
    TableView<Transfer> transfersList = (TableView<Transfer>) transfers.lookup("#transfersList");
    ObservableList<Game.Transfer> queue = transfersList.getItems();
    // TODO: This could probably be cleaned up a bit, maybe by adding equals/hashcode to Transfer
    games.removeIf(game -> queue.stream().anyMatch(q -> q.dst.get().equals(repo) && q.src.get().equals(game.path.get())));
    if (DEBUG_GAMES_QUEUE) {
      games.forEach(game -> LOG.debug("Enqueueing " + game.title.get()));
    }

    List<Transfer> items = games.stream()
        .map(g -> g.transferTo(repo))
        .collect(Collectors.toList());

    queue.addAll(items);
    ToggleButton btnPerformTransfers = (ToggleButton) scene.lookup("#btnPerformTransfers");
    if (!btnPerformTransfers.isSelected()) {
      return;
    }

    for (Transfer transfer : items) {
      doTransfer(transfer);
    }
  }

  private void doTransfer(@NotNull Transfer transfer) {
    File src = transfer.src.get().toFile();
    File dst = transfer.dst.get().toFile();
    transfer.task.addListener((observable, oldValue, newValue) -> executors.execute(newValue));
    transfer.task.set(new CopyTask(LOG, src, dst));
  }
}
