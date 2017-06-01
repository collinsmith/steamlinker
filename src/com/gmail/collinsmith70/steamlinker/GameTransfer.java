package com.gmail.collinsmith70.steamlinker;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

public class GameTransfer {
  final SimpleStringProperty title;
  final SimpleObjectProperty<Path> src;
  final SimpleObjectProperty<Path> dst;
  final SimpleDoubleProperty progress;

  GameTransfer(@NotNull String title, @NotNull Path src, @NotNull Path dst) {
    this.title = new SimpleStringProperty(title);
    this.src = new SimpleObjectProperty<>(src);
    this.dst = new SimpleObjectProperty<>(dst);
    this.progress = new SimpleDoubleProperty();
  }

}
