package com.gmail.collinsmith70.steamlinker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public abstract class LinkerService {
  @Nullable public abstract Path findSteam();
  @NotNull public abstract Path toRealPath(@NotNull Path path) throws Exception;
  public abstract boolean isJunction(@NotNull Path path) throws Exception;
  public abstract void createJunction(@NotNull Path path, @NotNull Path target) throws Exception;
  public abstract void deleteJunction(@NotNull Path path) throws Exception;
}
