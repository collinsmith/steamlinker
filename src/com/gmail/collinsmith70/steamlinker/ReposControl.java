package com.gmail.collinsmith70.steamlinker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;

public class ReposControl extends HBox implements Initializable {
  @FXML private ListView<Path> jfxRepos;
  @FXML private Button btnAddRepo;
  @FXML private Button btnRemoveRepo;

  @NotNull ObjectProperty<ObservableList<Path>> repos = new SimpleObjectProperty<>(FXCollections.observableArrayList());

  public ReposControl() {
    URL location = ReposControl.class.getResource("/layout/repos.fxml");
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
          // TODO: Configure space properties to work based off of directory watcher so they
          // update as file system changes
          RepoControl repo = new RepoControl();
          repo.textProperty().bind(textProperty());

          File asFile = path.toFile();
          repo.useableSpaceProperty().set(asFile.getUsableSpace());
          repo.totalSpaceProperty().set(asFile.getTotalSpace());

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
    //jfxRepos.prefHeightProperty().bind(
    //    Bindings.max(Bindings.min(3, Bindings.size(libs)), 1)
    //        .multiply(63));
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
}
