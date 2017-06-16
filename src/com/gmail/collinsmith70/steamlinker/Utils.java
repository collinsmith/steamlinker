package com.gmail.collinsmith70.steamlinker;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.WString;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.stage.Window;
import javafx.util.StringConverter;

public class Utils {
  private static final boolean DEBUG_JUNCTION_CREATION = Main.DEBUG_MODE && true;

  private static final Logger LOG = Logger.getLogger(Utils.class);
  static {
    PatternLayout layout = new PatternLayout("[%-5p] %c::%M - %m%n");
    LOG.addAppender(new ConsoleAppender(layout, ConsoleAppender.SYSTEM_OUT));
  }

  private Utils() {}

  public static final StringConverter<ObservableList<Path>> PATHS_CONVERTER = new
      StringConverter<ObservableList<Path>>() {
    @Override
    @NotNull
    public String toString(@Nullable ObservableList<Path> paths) {
      return paths != null
          ? String.join(";", paths.stream()
          .map(Path::toString)
          .collect(Collectors.joining(";")))
          : "";
    }

    @Override
    @NotNull
    public ObservableList<Path> fromString(@Nullable String string) {
      return string != null && !string.isEmpty()
          ? FXCollections.observableList(Arrays.stream(string.split(";"))
          .map(item -> Paths.get(item))
          .collect(Collectors.toList()))
          : FXCollections.observableArrayList();
    }
  };
  public static final StringConverter<Path> PATH_CONVERTER = new StringConverter<Path>() {
    @Override
    @NotNull
    public String toString(@Nullable Path path) {
      return path != null ? path.toString() : "";
    }

    @Override
    @Nullable
    public Path fromString(@Nullable String string) {
      return string != null && !string.isEmpty() ? Paths.get(string) : null;
    }
  };

  @NotNull
  public static Alert newExceptionAlert(@NotNull Throwable throwable) {
    return newExceptionAlert((Window) null, throwable);
  }

  @NotNull
  public static Alert newExceptionAlert(@NotNull String key, @NotNull Throwable throwable) {
    return newExceptionAlert(null, key, throwable);
  }

  @NotNull
  public static Alert newExceptionAlert(@Nullable Window owner, @NotNull Throwable throwable) {
    return newExceptionAlert(owner, "alert.exception", throwable);
  }

  @NotNull
  public static Alert newExceptionAlert(@Nullable Window owner, @NotNull String key, @NotNull Throwable throwable) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle(Bundle.get("alert.exception.title"));
    alert.setHeaderText(null);
    alert.setContentText(Bundle.get(key, throwable.getLocalizedMessage()));
    alert.getDialogPane().setExpandableContent(((Supplier<TextArea>) () -> {
      TextArea textArea = new TextArea();
      textArea.setText(ExceptionUtils.getStackTrace(throwable));
      textArea.setPrefColumnCount(64);
      textArea.setEditable(false);
      return textArea;
    }).get());
    alert.initOwner(owner);
    return alert;
  }

  @Nullable
  public static Path tryFindSteam() {
    if (SystemUtils.IS_OS_WINDOWS) {
      String drive = System.getenv("SystemDrive");
      Path steamDir = Paths.get(drive, "Program Files (x86)\\Steam\\steamapps\\common");
      if (Files.isDirectory(steamDir)) {
        return steamDir;
      }

      return null;
    }

    return null;
  }

  @NotNull
  public static String bytesToString(long bytes) {
    return bytesToString(bytes, true);
  }

  @NotNull
  public static String bytesToString(long bytes, boolean si) {
    int unit = si ? 1000 : 1024;
    if (bytes < unit) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(unit));
    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
  }

  @NotNull
  public static Path toRealPath(@NotNull Path path) throws IOException, InterruptedException {
    String fileName = path.getFileName().toString();
    ProcessBuilder processBuilder = new ProcessBuilder(
        "cmd.exe", "/c",
        "dir", path.getParent().toString(),
        "|",
        "findstr", "/c:" + fileName + " [");
    processBuilder.redirectErrorStream(true);

    Process process = processBuilder.start();
    String line = null;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      if ((line = reader.readLine()) == null) {
        throw new AssertionError("Expected non-null input.");
      }

      while (reader.readLine() != null) {}
    }

    Pattern pattern = Pattern.compile("\\[.+\\]");
    Matcher matcher = pattern.matcher(line);
    if (!matcher.find()) {
      throw new AssertionError("Expected [path] in string: " + line);
    }

    String part = matcher.group();
    part = part.substring(1, part.length() - 1);

    process.waitFor();
    return Paths.get(part);
  }

  private interface Kernel32 extends Library {
    int GetFileAttributesW(WString fileName);
  }

  private static Kernel32 lib = null;

  public static int getWin32FileAttributes(@NotNull Path path) throws IOException {
    if (lib == null) {
      synchronized (Kernel32.class) {
        lib = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);
      }
    }

    return lib.GetFileAttributesW(new WString(path.toAbsolutePath().toString()));
  }

  public static boolean isJunctionOrSymlink(@NotNull Path path) throws IOException {
    if (!Files.exists(path)) {
      return false;
    }

    int attributes = getWin32FileAttributes(path);
    if (-1 == attributes) {
      return false;
    }

    return ((0x400 & attributes) != 0);
  }

  public static void createJunction(@NotNull Path path, @NotNull Path target) throws IOException {
    ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", path.toString(), target.toString());
    builder.redirectErrorStream(true);
    try {
      Process p = builder.start();
      if (DEBUG_JUNCTION_CREATION) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
          String line;
          while ((line = r.readLine()) != null) {
            LOG.info(line);
          }
        }
      }
    } catch (IOException e) {
      LOG.error(e.getMessage(), e);
      throw e;
    }
  }
}
