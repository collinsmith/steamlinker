package com.gmail.collinsmith70.steamlinker;

import com.gmail.collinsmith70.steamlinker.Game.Transfer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;

public class TransfersControl extends HBox implements Initializable {

  @FXML private TableView<Transfer> jfxTransfers;
  @FXML private TableColumn<Transfer, String> jfxTransfersTitleColumn;
  @FXML private TableColumn<Transfer, Double> jfxTransfersProgressColumn;
  @FXML private TableColumn<Transfer, Path> jfxTransfersSourceColumn;
  @FXML private TableColumn<Transfer, Path> jfxTransfersDestinationColumn;

  @NotNull ObjectProperty<ObservableList<Transfer>> transfers = new SimpleObjectProperty<>(FXCollections.observableArrayList());
  @NotNull ObjectProperty<ObservableList<Path>> libs = new SimpleObjectProperty<>(FXCollections.emptyObservableList());

  public TransfersControl() {
    URL location = RepoControl.class.getResource("/layout/transfers.fxml");
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
    jfxTransfers.itemsProperty().unbind();
    jfxTransfers.itemsProperty().bind(transfers);

    jfxTransfersTitleColumn.setCellValueFactory(param -> param.getValue().game.title);

    jfxTransfersProgressColumn.setCellFactory(param -> new TableCell<Transfer, Double>() {
      private final ProgressBarControl progressBar = new ProgressBarControl();
      ObservableValue<Double> observable;

      @Override
      protected void updateItem(Double item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
          setGraphic(null);
        } else {
          progressBar.progressProperty().unbind();
          final TableColumn<Game.Transfer, Double> column = getTableColumn();
          observable = column == null ? null : column.getCellObservableValue(getIndex());
          if (observable != null) {
            progressBar.progressProperty().bind(observable);
          } else if (item != null) {
            progressBar.setProgress(item);
          }

          setGraphic(progressBar);
        }
      }
    });
    jfxTransfersProgressColumn.setCellValueFactory((TableColumn.CellDataFeatures<Transfer, Double> param)
        -> param.getValue().progressProperty().asObject());

    jfxTransfersSourceColumn.setCellFactory(param -> new TableCell<Transfer, Path>() {
      @Override
      protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);
        MainController.configureRepoCell(this, libs.get(), item, empty);
      }
    });
    jfxTransfersSourceColumn.setStyle("-fx-alignment: CENTER-LEFT;");
    //jfxTransfersSourceColumn.setStyle("-fx-text-overrun: LEADING-ELLIPSIS;");
    jfxTransfersSourceColumn.setCellValueFactory(param -> param.getValue().srcRepo);

    jfxTransfersDestinationColumn.setCellFactory(param -> new TableCell<Transfer, Path>() {
      @Override
      protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);
        MainController.configureRepoCell(this, libs.get(), item, empty);
      }
    });
    jfxTransfersDestinationColumn.setStyle("-fx-alignment: CENTER-LEFT;");
    //jfxTransfersDestinationColumn.setStyle("-fx-text-overrun: LEADING-ELLIPSIS;");
    jfxTransfersDestinationColumn.setCellValueFactory(param -> param.getValue().dstRepo);

    jfxTransfersTitleColumn.prefWidthProperty().bind(jfxTransfers.widthProperty().multiply(0.375).subtract(15));
    jfxTransfersProgressColumn.prefWidthProperty().bind(jfxTransfers.widthProperty().multiply(0.125));
    jfxTransfersSourceColumn.prefWidthProperty().bind(jfxTransfers.widthProperty().multiply(0.25));
    jfxTransfersDestinationColumn.prefWidthProperty().bind(jfxTransfers.widthProperty().multiply(0.25));
  }

  public void setItems(@Nullable ObservableList<Transfer> items) {
    transfers.set(items);
  }

  @Nullable
  public ObservableList<Transfer> getItems() {
    return transfers.get();
  }

  @NotNull
  public ObjectProperty<ObservableList<Transfer>> itemsProperty() {
    return transfers;
  }

  @NotNull
  public ObjectProperty<ObservableList<Path>> libsProperty() {
    return libs;
  }

}
