package com.gmail.collinsmith70.steamlinker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;

public class RepoControl extends HBox implements Initializable {
  private static final String SPACE_AVAILABLE = "repo-space-available";
  private static final String SPACE_LIMITED = "repo-space-limited";
  private static final String[] BAR_STYLES = { SPACE_AVAILABLE, SPACE_LIMITED };
  private static void setProgressBarStyleClass(@NotNull ProgressBar bar, @NotNull String styleClass) {
    bar.getStyleClass().removeAll(BAR_STYLES);
    bar.getStyleClass().add(styleClass);
  }

  @FXML private Label jfxLabel;
  @FXML private ProgressBar jfxProgressBar;
  @FXML private Label jfxProgressBarLabel;

  private ObjectProperty<Path> repoProperty = new SimpleObjectProperty<>();
  private LongProperty useableSpaceProperty = new SimpleLongProperty();
  private LongProperty totalSpaceProperty = new SimpleLongProperty();

  public RepoControl() {
    this(null);
  }

  public RepoControl(@Nullable Path repo) {
    URL location = RepoControl.class.getResource("/layout/repo.fxml");
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

    repoProperty.set(repo);
  }

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    ChangeListener<Number> progressUpdater = (observable, oldValue, newValue) -> {
      long usedSpace = useableSpaceProperty.get();
      long availableSpace = totalSpaceProperty.get();
      jfxProgressBar.setProgress(availableSpace > 0 ? 1.0 - ((double) usedSpace / availableSpace) : 0);
      jfxProgressBarLabel.setText(Bundle.get("label.repos.space",
          Utils.bytesToString(usedSpace), Utils.bytesToString(availableSpace)));
    };
    useableSpaceProperty.addListener(progressUpdater);
    totalSpaceProperty.addListener(progressUpdater);
    jfxProgressBar.progressProperty().addListener((observable, oldValue, newValue) -> {
      double progress = newValue == null ? 0 : newValue.doubleValue();
      if (progress < 0.95) {
        setProgressBarStyleClass(jfxProgressBar, SPACE_AVAILABLE);
      } else {
        setProgressBarStyleClass(jfxProgressBar, SPACE_LIMITED);
      }
    });
  }

  public String getText() {
    return textProperty().get();
  }

  public void setText(String text) {
    textProperty().set(text);
  }

  public StringProperty textProperty() {
    return jfxLabel.textProperty();
  }

  public LongProperty useableSpaceProperty() {
    return useableSpaceProperty;
  }

  public LongProperty totalSpaceProperty() {
    return totalSpaceProperty;
  }

  @Nullable
  public Path getRepo() {
    return repoProperty.get();
  }

  public void setRepo(@Nullable Path path) {
    repoProperty.set(path);
  }

  @NotNull
  public ObjectProperty<Path> repoProperty() {
    return repoProperty;
  }

  @FXML
  private void onDragOver(DragEvent event) {
    Dragboard db = event.getDragboard();
    if (!db.hasContent(Game.AS_LIST)) {
      return;
    }

    event.consume();

    Path repo = getRepo();
    //noinspection unchecked
    List<Game> games = (List<Game>) db.getContent(Game.AS_LIST);
    if (!games.stream().allMatch(game -> Objects.equals(repo, game.repo.get()))) {
      event.acceptTransferModes(TransferMode.ANY);
    }
  }
}
