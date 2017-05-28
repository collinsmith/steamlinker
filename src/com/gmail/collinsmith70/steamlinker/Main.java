package com.gmail.collinsmith70.steamlinker;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;

public class Main extends Application {

  private static final Preferences PREFERENCES = Preferences.userNodeForPackage(Main.class);

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(@NotNull Stage stage) throws IOException {
    Parent root = FXMLLoader.load(Main.class.getResource("/layout/main.fxml"), Bundle.BUNDLE);
    Scene scene = new Scene(root);

    stage.setTitle(Bundle.translate("name"));
    doIcons(stage);
    stage.setScene(scene);
    stage.show();

    configureSteamDir(scene);
  }

  private void doIcons(@NotNull Stage stage) {
    stage.getIcons().add(new Image("mipmap/icon_16x16.png"));
    stage.getIcons().add(new Image("mipmap/icon_32x32.png"));
  }

  private void configureSteamDir(@NotNull Scene scene) {
    TextField steamDir = (TextField) scene.lookup("#commonDir");
    steamDir.setText(PREFERENCES.get("common.dir", ""));
  }

  @FXML
  private void onChangeSteamDir(@NotNull ActionEvent event) {
    Node source = (Node) event.getSource();
    Scene scene = source.getScene();
    TextInputControl initialDirectory = (TextInputControl) scene.lookup("#commonDir");
    String text = initialDirectory.getText();
    Path path = Paths.get(text);

    DirectoryChooser directoryChooser = new DirectoryChooser();
    if (!text.isEmpty() && Files.isDirectory(path)) {
      directoryChooser.setInitialDirectory(path.toFile());
    }

    File result = directoryChooser.showDialog(scene.getWindow());
    if (result != null) {
      text = result.getAbsolutePath();
      initialDirectory.setText(text);
      //PREFERENCES.put("common.dir", text);
    }
  }

  @FXML
  private void onSettingsPressed(@NotNull ActionEvent event) throws IOException {
    Dialog<Pair<ButtonType, Map<String, Object>>> dialog = new Dialog<>();
    dialog.setTitle(Bundle.translate("settings"));
    Control control = (Control) event.getSource();
    Scene thisScene = control.getScene();

    Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
    stage.getIcons().add(new Image("png/settings-4.png"));
    stage.setOnCloseRequest(closeEvent -> dialog.close());

    DialogPane content = FXMLLoader.load(Main.class.getResource("/layout/settings.fxml"), Bundle.BUNDLE);

    dialog.initModality(Modality.WINDOW_MODAL);
    dialog.initOwner(thisScene.getWindow());
    dialog.setDialogPane(content);
    dialog.setResizable(true);
    Scene scene = content.getScene();
    configureSteamDir(scene);
    dialog.setResultConverter(buttonType -> {
      if (buttonType != ButtonType.APPLY) {
        return null;
      }

      TextInputControl initialDirectory = (TextInputControl) scene.lookup("#commonDir");

      Map<String, Object> result = new HashMap<>();
      result.put("#commonDir", initialDirectory.getText());

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
  }
}
