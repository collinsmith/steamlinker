package com.gmail.collinsmith70.steamlinker2;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javafx.concurrent.Task;

public class CopyTask extends Task {
  private static final boolean DEBUG_COPYING = false;

  private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

  @NotNull final File srcDir;
  @NotNull final File destDir;

  private long bytesCopied;
  private long totalSize;

  public CopyTask(@NotNull File srcDir, @NotNull File destDir) {
    // TODO: check validity (even though this should be checked at this point)
    this.srcDir = srcDir;
    this.destDir = destDir;
  }

  @NotNull
  public SizingTask calculateSize() {
    return new SizingTask();
  }

  public class SizingTask extends Task<Long> {
    private SizingTask() {
      updateProgress(0, 1);
    }

    @Override
    protected Long call() throws Exception {
      bytesCopied = 0;
      totalSize = FileUtils.sizeOfDirectory(srcDir);
      updateProgress(totalSize, totalSize);
      CopyTask.this.updateProgress(bytesCopied, totalSize);
      return totalSize;
    }
  }

  @Override
  protected Object call() throws Exception {
    if (!DEBUG_COPYING) {
      copyDirectoryToDirectory(srcDir, destDir);
    }

    return null;
  }

  public void copyDirectoryToDirectory(File srcDir, File destDir) throws IOException {
    if (srcDir == null) {
      throw new NullPointerException("Source must not be null");
    }
    if (srcDir.exists() && srcDir.isDirectory() == false) {
      throw new IllegalArgumentException("Source '" + destDir + "' is not a directory");
    }
    if (destDir == null) {
      throw new NullPointerException("Destination must not be null");
    }
    if (destDir.exists() && destDir.isDirectory() == false) {
      throw new IllegalArgumentException("Destination '" + destDir + "' is not a directory");
    }
    copyDirectory(srcDir, new File(destDir, srcDir.getName()), true);
  }

  public void copyDirectory(File srcDir, File destDir, boolean preserveFileDate) throws IOException {
    if (srcDir == null) {
      throw new NullPointerException("Source must not be null");
    }
    if (destDir == null) {
      throw new NullPointerException("Destination must not be null");
    }
    if (srcDir.exists() == false) {
      throw new FileNotFoundException("Source '" + srcDir + "' does not exist");
    }
    if (srcDir.isDirectory() == false) {
      throw new IOException("Source '" + srcDir + "' exists but is not a directory");
    }
    if (srcDir.getCanonicalPath().equals(destDir.getCanonicalPath())) {
      throw new IOException("Source '" + srcDir + "' and destination '" + destDir + "' are the same");
    }
    doCopyDirectory(srcDir, destDir, preserveFileDate);
  }

  private void doCopyDirectory(File srcDir, File destDir, boolean preserveFileDate) throws IOException {
    if (destDir.exists()) {
      if (destDir.isDirectory() == false) {
        throw new IOException("Destination '" + destDir + "' exists but is not a directory");
      }
    } else {
      if (destDir.mkdirs() == false) {
        throw new IOException("Destination '" + destDir + "' directory cannot be created");
      }
      if (preserveFileDate) {
        destDir.setLastModified(srcDir.lastModified());
      }
    }
    if (destDir.canWrite() == false) {
      throw new IOException("Destination '" + destDir + "' cannot be written to");
    }
    // recurse
    File[] files = srcDir.listFiles();
    if (files == null) {  // null if security restricted
      throw new IOException("Failed to list contents of " + srcDir);
    }
    for (int i = 0; i < files.length && !isCancelled(); i++) {
      File copiedFile = new File(destDir, files[i].getName());
      if (files[i].isDirectory()) {
        doCopyDirectory(files[i], copiedFile, preserveFileDate);
      } else {
        doCopyFile(files[i], copiedFile, preserveFileDate);
      }
    }
  }

  private void doCopyFile(File srcFile, File destFile, boolean preserveFileDate) throws IOException {
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

  public int copy(InputStream input, OutputStream output) throws IOException {
    long count = copyLarge(input, output);
    if (count > Integer.MAX_VALUE) {
      return -1;
    }
    return (int) count;
  }

  public long copyLarge(InputStream input, OutputStream output)
      throws IOException {
    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
    long count = 0;
    int n = 0;
    while (!isCancelled() && -1 != (n = input.read(buffer))) {
      updateProgress(bytesCopied += n, totalSize);
      output.write(buffer, 0, n);
      count += n;
    }
    return count;
  }
}
