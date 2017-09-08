package com.gmail.collinsmith70.steamlinker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;

public class ReposControl extends HBox implements Initializable {
  @FXML private ListView<Path> jfxRepos;
  @FXML private Text jfxReposPlaceholder;
  @FXML private Button btnAddRepo;
  @FXML private Button btnRemoveRepo;

  @NotNull ObjectProperty<ObservableList<Path>> repos = new SimpleObjectProperty<>(FXCollections.observableArrayList());
  @NotNull StringProperty placeholderProperty = new SimpleStringProperty();
  @NotNull ObjectProperty<EventHandler<? super Game.TransferEvent>> transferEventHandler = new SimpleObjectProperty<>();

  // TODO: Got to be a cleaner way of doing this
  private Map<Path, RepoControl> repoControls = new ConcurrentHashMap<>();

  public ReposControl() {
    URL location = ReposControl.class.getResource("/layout/repos.fxml");
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
    jfxRepos.setCellFactory(param -> new ListCell<Path>() {
      {
        // TODO: .subtract(2) should reference actual border width
        prefWidthProperty().bind(jfxRepos.widthProperty().subtract(2));
        setMaxWidth(Control.USE_PREF_SIZE);
      }

      @Override
      protected void updateItem(Path path, boolean empty) {
        super.updateItem(path, empty);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        if (empty) {
          setText(null);
          setGraphic(null);
        } else if (path != null) {
          // TODO: Configure space properties to work based off of directory watcher so they update as file system changes
          RepoControl repo = new RepoControl(path, transferEventHandler);
          repo.refresh();
          repoControls.put(path, repo);

          setText(path.toString());
          setGraphic(repo);
        } else {
          setText(null);
          setGraphic(null);
        }
      }
    });
    jfxRepos.itemsProperty().unbind();
    jfxRepos.itemsProperty().bind(repos);
    jfxRepos.setOnMouseClicked(event -> {
      if (event.getButton() == MouseButton.PRIMARY && repos.get().isEmpty()) {
        event.consume();
        btnAddRepo.fire();
      }
    });

    MultipleSelectionModel<Path> selectionModel = jfxRepos.getSelectionModel();
    selectionModel.selectedIndexProperty().addListener(((observable, oldValue, newValue) -> {
      btnRemoveRepo.setDisable(newValue.intValue() == -1);
    }));

    jfxReposPlaceholder.textProperty().unbind();
    jfxReposPlaceholder.textProperty().bind(placeholderProperty);
  }

  @NotNull
  public ListView<Path> getListView() {
    return jfxRepos;
  }

  public void setItems(@Nullable ObservableList<Path> items) {
    repos.set(items);
  }

  @Nullable
  public ObservableList<Path> getItems() {
    return repos.get();
  }

  @NotNull
  public ObjectProperty<ObservableList<Path>> itemsProperty() {
    return repos;
  }

  @Nullable
  public RepoControl lookup(@NotNull Path path) {
    return repoControls.get(path);
  }

  @Nullable
  public String getPlaceholder() {
    return placeholderProperty.get();
  }

  public void setPlaceholder(@Nullable String text) {
    placeholderProperty.set(text);
  }

  @NotNull
  public StringProperty placeholderProperty() {
    return placeholderProperty;
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
  private void addRepo(@NotNull ActionEvent event) {
    event.consume();
    ObservableList<Path> repos = this.repos.get();
    DirectoryChooser directoryChooser = new DirectoryChooser();
    Optional.ofNullable(directoryChooser.showDialog(getScene().getWindow()))
        .map(File::toPath)
        .filter(path -> !repos.contains(path))
        .ifPresent(repos::add);
  }

  @FXML
  private void removeRepo(@NotNull ActionEvent event) {
    event.consume();
    ObservableList<Path> repos = this.repos.get();
    //noinspection unchecked
    MultipleSelectionModel<Path> selectionModel = jfxRepos.getSelectionModel();
    repos.remove(selectionModel.getSelectedIndex());
    selectionModel.clearSelection();
  }

  public void fireAddRepo() {
    btnAddRepo.fire();
  }
}
