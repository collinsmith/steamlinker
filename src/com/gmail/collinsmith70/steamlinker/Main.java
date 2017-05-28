package com.gmail.collinsmith70.steamlinker;

import org.jetbrains.annotations.NotNull;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
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

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import javafx.scene.image.Image;
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

    configureSteamDir(scene);
  }

  private void configureSteamDir(@NotNull Scene scene) {
    TextInputControl steamDir = (TextInputControl) scene.lookup("#commonDir");
    steamDir.setText(PREFERENCES.get("common.dir", ""));
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
    stage.getIcons().add(new FontIcon("gmi-settings:32:grey").snapshot(null, null));
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
}
