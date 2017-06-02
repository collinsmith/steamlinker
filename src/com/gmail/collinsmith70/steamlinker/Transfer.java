package com.gmail.collinsmith70.steamlinker;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.scene.control.ProgressBar;

public class Transfer {
  final SimpleStringProperty title;
  final SimpleObjectProperty<Path> src;
  final SimpleObjectProperty<Path> dst;
  final SimpleDoubleProperty progress;
  final SimpleObjectProperty<Task> task;

  Transfer(@NotNull Game game, @NotNull Path dst) {
    this.title = new SimpleStringProperty();
    this.src = new SimpleObjectProperty<>();
    this.dst = new SimpleObjectProperty<>(dst);
    this.progress = new SimpleDoubleProperty(ProgressBar.INDETERMINATE_PROGRESS);
    this.title.bindBidirectional(game.title);
    this.src.bindBidirectional(game.path);
    this.task = new SimpleObjectProperty<>();
    this.task.addListener((observable, oldValue, newValue) -> {
      this.progress.bind(newValue.progressProperty());
    });
  }

}
