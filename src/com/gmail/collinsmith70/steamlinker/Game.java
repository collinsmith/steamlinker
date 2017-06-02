package com.gmail.collinsmith70.steamlinker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Game {
  final StringProperty title;
  final ObjectProperty<Path> repo;
  final ObjectProperty<Path> folder;
  final ObjectProperty<Path> path;
  final LongProperty size;

  Game(@NotNull String title, @Nullable Path path) {
    this(title, path, Long.MIN_VALUE);
  }

  Game(@NotNull String title, @Nullable Path path, long size) {
    this.title = new SimpleStringProperty(title);
    this.repo = new SimpleObjectProperty<>(path != null ? path.getParent() : null);
    this.folder = new SimpleObjectProperty<>(path != null ? path.getFileName() : null);
    this.path = new SimpleObjectProperty<>(path);
    this.size = new SimpleLongProperty(size);
    this.path.addListener((observable, oldValue, newValue) -> {
      if (newValue != null) {
        this.repo.set(newValue.getParent());
        this.folder.set(newValue.getFileName());
      }
    });
  }

  @NotNull
  StringProperty titleProperty() {
    return title;
  }

  @NotNull
  ObjectProperty<Path> repoProperty() {
    return repo;
  }

  @NotNull
  ObjectProperty<Path> folderProperty() {
    return folder;
  }

  @NotNull
  ObjectProperty<Path> pathProperty() {
    return path;
  }

  @NotNull
  LongProperty sizeProperty() {
    return size;
  }

  @Override
  public String toString() {
    return "title:" + title.get() + "; path:" + path.get() + "; size:" + size.get();
  }
}
