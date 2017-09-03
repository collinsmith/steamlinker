package com.gmail.collinsmith70.steamlinker;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.WString;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WindowsLinkerService extends LinkerService {
  private static final boolean DEBUG_JUNCTION_CREATION = Main.DEBUG_MODE && false;

  private static final Logger LOG = Logger.getLogger(WindowsLinkerService.class);
  static {
    PatternLayout layout = new PatternLayout("[%-5p] %c::%M - %m%n");
    LOG.addAppender(new ConsoleAppender(layout, ConsoleAppender.SYSTEM_OUT));
  }

  @Override
  public void browse(@NotNull Path path) throws Exception {
    Runtime.getRuntime().exec("explorer.exe " + path);
  }

  @Nullable
  @Override
  public Path findSteam() {
    String drive = System.getenv("SystemDrive");
    Path steamDir = Paths.get(drive, "Program Files (x86)\\Steam\\steamapps\\common");
    if (Files.isDirectory(steamDir)) {
      return steamDir;
    }

    return null;
  }

  @NotNull
  @Override
  public Path toRealPath(@NotNull Path path) throws Exception {
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

      while (reader.readLine() != null);
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

  @Override
  public boolean isJunction(@NotNull Path path) throws Exception {
    if (!Files.exists(path)) {
      return false;
    }

    int attributes = getWin32FileAttributes(path);
    if (-1 == attributes) {
      return false;
    }

    return ((0x400 & attributes) != 0);
  }

  @Override
  public void createJunction(@NotNull Path path, @NotNull Path target) throws Exception {
    ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", path.toString(), target.toString());
    builder.redirectErrorStream(true);
    Process p = builder.start();
    if (DEBUG_JUNCTION_CREATION) {
      try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
        String line;
        while ((line = r.readLine()) != null) {
          LOG.info(line);
        }
      }
    }

    p.waitFor();
  }

  @Override
  public void deleteJunction(@NotNull Path path) throws Exception {
    ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "rmdir", path.toString());
    builder.redirectErrorStream(true);
    Process p = builder.start();
    if (DEBUG_JUNCTION_CREATION) {
      try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
        String line;
        while ((line = r.readLine()) != null) {
          LOG.info(line);
        }
      }
    }

    p.waitFor();
  }

  private interface Kernel32 extends Library {
    int GetFileAttributesW(WString fileName);
  }

  private static Kernel32 lib = null;

  public static int getWin32FileAttributes(@NotNull Path path) throws Exception {
    if (lib == null) {
      synchronized (Kernel32.class) {
        lib = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);
      }
    }

    return lib.GetFileAttributesW(new WString(path.toAbsolutePath().toString()));
  }
}
