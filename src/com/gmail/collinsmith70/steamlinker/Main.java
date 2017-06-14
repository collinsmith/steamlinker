package com.gmail.collinsmith70.steamlinker;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {

  static final boolean DEBUG_MODE = true;
  private static final boolean DEBUG_PREFERENCE_CHANGES = DEBUG_MODE && true;

  private static final Logger LOG = Logger.getLogger(Main.class);
  static {
    PatternLayout layout = new PatternLayout("[%-5p] %c::%M - %m%n");
    LOG.addAppender(new ConsoleAppender(layout, ConsoleAppender.SYSTEM_OUT));
  }

  private static final boolean CLEAR_PREFERENCES = true;
  static final Preferences PREFERENCES = Preferences.userNodeForPackage(Main.class);
  static {
    if (CLEAR_PREFERENCES) {
      LOG.debug("Clearing preferences...");
      try {
        PREFERENCES.clear();
      } catch (BackingStoreException e) {
        LOG.error(e.getMessage(), e);
      }
    }

    if (DEBUG_PREFERENCE_CHANGES) {
      PREFERENCES.addPreferenceChangeListener(event -> LOG.debug(event.getKey() + "->\"" + event.getNewValue() + "\""));
    }
  }

  static final class Prefs {
    private Prefs() {}
    static String LIBS = "config.libs";
    static String REPOS = "config.repos";
    static String KEEP_ORIGINAL = "config.keep.original";
    static String GAME_TITLE = "game.title.";
    static String GAME_SIZE = "game.title.";
  }

  public static void main(final @Nullable String[] args) {
    launch(args);
  }

  @NotNull private Stage stage;
  @NotNull private Scene scene;

  @Override
  public void start(@NotNull Stage stage) throws Exception {
    this.stage = stage;
    URL location = Main.class.getResource("/layout/main.fxml");
    FXMLLoader loader = new FXMLLoader();
    loader.setLocation(location);
    loader.setResources(Bundle.BUNDLE);
    Parent root;
    try {
      root = loader.load();
    } catch (Exception e) {
      LOG.fatal(e.getMessage(), e);
      Utils.newExceptionAlert("alert.exception.while.starting", e).showAndWait();
      throw e;
    }

    this.scene = new Scene(root);
    doStyleSheets(scene);

    stage.setTitle(Bundle.get("app.name"));
    doIcons(stage);
    stage.setScene(scene);
    stage.show();

    MainController controller = loader.getController();
    controller.bindProperties(PREFERENCES);
  }

  @NotNull
  private static void doIcons(@NotNull Stage stage) {
    final ObservableList<Image> icons = stage.getIcons();
    icons.add(new Image("mipmap/icon_16x16.png"));
    icons.add(new Image("mipmap/icon_32x32.png"));
  }

  private static void doStyleSheets(@NotNull Scene scene) {
    final ObservableList<String> styleSheets = scene.getStylesheets();
  }
}
