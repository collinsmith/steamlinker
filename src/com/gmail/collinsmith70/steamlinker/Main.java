package com.gmail.collinsmith70.steamlinker;

import com.jfoenix.controls.JFXTreeTableColumn;
import com.jfoenix.controls.JFXTreeTableView;
import com.jfoenix.controls.RecursiveTreeItem;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;

public class Main extends Application {

  private static final boolean DEBUG_REPOSITORIES_AUTOCONFIG = true;

  private static final Preferences PREFERENCES = Preferences.userNodeForPackage(Main.class);

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(@NotNull Stage stage) throws IOException {
    Parent root = FXMLLoader.load(Main.class.getResource("/layout/main.fxml"), Bundle.BUNDLE);
    Scene scene = new Scene(root);

    stage.setTitle(Bundle.translate("name"));
    stage.getIcons().add(new Image("mipmap/icon_16x16.png"));
    stage.getIcons().add(new Image("mipmap/icon_32x32.png"));
    stage.setScene(scene);
    stage.show();

    boolean[] running = { true };
    Path steamDir = configureSteamDir(scene);
    if (steamDir != null) {
      ObservableList<Game> games = populateGames(scene, steamDir);
      Task task = new Task() {
        @Override
        protected Object call() throws Exception {
          for (Game game : games) {
            if (!running[0]) {
              break;
            }

            Path path = game.getPath();
            if (path == null) {
              continue;
            }

            long size = FileUtils.sizeOfDirectory(path.toFile());
            game.setSize(new Game.FileSize(size));
          }

          return null;
        }
      };

      new Thread(task).start();
      stage.setOnCloseRequest(event -> running[0] = false);
    }
  }

