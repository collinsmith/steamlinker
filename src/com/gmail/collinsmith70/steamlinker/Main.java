package com.gmail.collinsmith70.steamlinker;

import com.google.common.collect.Sets;

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
import java.util.function.Function;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ListPropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
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
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.image.Image;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
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
    String GAME = "game.";
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

  @Override
  public void start(Stage stage) throws Exception {
    URL location = Main.class.getResource("/layout/main_layout.fxml");
    Parent root = FXMLLoader.load(location, Bundle.BUNDLE);
    Scene scene = new Scene(root);

    scene.getStylesheets().add(Main.class.getResource("/style/jfoenix-components.css") .toExternalForm());

    stage.setTitle(Bundle.get("name"));
    doIcons(stage.getIcons());
    stage.setScene(scene);

    bindProperties();

    configureSteamDir(scene);
    configureEditSteamDir(scene);
    configureRefreshSteamDir(scene);
    configureSteamRepo(scene);
    configureRepos(scene);
    configureGamesTable(scene);

    stage.show();
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

  private static class RepoDragEventHandler implements EventHandler<DragEvent> {
    private static Tooltip draggingTooltip;

    @NotNull final ObjectProperty<Path> repo;

    RepoDragEventHandler(@NotNull ObjectProperty<Path> repo) {
      this.repo = repo;
    }

    @Override
    public void handle(@NotNull DragEvent event) {
      Object content = event.getDragboard().getContent(DataFormat.FILES);
      if (content == null) {
        return;
      }

      Path repo = this.repo.get();
      //noinspection unchecked
      List<File> games = (List<File>) content;
      if (games.stream().allMatch(game -> repo.equals(game.toPath().getParent()))) {
        return;
      }

      event.acceptTransferModes(TransferMode.ANY);
      event.consume();

      if (event.getEventType() == DragEvent.DRAG_ENTERED) {
        //System.out.println(event.getEventType());
        ((Node) event.getTarget()).setStyle("-fx-padding: 4px; -fx-border-color: lightskyblue; -fx-border-insets: -1; -fx-background-color: aliceblue;");
        /*if (draggingTooltip == null) {
          System.out.println("creating");
          draggingTooltip = new Tooltip("test");
          draggingTooltip.show(((Node) event.getTarget()), event.getScreenX(), event.getScreenY());
        }*/
      } else if (event.getEventType() == DragEvent.DRAG_OVER) {
        //System.out.println(event.getEventType());
        //draggingTooltip.setAnchorX(event.getScreenX());
        //draggingTooltip.setAnchorY(event.getScreenY());
      } else if (event.getEventType() == DragEvent.DRAG_EXITED) {
        //System.out.println(event.getEventType());
        ((Node) event.getTarget()).setStyle("-fx-padding: 4px;");
        //event.getDragboard().setDragView(null);
        /*if (draggingTooltip != null) {
          System.out.println("hiding");
          draggingTooltip.hide();
          draggingTooltip = null;
        }*/
      } else if (event.getEventType() == DragEvent.DRAG_DROPPED) {
        List<File> validGames = games.stream()
            .filter(game -> !repo.equals(game.toPath().getParent()))
            .collect(Collectors.toList());

        long spaceRequired = 0L;
        for (File game : validGames) {
          spaceRequired += FileUtils.sizeOfDirectory(game);
        }

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
                      .map(File::toString)
                      .collect(Collectors.joining("\n")))));
          alert.show();
          return;
        }

        Node source = ((Node) event.getSource());
        transfer(source, source.getScene(), games, repo.toFile(), steamDir.get().toFile());
      }
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

  private <T> void setupCellValueFactory(TreeTableColumn<Game, T> column, Function<Game, ObservableValue<T>> mapper) {
    column.setCellValueFactory((TreeTableColumn.CellDataFeatures<Game, T> param) -> {
      return mapper.apply(param.getValue().getValue());
    });
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
      PREFERENCES.put(Prefs.GAME + game.folder.get().toString(), name);
    });

    TreeTableColumn<Game, Path> pathColumn = new TreeTableColumn<>();
    pathColumn.setText(Bundle.get("table.path"));
    pathColumn.setStyle("-fx-alignment: CENTER-LEFT;");
    //pathColumn.setStyle("-fx-text-overrun: LEADING-ELLIPSIS;");

    TreeTableColumn<Game, Game.FileSize> sizeColumn = new TreeTableColumn<>();
    sizeColumn.setText(Bundle.get("table.size"));
    sizeColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

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
      TreeTableRow<Game> row = new TreeTableRow<>();
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
        List<File> selectedGames = selectedItems.stream()
            .filter(selectedItem -> selectedItem.getValue().path.get() != null)
            .map(selectedItem -> selectedItem.getValue().path.get().toFile())
            .collect(Collectors.toList());
        content.putFiles(selectedGames);
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
              .map(game -> {
                String fileName = game.getFileName().toString();
                String name = PREFERENCES.get(Prefs.GAME + fileName, fileName);
                try {
                  return new Game(name, game.toRealPath());
                } catch (IOException e) {
                  LOG.warn("Broken link detected: " + game);
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
          for (Game game : gamesList) {
            Path path = game.path.get();
            if (path == null) {
              continue;
            }

            long size = FileUtils.sizeOfDirectory(path.toFile());
            game.size.set(new Game.FileSize(size));
            Platform.runLater(games::refresh);
          }
        }

        return null;
      }
    };

    new Thread(task).start();
  }

  private static void transfer(@NotNull Node node, @NotNull Scene scene, @NotNull List<File> games, @NotNull File repo, @NotNull File steamDir) {
    ProgressBar transferProgress = (ProgressBar) scene.lookup("#transferProgress");
    Label transferPercent = (Label) scene.lookup("#transferPercent");
    Label transferETA = (Label) scene.lookup("#transferETA");
    Label transferBPS = (Label) scene.lookup("#transferBPS");

    CopyTask copyTask = new CopyTask(LOG, games, repo);
    Thread t = new Thread(copyTask);
    t.setName("steamlinker.copy");
    t.start();

    Task progress = new Task() {
      @Override
      protected Object call() throws Exception {
        while (!t.isAlive()) {
          Thread.sleep(10);
        }

        long bytesCopied, totalSize = copyTask.totalSize;
        long previousBytes = 0L;
        while (true) {
          bytesCopied = copyTask.bytesCopied;
          double percent = (double) bytesCopied / totalSize;
          long bytesSinceLast = bytesCopied - previousBytes;
          long remainingBytes = totalSize - bytesCopied;
          double eta = (double) remainingBytes / bytesSinceLast;
          Platform.runLater(() -> {
            transferProgress.setProgress(percent);
            transferPercent.setText(String.format("%.0f%%", percent * 100.0));
            transferBPS.setText(Utils.humanReadableByteCount(bytesSinceLast, true) + "ps");
            transferETA.setText(String.format("%.0f:%02.0f:%02.0f", eta / 3600, (eta % 3600) / 60, eta % 60));
          });

          previousBytes = bytesCopied;
          if (bytesCopied < totalSize) {
            Thread.sleep(1000);
          } else {
            break;
          }
        }

        return null;
      }
    };

    new Thread(progress).start();
  }
}
