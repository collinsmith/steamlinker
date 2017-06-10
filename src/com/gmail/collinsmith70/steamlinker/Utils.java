package com.gmail.collinsmith70.steamlinker;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

public class Utils {

  private Utils() {}


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
}
