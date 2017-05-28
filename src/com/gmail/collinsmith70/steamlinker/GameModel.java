package com.gmail.collinsmith70.steamlinker;

import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;

import org.jetbrains.annotations.NotNull;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

public class GameModel extends RecursiveTreeObject<GameModel> {
  final SimpleStringProperty title;
  final SimpleStringProperty path;
  final SimpleObjectProperty<FileSize> size;

  GameModel(@NotNull String title, @NotNull String path, long size) {
    this.title = new SimpleStringProperty(title);
    this.path = new SimpleStringProperty(path);
    this.size = new SimpleObjectProperty<>(new FileSize(size));
  }

  public String getTitle() {
    return title.get();
  }

  public void setTitle(String title) {
    this.title.set(title);
  }

  public String getPath() {
    return path.get();
  }

  public void setPath(String path) {
    this.path.set(path);
  }

  public FileSize getSize() {
    return size.get();
  }

  public void setSize(FileSize size) {
    this.size.set(size);
  }

  public static String humanReadableByteCount(long bytes, boolean si) {
    int unit = si ? 1000 : 1024;
    if (bytes < unit) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(unit));
    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
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
      return humanReadableByteCount(value, true);
    }

    @Override
    public int compareTo(@NotNull FileSize o) {
      return Long.compare(value, o.value);
    }
  }
}
