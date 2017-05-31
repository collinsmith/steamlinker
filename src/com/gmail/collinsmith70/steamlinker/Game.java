package com.gmail.collinsmith70.steamlinker;

import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

public class Game extends RecursiveTreeObject<Game> {
  final SimpleStringProperty title;
  final SimpleObjectProperty<Path> repo;
  final SimpleObjectProperty<Path> path;
  final SimpleObjectProperty<FileSize> size;

  Game(@NotNull String title, @Nullable Path path) {
    this.title = new SimpleStringProperty(title);
    this.repo = new SimpleObjectProperty<>(path != null ? path.getParent() : null);
    this.path = new SimpleObjectProperty<>(path);
    this.size = new SimpleObjectProperty<>(null);
  }

  public String getTitle() {
    return title.get();
  }

  public void setTitle(String title) {
    this.title.set(title);
  }

  public Path getPath() {
    return path.get();
  }

  public void setPath(Path path) {
    this.path.set(path);
  }

  public FileSize getSize() {
    return size.get();
  }

  public void setSize(FileSize size) {
    this.size.set(size);
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
