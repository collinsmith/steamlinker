package com.gmail.collinsmith70.steamlinker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.DataFormat;

public class Game implements Serializable {
  public static final DataFormat DATA_FORMAT = new DataFormat(Game.class.getName() + "-list");

  transient StringProperty title = new SimpleStringProperty();
  transient ObjectProperty<Path> repo = new SimpleObjectProperty<>();
  transient ObjectProperty<Path> folder = new SimpleObjectProperty<>();
  transient ObjectProperty<Path> path = new SimpleObjectProperty<>();
  transient LongProperty size = new SimpleLongProperty();

  Game() {}

  Game(@NotNull String title, @Nullable Path path) {
    this(title, path, Long.MIN_VALUE);
  }

  Game(@NotNull String title, @Nullable Path path, long size) {
    this.title.set(title);
    this.repo.set(path != null ? path.getParent() : null);
    this.folder.set(path != null ? path.getFileName() : null);
    this.path.set(path);
    this.size.set(size);
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
    return title.get() + " (" + Utils.humanReadableByteCount(size.get(), true) + ") [" + path.get() + "]";
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    out.writeUTF(title.get());
    out.writeUTF(path.get().toString());
    out.writeLong(size.get());
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    String title = in.readUTF();
    String path = in.readUTF();
    long size = in.readLong();

    this.title = new SimpleStringProperty();
    this.repo = new SimpleObjectProperty<>();
    this.folder = new SimpleObjectProperty<>();
    this.path = new SimpleObjectProperty<>();
    this.size = new SimpleLongProperty();

    Path asPath = Paths.get(path);
    this.title.set(title);
    this.repo.set(asPath != null ? asPath.getParent() : null);
    this.folder.set(asPath != null ? asPath.getFileName() : null);
    this.path.set(asPath);
    this.size.set(size);
    this.path.addListener((observable, oldValue, newValue) -> {
      if (newValue != null) {
        this.repo.set(newValue.getParent());
        this.folder.set(newValue.getFileName());
      }
    });
  }

  @NotNull
  Transfer transferTo(@NotNull Path dst) {
    return new Transfer(dst);
  }

  public class Transfer {
    final Game game;
    final ObjectProperty<Path> src;
    final ObjectProperty<Path> dst;
    final DoubleProperty progress;
    final ObjectProperty<Task> task;

    private Transfer(@NotNull Path dst) {
      this.game = Game.this;
      this.src = game.path;
      this.dst = new SimpleObjectProperty<>(dst);
      this.progress = new SimpleDoubleProperty(ProgressBar.INDETERMINATE_PROGRESS);
      this.task = new SimpleObjectProperty<>();
      this.task.addListener((observable, oldValue, newValue) -> this.progress.bind(newValue.progressProperty()));
    }
  }
}