  private ObservableList<Game> populateGames(@NotNull Scene scene, @NotNull Path steamDir) throws IOException {
    JFXTreeTableView<Game> games = (JFXTreeTableView) scene.lookup("#games");

    JFXTreeTableColumn<Game, String> titleColumn = new JFXTreeTableColumn(Bundle.translate("table.title"));
    //titleColumn.setCellValueFactory(new PropertyValueFactory<Game, String>("title"));
    titleColumn.setCellValueFactory(
        (TreeTableColumn.CellDataFeatures<Game, String> param) -> {
          if (titleColumn.validateValue(param)) {
            return param.getValue().getValue().title;
          } else {
            return titleColumn.getComputedValue(param);
          }
        });

    JFXTreeTableColumn<Game, Path> pathColumn = new JFXTreeTableColumn(Bundle.translate("table.path"));
    //pathColumn.setCellValueFactory(new PropertyValueFactory<Game, String>("path"));
    pathColumn.setCellValueFactory(
        (TreeTableColumn.CellDataFeatures<Game, Path> param) -> {
          if (pathColumn.validateValue(param)) {
            return param.getValue().getValue().path;
          } else {
            return pathColumn.getComputedValue(param);
          }
        });

    JFXTreeTableColumn<Game, Game.FileSize> sizeColumn = new JFXTreeTableColumn(Bundle.translate("table.size"));
    //sizeColumn.setCellValueFactory(new PropertyValueFactory<Game, String>("size"));
    sizeColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    sizeColumn.setCellValueFactory(
        (TreeTableColumn.CellDataFeatures<Game, Game.FileSize> param) -> {
          if (sizeColumn.validateValue(param)) {
            return param.getValue().getValue().size;
          } else {
            return sizeColumn.getComputedValue(param);
          }
        });

    List<Game> list = Files.list(steamDir)
        .map(game -> {
          try {
            return new Game(game.getFileName().toString(), game.toRealPath());
          } catch (IOException e) {
            return new Game(game.getFileName().toString(), null);
          }
        })
        .collect(Collectors.toList());

    ObservableList<Game> items = FXCollections.observableList(list);
    TreeItem<Game> root = new RecursiveTreeItem<>(items, RecursiveTreeObject::getChildren);
    games.setRoot(root);
    games.setShowRoot(false);
    games.getColumns().addAll(titleColumn, pathColumn, sizeColumn);

    games.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    games.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
      Button btnLeft = (Button) scene.lookup("#btnLeft");
      Button btnRight = (Button) scene.lookup("#btnRight");
      if (newValue == null) {
        btnLeft.setDisable(true);
        btnRight.setDisable(true);
      } else {
        Game game = newValue.getValue();
        Path path = game.getPath();
        if (path == null) {
          return;
        }

        boolean isJunction = game.getPath().getParent().equals(steamDir);
        btnLeft.setDisable(isJunction);
        btnRight.setDisable(!isJunction);
      }
    });

    return items;
  }

  @Nullable
  private Path configureSteamDir(@NotNull Scene scene) {
    TextInputControl steamDir = (TextInputControl) scene.lookup("#commonDir");
    String text = PREFERENCES.get("common.dir", "");
    steamDir.setText(text);
    return !text.isEmpty() ? Paths.get(text) : null;
  }

  private void configureRepositories(@NotNull Scene scene) {
    String value = PREFERENCES.get("repos", "");
    List<String> paths = Arrays.asList(value.split(";"));
    ObservableList<String> items = FXCollections.observableList(paths);

    ListView repos = (ListView) scene.lookup("#repos");
    repos.setItems(items);

    MultipleSelectionModel selectionModel = repos.getSelectionModel();
    selectionModel.clearSelection();
    selectionModel.setSelectionMode(SelectionMode.SINGLE);
    selectionModel.selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
      Button removeRepository = (Button) scene.lookup("#removeRepository");
      removeRepository.setDisable((int) newValue == -1);
    });
  }

  @FXML
  private void onSettingsPressed(@NotNull ActionEvent event) throws IOException {
    Dialog<Pair<ButtonType, Map<String, Object>>> dialog = new Dialog<>();
    dialog.setTitle(Bundle.translate("settings"));
    Control control = (Control) event.getSource();
    Scene thisScene = control.getScene();

    Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
    //stage.getIcons().add(new FontIcon("gmi-settings:32:grey").snapshot(null, null));
    stage.setOnCloseRequest(closeEvent -> dialog.close());

    DialogPane content = FXMLLoader.load(Main.class.getResource("/layout/settings.fxml"), Bundle.BUNDLE);

    dialog.initModality(Modality.WINDOW_MODAL);
    dialog.initOwner(thisScene.getWindow());
    dialog.setDialogPane(content);
    dialog.setResizable(true);
    Scene scene = content.getScene();
    configureSteamDir(scene);
    configureRepositories(scene);
    dialog.setResultConverter(buttonType -> {
      if (buttonType != ButtonType.APPLY) {
        return null;
      }

      TextInputControl initialDirectory = (TextInputControl) scene.lookup("#commonDir");

      Map<String, Object> result = new HashMap<>();
      result.put("#commonDir", initialDirectory.getText());

      ListView repos = (ListView) scene.lookup("#repos");
      ObservableList items = repos.getItems();
      result.put("#repos", String.join(";", items));

      return new Pair<>(buttonType, result);
    });
    dialog.showAndWait()
        .filter(response -> response.getKey() == ButtonType.APPLY)
        .ifPresent(response -> applySettings(thisScene, response.getValue()));
  }

  private void applySettings(@NotNull Scene thisScene, @NotNull Map<String, Object> map) {
    String initialDirectory = (String) map.get("#commonDir");
    PREFERENCES.put("common.dir", initialDirectory);

    TextField steamDir = (TextField) thisScene.lookup("#commonDir");
    steamDir.setText(initialDirectory);

    String repos = (String) map.get("#repos");
    PREFERENCES.put("repos", repos);
  }

  @FXML
  private void onChangeSteamDir(@NotNull ActionEvent event) {
    Scene scene = ((Node) event.getSource()).getScene();
    TextInputControl initialDirectory = (TextInputControl) scene.lookup("#commonDir");
    String text = initialDirectory.getText();
    Path path = Paths.get(text);

    File existingFile = !text.isEmpty() && Files.isDirectory(path)
        ? path.toFile()
        : null;

    DirectoryChooser directoryChooser = new DirectoryChooser();
    if (existingFile != null) {
      directoryChooser.setInitialDirectory(existingFile);
    }

    File steamDir = directoryChooser.showDialog(scene.getWindow());
    if (steamDir != null) {
      if (existingFile != null && existingFile.equals(steamDir) && !DEBUG_REPOSITORIES_AUTOCONFIG) {
        return;
      }

      String steamDirPath = steamDir.getAbsolutePath();
      initialDirectory.setText(steamDirPath);

      Alert autoconfig = new Alert(Alert.AlertType.CONFIRMATION,
          Bundle.translate("autoconfig.message"),
          ButtonType.YES, ButtonType.NO);
      autoconfig.setTitle(Bundle.translate("autoconfig.title"));
      autoconfig.setHeaderText(null);

      autoconfig.initModality(Modality.WINDOW_MODAL);
      autoconfig.initOwner(scene.getWindow());

      Stage stage = (Stage) autoconfig.getDialogPane().getScene().getWindow();
      stage.getIcons().add(new FontIcon("gmi-info:16:red").snapshot(null, null));
      stage.setOnCloseRequest(closeEvent -> autoconfig.close());

      autoconfig.showAndWait()
          .filter(result -> result == ButtonType.YES)
          .ifPresent(result -> autoconfigRepositories(scene, steamDirPath));
    }
  }

  private void autoconfigRepositories(@NotNull Scene scene, @NotNull String steamDirPath) {
    Path path = Paths.get(steamDirPath);
    Set<String> uniqueRepositories = new HashSet<>();
    try {
      Files.list(path)
          .filter(p -> {
            try {
              return JunctionSupport.isJunctionOrSymlink(p);
            } catch (IOException e) {
              e.printStackTrace();
              return false;
            }
          })
          .forEach(symlink -> {
            try {
              uniqueRepositories.add(symlink.toRealPath().getParent().toString());
            } catch (IOException e) {
              e.printStackTrace();
            }
          });
    } catch (IOException e) {
      e.printStackTrace();
    }

    ObservableList<String> items = FXCollections.observableArrayList(uniqueRepositories);
    ListView repos = (ListView) scene.lookup("#repos");
    repos.setItems(items);
  }

  @FXML
  private void onAddRepo(@NotNull ActionEvent event) {
    Scene scene = ((Node) event.getSource()).getScene();
    DirectoryChooser directoryChooser = new DirectoryChooser();
    File steamDir = directoryChooser.showDialog(scene.getWindow());
    if (steamDir != null) {
      String steamDirPath = steamDir.getAbsolutePath();
      ListView repos = (ListView) scene.lookup("#repos");
      repos.getItems().add(steamDirPath);
    }
  }

  @FXML
  private void onRemoveRepo(@NotNull ActionEvent event) {
    Scene scene = ((Node) event.getSource()).getScene();
    ListView repos = (ListView) scene.lookup("#repos");
    MultipleSelectionModel model = repos.getSelectionModel();
    repos.getItems().remove(model.getSelectedIndex());
    model.clearSelection();
  }

  @FXML
  private void onTransferPressed(@NotNull ActionEvent event) {
    Button source = (Button) event.getSource();
    Scene scene = source.getScene();
    TreeTableView<Game> games = (TreeTableView) scene.lookup("#games");
    if (games.getSelectionModel().isEmpty()) {
      return;
    }

    Path steamDir = Paths.get(((TextInputControl) scene.lookup("#commonDir")).getText());
    if (steamDir.toString().isEmpty()) {
      return;
    }

    Path linkedDir = Paths.get("D:\\games\\steam");
    TreeItem<Game> item = games.getSelectionModel().getSelectedItem();
    Game game = item.getValue();
    Pane transferProgress = (Pane) scene.lookup("#transferProgress");

    Path sourcePath = game.getPath();
    Path targetPath = sourcePath.getParent().equals(steamDir) ? linkedDir : steamDir;
    targetPath = targetPath.resolve(sourcePath.getFileName());
    transferProgress.setVisible(true);

    try {
      if (targetPath.getParent().equals(steamDir) && Files.isDirectory(targetPath)) {
        FileUtils.deleteDirectory(targetPath.toFile());
      }

      System.out.println(sourcePath + "->" + targetPath);
      FileUtils.copyDirectory(sourcePath.toFile(), targetPath.toFile());
      if (sourcePath.getParent().equals(steamDir)) {
        FileUtils.deleteDirectory(sourcePath.toFile());
        ProcessBuilder builder = new ProcessBuilder(
            "cmd.exe", "/c", "mklink", "/J", sourcePath.toString(), targetPath.toString());
        builder.redirectErrorStream(true);
        Process p = builder.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
          String line;
          while ((line = r.readLine()) != null) {
            System.out.println(line);
          }
        }
      }

      item.getValue().setPath(targetPath);
      int selectedIndex = games.getSelectionModel().getSelectedIndex();
      games.getSelectionModel().clearSelection();
      games.getSelectionModel().select(selectedIndex);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      transferProgress.setVisible(false);
    }
  }
}
