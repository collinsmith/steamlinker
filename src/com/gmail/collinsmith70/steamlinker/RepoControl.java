package com.gmail.collinsmith70.steamlinker;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;

public class RepoControl extends HBox implements Initializable {
  private static final Logger LOG = Logger.getLogger(RepoControl.class);
  static {
    PatternLayout layout = new PatternLayout("[%-5p] %c::%M - %m%n");
    LOG.addAppender(new ConsoleAppender(layout, ConsoleAppender.SYSTEM_OUT));
  }

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
  private ObjectProperty<EventHandler<? super Game.TransferEvent>> transferEventHandler = new SimpleObjectProperty<>();

  private ContextMenu contextMenu;

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
    jfxLabel.setLabelFor(this);
    jfxLabel.textProperty().bind(repoProperty.asString());

    ChangeListener<Number> progressUpdater = (observable, oldValue, newValue) -> {
      long usedSpace = useableSpaceProperty.get();
      long availableSpace = totalSpaceProperty.get();
      jfxProgressBar.setProgress(availableSpace > 0 ? 1.0 - ((double) usedSpace / availableSpace) : 0);
      jfxProgressBarLabel.setText(Bundle.get("label.repos.space",
          Utils.bytesToString(usedSpace, false), Utils.bytesToString(availableSpace)));
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

    MenuItem browse = new MenuItem(Bundle.get("repo.browse"));
    browse.setOnAction(event -> {
      try {
        Main.service().browse(repoProperty.get());
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
      }
    });

    contextMenu = new ContextMenu();
    contextMenu.getItems().add(browse);
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

  @Nullable
  public EventHandler<? super Game.TransferEvent> getOnTransfer() {
    return transferEventHandler.get();
  }

  public void setOnTransfer(@Nullable EventHandler<? super Game.TransferEvent> value) {
    transferEventHandler.set(value);
  }

  @NotNull
  public ObjectProperty<EventHandler<? super Game.TransferEvent>> onTransferProperty() {
    return transferEventHandler;
  }

  @FXML
  private void onDragOver(@NotNull DragEvent event) {
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

  @FXML
  private void onDragDropped(@NotNull DragEvent event) {
    Dragboard db = event.getDragboard();
    if (!db.hasContent(Game.AS_LIST)) {
      return;
    }

    event.consume();

    Path repo = getRepo();
    //noinspection unchecked
    List<Game> games = (List<Game>) db.getContent(Game.AS_LIST);
    List<Game> filteredGames = games.stream()
        .filter(game -> !Objects.equals(repo, game.repo.get()))
        .collect(Collectors.toList());
    if (transferEventHandler.get() != null) {
      Game.TransferEvent transferEvent = new Game.TransferEvent(
          event.getSource(), this, Game.TransferEvent.TRANSFER, filteredGames, repo);
      transferEventHandler.get().handle(transferEvent);
    }
  }

  @FXML
  private void onMouseClicked(@NotNull MouseEvent event) {
    if (event.getButton() != MouseButton.SECONDARY) {
      return;
    }

    contextMenu.show(this, event.getScreenX(), event.getScreenY());
    event.consume();
  }
}
