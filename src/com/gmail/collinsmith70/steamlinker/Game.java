package com.gmail.collinsmith70.steamlinker;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;
import javafx.scene.input.DataFormat;

public class Game implements Serializable {

  public static final DataFormat GAME = new DataFormat(Game.class.getName());
  public static final DataFormat AS_LIST = new DataFormat(Game.class.getName() + "-list");

  private static final Function<Path, Path> PATH_TO_REPO = path -> path != null ? path.getParent() : null;
  private static final Function<Path, Path> PATH_TO_FOLDER = path -> path != null ? path.getFileName() : null;

  @NotNull transient StringProperty title;
  @NotNull transient ObjectProperty<Path> path;
  @NotNull transient ReadOnlyObjectProperty<Path> repo;
  @NotNull transient ReadOnlyObjectProperty<Path> folder;
  @NotNull transient LongProperty size;
  @NotNull transient BooleanProperty brokenJunction;

  @NotNull
  public StringProperty titleProperty() {
    return title;
  }

  @NotNull
  public ObjectProperty<Path> pathProperty() {
    return path;
  }

  @NotNull
  public ReadOnlyObjectProperty<Path> repoProperty() {
    return repo;
  }

  @NotNull
  public ReadOnlyObjectProperty<Path> folderProperty() {
    return folder;
  }

  @NotNull
  public LongProperty sizeProperty() {
    return size;
  }

  @NotNull
  public BooleanProperty brokenJunctionProperty() {
    return brokenJunction;
  }

  @NotNull
  Game init(@NotNull String title, @Nullable Path path) {
    return init(title, path, Long.MIN_VALUE);
  }

  @NotNull
  Game init(@NotNull String title, @Nullable Path path, boolean broken) {
    return init(title, path, true, Long.MIN_VALUE);
  }

  @NotNull
  Game init(@NotNull String title, @Nullable Path path, long size) {
    return init(title, path, false, size);
  }

  @NotNull
  Game init(@NotNull String title, @Nullable Path path, boolean broken, long size) {
    this.title = new SimpleStringProperty(title);
    this.path = new SimpleObjectProperty<>(path);
    this.repo = createReadOnlyWrapper(() -> PATH_TO_REPO.apply(Game.this.path.get()), this.path);
    this.folder = createReadOnlyWrapper(() -> PATH_TO_FOLDER.apply(Game.this.path.get()), this.path);
    this.size = new SimpleLongProperty(size);
    this.brokenJunction = new SimpleBooleanProperty(broken);
    this.path.addListener((observable, oldValue, newValue) -> {
      if (newValue == null) {
        brokenJunction.set(true);
      }
    });
    return this;
  }

  private static <T> ReadOnlyObjectProperty<T> createReadOnlyWrapper(Callable<T> func, Observable... dependencies) {
    ReadOnlyObjectWrapper<T> wrapper = new ReadOnlyObjectWrapper<>();
    wrapper.bind(Bindings.createObjectBinding(func, dependencies));
    return wrapper;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == null) {
      return false;
    } else if (obj == this) {
      return true;
    } else if (!Game.class.isAssignableFrom(obj.getClass())) {
      return false;
    }

    final Game other = (Game) obj;
    return Objects.equals(title.get(), other.title.get())
        && Objects.equals(path.get(), other.path.get())
        && Objects.equals(size.get(), other.size.get());
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 37 * result + (title.get() != null ? title.get().hashCode() : 0);
    result = 37 * result + (path.get() != null ? path.get().hashCode() : 0);
    result = 37 * result + Long.hashCode(size.get());
    return result;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("title", title.get())
        .append("path", path.get())
        .append("size", Utils.bytesToString(size.get()))
        .toString();
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    out.writeUTF(title.get());
    out.writeUTF(path.get().toString());
    out.writeLong(size.get());
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    init(in.readUTF(), Paths.get(in.readUTF()), in.readLong());
  }

  @NotNull
  Transfer createTransfer(@NotNull Path dst) {
    return new Transfer(dst);
  }

  final class Transfer extends Task {
    @NotNull final Game game;

    @NotNull final ReadOnlyObjectProperty<Path> src;
    @NotNull final ReadOnlyObjectProperty<Path> srcRepo;
    @NotNull final ReadOnlyObjectProperty<Path> dst;
    @NotNull final ReadOnlyObjectProperty<Path> dstRepo;

    private Transfer(@NotNull Path dst) {
      this.game = Game.this;
      this.src = createReadOnlyWrapper(() -> game.path.get(), game.path);
      this.srcRepo = createReadOnlyWrapper(() -> PATH_TO_REPO.apply(this.src.get()), this.src);
      this.dst = new ReadOnlyObjectWrapper<>(dst);
      this.dstRepo = createReadOnlyWrapper(() -> PATH_TO_REPO.apply(this.dst.get()), this.dst);
    }

    @Override
    protected Object call() throws Exception {

      return null;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (obj == null) {
        return false;
      } else if (obj == this) {
        return true;
      } else if (!Transfer.class.isAssignableFrom(obj.getClass())) {
        return false;
      }

      final Transfer other = (Transfer) obj;
      return Objects.equals(game, other.game)
          /** src should have been checked by {@link Game#equals(Object)}*/
          //&& Objects.equals(src.get(), other.src.get());
          && Objects.equals(dst.get(), other.dst.get());
    }

    @Override
    public int hashCode() {
      int result = 17;
      result = 37 * result + (game != null ? game.hashCode() : 0);
      /** src should have been hashed by {@link Game#hashCode()}*/
      //result = 37 * result + (src.get() != null ? src.get().hashCode() : 0);
      result = 37 * result + (dst.get() != null ? dst.get().hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      return src + "->" + dst;
    }
  }

  public static final class TransferEvent extends Event {
    public static final EventType<TransferEvent> TRANSFER = new EventType<>("TRANSFER");
    final List<Game> src;
    final Path dst;

    public TransferEvent(EventType<? extends Event> eventType, List<Game> src, Path dst) {
      super(eventType);
      this.src = src;
      this.dst = dst;
    }

    public TransferEvent(Object source, EventTarget target, EventType<? extends Event> eventType,
                         List<Game> src, Path dst) {
      super(source, target, eventType);
      this.src = src;
      this.dst = dst;
    }

    public List<Game> getSrc() {
      return src;
    }

    public Path getDst() {
      return dst;
    }
  }
}
