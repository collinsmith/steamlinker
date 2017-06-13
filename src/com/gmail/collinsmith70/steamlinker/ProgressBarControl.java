package com.gmail.collinsmith70.steamlinker;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.beans.property.DoubleProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;

public class ProgressBarControl extends StackPane implements Initializable {
  @FXML private ProgressBar jfxProgressBar;
  @FXML private Label jfxProgressBarLabel;

  public ProgressBarControl() {
    URL location = RepoControl.class.getResource("/layout/progressbar.fxml");
    FXMLLoader loader = new FXMLLoader();
    loader.setRoot(this);
    loader.setController(this);
    loader.setLocation(location);
    loader.setResources(Bundle.BUNDLE);

    try {
      loader.load();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    jfxProgressBarLabel.setLabelFor(jfxProgressBar);
    jfxProgressBarLabel.textProperty()
        .bind(jfxProgressBar.progressProperty()
            .multiply(100.0)
            .asString("%.0f%%"));
    jfxProgressBarLabel.visibleProperty()
        .bind(jfxProgressBar.progressProperty()
            .greaterThanOrEqualTo(0.0));
  }

  public double getProgress() {
    return jfxProgressBar.getProgress();
  }

  public void setProgress(double progress) {
    jfxProgressBar.setProgress(progress);
  }

  public DoubleProperty progressProperty() {
    return jfxProgressBar.progressProperty();
  }
}
