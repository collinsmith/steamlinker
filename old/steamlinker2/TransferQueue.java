package com.gmail.collinsmith70.steamlinker2;

import java.io.IOException;
import java.net.URL;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;

public class TransferQueue extends HBox {

  @FXML ToggleButton btnPerformTransfers;
  @FXML Button btnRemoveTransfer;
  @FXML Button btnClearTransfers;
  @FXML TableView<Game.Transfer> transfersList;

  final ObservableList<Game.Transfer> items;

  public TransferQueue() {
    URL location = TransferQueue.class.getResource("/layout/transfer_queue.fxml");
    FXMLLoader loader = new FXMLLoader(location);
    loader.setRoot(this);
    loader.setController(this);

    try {
      loader.load();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    this.items = FXCollections.observableArrayList();
    this.transfersList.setItems(items);
  }
}
