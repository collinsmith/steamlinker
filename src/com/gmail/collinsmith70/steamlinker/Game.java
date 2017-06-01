package com.gmail.collinsmith70.steamlinker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Game {
  final StringProperty title;
  final ObjectProperty<Path> repo;
  final ObjectProperty<Path> folder;
  final ObjectProperty<Path> path;
  final ObjectProperty<FileSize> size;

  Game(@NotNull String title, @Nullable Path path) {
    this.title = new SimpleStringProperty(title);
    this.repo = new SimpleObjectProperty<>(path != null ? path.getParent() : null);
    this.folder = new SimpleObjectProperty<>(path != null ? path.getFileName() : null);
    this.path = new SimpleObjectProperty<>(path);
    this.size = new SimpleObjectProperty<>(null);
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
  ObjectProperty<FileSize> sizeProperty() {
    return size;
  }

  @Override
  public String toString() {
    return "title:" + title.get() + "; path:" + path.get() + "; size:" + size.get();
  }

  public static class FileSize implements Comparable<FileSize> {
    final long value;

    FileSize(long value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return Utils.humanReadableByteCount(value, true);
    }

    @Override
    public int compareTo(@NotNull FileSize o) {
      return Long.compare(value, o.value);
    }
  }
}
