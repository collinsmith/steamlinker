package com.gmail.collinsmith70.steamlinker2;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.WString;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JunctionSupport {

  private JunctionSupport() {
  }

  interface Kernel32 extends Library {
    int GetFileAttributesW(WString fileName);
  }

  static Kernel32 lib = null;

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

}
