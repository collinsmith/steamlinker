package com.gmail.collinsmith70.steamlinker;

import org.jetbrains.annotations.NotNull;

import javafx.beans.property.SimpleStringProperty;

public class GameModel {
  final SimpleStringProperty title;
  final SimpleStringProperty path;

  GameModel(@NotNull String title, @NotNull String path) {
    this.title = new SimpleStringProperty(title);
    this.path = new SimpleStringProperty(path);
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

  @Override
  public String toString() {
    return "title:" + title.getValue() + "; path:" + path.getValue();
  }
}
