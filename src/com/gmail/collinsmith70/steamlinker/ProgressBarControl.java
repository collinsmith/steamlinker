package com.gmail.collinsmith70.steamlinker;

import org.jetbrains.annotations.NotNull;

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
  @FXML ProgressBar jfxProgressBar;
  @FXML Label jfxProgressBarLabel;

  private static final String PROGRESS_OKAY = "progressbar-okay";
  private static final String PROGRESS_ERROR = "progressbar-error";
  private final String[] BAR_STYLES = {PROGRESS_OKAY, PROGRESS_ERROR};

  private void setProgressBarStyleClass(@NotNull String styleClass) {
    jfxProgressBar.getStyleClass().removeAll(BAR_STYLES);
    jfxProgressBar.getStyleClass().add(styleClass);
  }

  public ProgressBarControl() {
    URL location = RepoControl.class.getResource("/layout/progressbar.fxml");
    FXMLLoader loader = new FXMLLoader();
    loader.setRoot(this);
    loader.setController(this);
    loader.setLocation(location);
    loader.setResources(Bundle.BUNDLE);

    try {
      loader.load();
    } catch (Throwable t) {
      throw new RuntimeException(t);
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
    setProgressBarStyleClass(PROGRESS_OKAY);
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

  public void throwError() {
    setProgressBarStyleClass(PROGRESS_ERROR);
  }
}
