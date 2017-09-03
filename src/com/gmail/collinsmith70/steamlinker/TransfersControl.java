package com.gmail.collinsmith70.steamlinker;

import com.gmail.collinsmith70.steamlinker.Game.Transfer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.FutureTask;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.util.converter.DefaultStringConverter;

public class TransfersControl extends HBox implements Initializable {
  private static final boolean DEBUG_TRANSFERS = Main.DEBUG_MODE && true;

  private static final boolean USE_TOOLTIP_FOR_TRANSFER_EXCEPTIONS = false;

  @FXML private TableView<Transfer> jfxTransfers;
  @FXML private TableColumn<Transfer, String> jfxTransfersTitleColumn;
  @FXML private TableColumn<Transfer, Double> jfxTransfersProgressColumn;
  @FXML private TableColumn<Transfer, Path> jfxTransfersSourceColumn;
  @FXML private TableColumn<Transfer, Path> jfxTransfersDestinationColumn;
  @FXML private TableColumn<Transfer, String> jfxTransfersStatusColumn;
  @FXML private TableColumn<Transfer, Long> jfxTransfersSpeedColumn;
  @FXML private TableColumn<Transfer, Long> jfxTransfersEtaColumn;

  @FXML private Button btnClearTransfers;

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
    jfxTransfers.setTableMenuButtonVisible(true);
    jfxTransfers.itemsProperty().unbind();
    jfxTransfers.itemsProperty().bind(transfers);

    jfxTransfersTitleColumn.setCellValueFactory(param -> param.getValue().game.title);
    jfxTransfersTitleColumn.setCellFactory(Callback -> new TextFieldTableCell<Game.Transfer, String>(new DefaultStringConverter()) {
      @Override
      public void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
          setGraphic(null);
        }

        TableRow<Game.Transfer> row = getTableRow();
        Game.Transfer transfer = row.getItem();
        if (transfer != null && transfer.exceptionProperty().isNotNull().get()) {
          Throwable throwable = (Throwable) transfer.exceptionProperty().get();
          Node graphic = new FontIcon("gmi-warning:20:yellow");
          Tooltip tooltip = new Tooltip(throwable.getLocalizedMessage());
          tooltip.setFont(new Font(tooltip.getFont().getName(), 12));
          Tooltip.install(graphic, tooltip);
          graphic.setOnMouseClicked(event -> {
            if (USE_TOOLTIP_FOR_TRANSFER_EXCEPTIONS) {
              tooltip.show(graphic, event.getScreenX(), event.getScreenY());
              tooltip.setAutoHide(true);
              event.consume();
            } else if (throwable instanceof NotEnoughSpaceException) {
              Alert alert = new Alert(Alert.AlertType.ERROR);
              alert.setTitle(Bundle.get("alert.transfer.wont.fit.title"));
              alert.setContentText(Bundle.get("alert.transfer.wont.fit"));
              alert.setHeaderText(null);
              alert.getButtonTypes().setAll(ButtonType.OK);
              alert.initOwner(getScene().getWindow());
              alert.getDialogPane().setExpandableContent(new TextArea(
                  Bundle.get("alert.transfer.wont.fit.expanded",
                      Utils.bytesToString(transfer.totalSize.get()),
                      Utils.bytesToString(transfer.dstRepo.get().toFile().getUsableSpace()),
                      transfer.game.title.get())
              ));
              alert.show();
            } else {
              Utils.newExceptionAlert(getScene().getWindow(), throwable).show();
            }
          });
          setGraphic(graphic);
        } else {
          setGraphic(null);
        }
      }
    });

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
            Game.Transfer transfer = (Game.Transfer) getTableRow().getItem();
            if (transfer != null && transfer.exceptionProperty().isNotNull().get()) {
              progressBar.throwError();
            }
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

    jfxTransfersStatusColumn.setCellValueFactory(param -> param.getValue().status);
    if (!DEBUG_TRANSFERS) {
      jfxTransfersStatusColumn.setVisible(false);
    }

    jfxTransfersSpeedColumn.setStyle("-fx-alignment: CENTER;");
    jfxTransfersSpeedColumn.setCellFactory(param -> new TableCell<Transfer, Long>() {
      @Override
      protected void updateItem(Long item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
          setText(null);
        } else {
          setText(Utils.bytesToString(item.longValue()) + "/s");
        }
      }
    });
    jfxTransfersSpeedColumn.setCellValueFactory((TableColumn.CellDataFeatures<Transfer, Long> param)
        -> param.getValue().bytesPerSec.asObject());

    jfxTransfersEtaColumn.setStyle("-fx-alignment: CENTER;");
    jfxTransfersEtaColumn.setCellFactory(param -> new TableCell<Transfer, Long>() {
      @Override
      protected void updateItem(Long item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
          setText(null);
        } else {
          long hrs = item / 3600;
          long rem = item % 3600;
          long mins = rem / 60;
          long secs = rem % 60;
          setText(String.format("%d:%02d:%02d", hrs, mins, secs));
        }
      }
    });
    jfxTransfersEtaColumn.setCellValueFactory((TableColumn.CellDataFeatures<Transfer, Long> param)
        -> param.getValue().eta.asObject());

    jfxTransfersTitleColumn.prefWidthProperty().bind(jfxTransfers.widthProperty().multiply(0.25).subtract(15));
    jfxTransfersProgressColumn.prefWidthProperty().bind(jfxTransfers.widthProperty().multiply(0.125));
    jfxTransfersSourceColumn.prefWidthProperty().bind(jfxTransfers.widthProperty().multiply(0.25));
    jfxTransfersDestinationColumn.prefWidthProperty().bind(jfxTransfers.widthProperty().multiply(0.25));
    jfxTransfersSpeedColumn.prefWidthProperty().bind(jfxTransfers.widthProperty().multiply(0.0625));
    jfxTransfersEtaColumn.prefWidthProperty().bind(jfxTransfers.widthProperty().multiply(0.0625));
    jfxTransfersStatusColumn.prefWidthProperty().bind(jfxTransfers.widthProperty().multiply(0.125));
  }

  public void setItems(@Nullable ObservableList<Transfer> items) {
    transfers.set(items);
    if (items != null) {
      items.addListener((ListChangeListener<Game.Transfer>) c -> {
        while (c.next()) {
          if (c.wasAdded()) {
            List<? extends Transfer> transfers = c.getAddedSubList();
            transfers.forEach(transfer -> {
              transfer.exceptionProperty().addListener((observable, oldValue, newValue) -> {
                jfxTransfers.refresh();
              });
            });
          }
        }
      });
    }
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

  @FXML
  private void clearTransfers(@NotNull ActionEvent event) {
    event.consume();
    ObservableList<Game.Transfer> transfers = this.transfers.get();
    transfers.removeIf(FutureTask::isDone);
  }

}
