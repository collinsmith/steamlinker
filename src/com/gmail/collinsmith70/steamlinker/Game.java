package com.gmail.collinsmith70.steamlinker;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
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
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
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
    return createTransfer(dst, true);
  }

  @NotNull
  Transfer createTransfer(@NotNull Path dst, boolean verify) {
    return new Transfer(dst, verify);
  }

  final class Transfer extends Task {
    private static final boolean DONT_COPY = false;

    private static final int MAX_ATTEMPTS = 5;
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    @NotNull final Game game;

    @NotNull final ReadOnlyObjectProperty<Path> src;
    @NotNull final ReadOnlyObjectProperty<Path> srcRepo;
    @NotNull final ReadOnlyObjectProperty<Path> dst;
    @NotNull final ReadOnlyObjectProperty<Path> dstRepo;
    @NotNull final ReadOnlyStringProperty status;
    @NotNull final ReadOnlyLongProperty totalSize;
    @NotNull final ReadOnlyBooleanProperty verify;

    private long bytesCopied;
    private long totalBytes;

    private Transfer(@NotNull Path dstDir, boolean verify) {
      this.game = Game.this;
      this.src = createReadOnlyWrapper(() -> game.path.get(), game.path);
      this.srcRepo = createReadOnlyWrapper(() -> PATH_TO_REPO.apply(this.src.get()), this.src);
      this.dstRepo = new ReadOnlyObjectWrapper<>(dstDir);
      this.dst = createReadOnlyWrapper(() -> this.dstRepo.get().resolve(PATH_TO_FOLDER.apply(this.src.get())), this.dstRepo);
      this.status = new SimpleStringProperty("initializing");
      this.totalSize = new SimpleLongProperty(0);
      this.verify = new SimpleBooleanProperty(verify);
      exceptionProperty().addListener((observable, oldValue, newValue) ->  {
        ((StringProperty) status).set("error");
        updateProgress(1, 1);
      });
    }

    @Override
    protected Object call() throws Exception {
      ((StringProperty) status).set("sizing");
      File src = this.src.get().toFile();
      totalBytes = FileUtils.sizeOfDirectory(src);
      ((LongProperty) totalSize).set(totalBytes);
      updateProgress(bytesCopied, totalBytes);
      // TODO: check if the transfer will fit and set fail state if not
      if (dstRepo.get().toFile().getUsableSpace() < totalBytes) {
        throw new NotEnoughSpaceException("Game will not fit in destination repository!");
      }

      ((StringProperty) status).set("copying");
      if (DONT_COPY || Files.isDirectory(dst.get())) {
        bytesCopied = totalBytes;
        updateProgress(bytesCopied, totalBytes);
      } else {
        copyDirectoryToDirectory(src, dstRepo.get().toFile(), verify.get());
      }

      ((StringProperty) status).set("complete");
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

    public void copyDirectoryToDirectory(@NotNull File srcDir, @NotNull File destDir, boolean validate) throws IOException {
      if (srcDir == null) {
        throw new NullPointerException("Source must not be null");
      }
      if (srcDir.exists() && !srcDir.isDirectory()) {
        throw new IllegalArgumentException("Source '" + destDir + "' is not a directory");
      }
      if (destDir == null) {
        throw new NullPointerException("Destination must not be null");
      }
      if (destDir.exists() && !destDir.isDirectory()) {
        throw new IllegalArgumentException("Destination '" + destDir + "' is not a directory");
      }
      copyDirectory(srcDir, new File(destDir, srcDir.getName()), validate, true);
    }

    public void copyDirectory(@NotNull File srcDir, @NotNull File destDir, boolean validate, boolean preserveFileDate) throws IOException {
      if (srcDir == null) {
        throw new NullPointerException("Source must not be null");
      }
      if (destDir == null) {
        throw new NullPointerException("Destination must not be null");
      }
      if (!srcDir.exists()) {
        throw new FileNotFoundException("Source '" + srcDir + "' does not exist");
      }
      if (!srcDir.isDirectory()) {
        throw new IOException("Source '" + srcDir + "' exists but is not a directory");
      }
      if (srcDir.getCanonicalPath().equals(destDir.getCanonicalPath())) {
        throw new IOException("Source '" + srcDir + "' and destination '" + destDir + "' are the same");
      }
      doCopyDirectory(srcDir, destDir, validate, preserveFileDate);
    }

    private void doCopyDirectory(@NotNull File srcDir, @NotNull File destDir, boolean validate, boolean preserveFileDate) throws IOException {
      if (destDir.exists()) {
        if (!destDir.isDirectory()) {
          throw new IOException("Destination '" + destDir + "' exists but is not a directory");
        }
      } else {
        if (!destDir.mkdirs()) {
          throw new IOException("Destination '" + destDir + "' directory cannot be created");
        }
        if (preserveFileDate) {
          destDir.setLastModified(srcDir.lastModified());
        }
      }
      if (!destDir.canWrite()) {
        throw new IOException("Destination '" + destDir + "' cannot be written to");
      }
      // recurse
      File[] files = srcDir.listFiles();
      if (files == null) {  // null if security restricted
        throw new IOException("Failed to list contents of " + srcDir);
      }
      for (int i = 0; i < files.length && !isCancelled(); i++) {
        File originalFile = files[i];
        File copiedFile = new File(destDir, originalFile.getName());
        if (originalFile.isDirectory()) {
          doCopyDirectory(originalFile, copiedFile, validate, preserveFileDate);
        } else {
          long chk1, chk2;
          for (int attempt = 0;; attempt++) {
            doCopyFile(originalFile, copiedFile, preserveFileDate);
            chk1 = FileUtils.checksumCRC32(originalFile);
            chk2 = FileUtils.checksumCRC32(copiedFile);
            if (chk1 == chk2) {
              break;
            } else if (attempt >= MAX_ATTEMPTS) {
              throw new IOException(Bundle.get("exception.verify", originalFile.getName()));
            }
          }
          // TODO: perform a checksum on the copied file and the source file if desired
        }
      }
    }

    private void doCopyFile(@NotNull File srcFile, @NotNull File destFile, boolean preserveFileDate) throws IOException {
      if (destFile.exists() && destFile.isDirectory()) {
        throw new IOException("Destination '" + destFile + "' exists but is a directory");
      }

      FileInputStream input = new FileInputStream(srcFile);
      try {
        FileOutputStream output = new FileOutputStream(destFile);
        try {
          copy(input, output);
        } finally {
          IOUtils.closeQuietly(output);
        }
      } finally {
        IOUtils.closeQuietly(input);
      }

      if (srcFile.length() != destFile.length()) {
        throw new IOException("Failed to copy full contents from '" +
            srcFile + "' to '" + destFile + "'");
      }
      if (preserveFileDate) {
        destFile.setLastModified(srcFile.lastModified());
      }
    }

    public int copy(@NotNull InputStream input, @NotNull OutputStream output) throws IOException {
      long count = copyLarge(input, output);
      if (count > Integer.MAX_VALUE) {
        return -1;
      }
      return (int) count;
    }

    public long copyLarge(@NotNull InputStream input, @NotNull OutputStream output) throws IOException {
      byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
      long count = 0;
      int n = 0;
      while (!isCancelled() && -1 != (n = input.read(buffer))) {
        updateProgress(bytesCopied += n, totalBytes);
        output.write(buffer, 0, n);
        count += n;
      }
      return count;
    }
  }

  public static final class TransferEvent extends Event {
    public static final EventType<TransferEvent> TRANSFER = new EventType<>("TRANSFER");
    final List<Game> games;
    final Path dstRepo;

    public TransferEvent(EventType<? extends Event> eventType, List<Game> games, Path dstRepo) {
      super(eventType);
      this.games = games;
      this.dstRepo = dstRepo;
    }

    public TransferEvent(Object source, EventTarget target, EventType<? extends Event> eventType,
                         List<Game> games, Path dstRepo) {
      super(source, target, eventType);
      this.games = games;
      this.dstRepo = dstRepo;
    }

    public List<Game> getGames() {
      return games;
    }

    public Path getDstRepo() {
      return dstRepo;
    }
  }
}
