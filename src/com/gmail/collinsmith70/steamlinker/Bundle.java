package com.gmail.collinsmith70.steamlinker;

import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.ResourceBundle;

public class Bundle {

  private Bundle() {}

  public static final ResourceBundle BUNDLE = ResourceBundle.getBundle("SteamLinker");

  @NotNull
  public static String translate(@NotNull String key, @NotNull Object... args) {
    String pattern = BUNDLE.getString(key);
    return MessageFormat.format(pattern, args);
  }

}
