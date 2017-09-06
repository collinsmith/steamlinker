package com.gmail.collinsmith70.steamlinker;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javafx.application.Application;
import javafx.application.Platform;
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

  private static final boolean CLEAR_PREFERENCES = false;
  static final Preferences PREFERENCES = Preferences.userNodeForPackage(Main.class);
  static {
    if (DEBUG_PREFERENCE_CHANGES) {
      PREFERENCES.addPreferenceChangeListener(event -> {
        String newValue = event.getNewValue();
        if (newValue != null) {
          LOG.debug(event.getKey() + "->\"" + newValue + "\"");
        }
      });
    }
  }

  static final class Prefs {
    private Prefs() {}
    static String LIBS = "config.libs";
    static String REPOS = "config.repos";
    static String DELETE_REPO_COPY = "config.delete.repo.copy";
    static String VERIFY = "config.verify";
    static String GAME_TITLE = "game.title.";
    static String GAME_SIZE = "game.size.";
  }

  static final class CliOptions {
    private CliOptions() {}
    static String CLEAR_PREFS = "clearPrefs";
  }

  public static void main(final @Nullable String[] args) {
    try {
      Options options = createOptions();
      CommandLineParser cliParser = new DefaultParser();
      CommandLine cli = cliParser.parse(options, args);

      if (cli.hasOption(CliOptions.CLEAR_PREFS) || CLEAR_PREFERENCES) {
        LOG.info("Clearing preferences...");
        try {
          PREFERENCES.clear();
          LOG.debug("Preferences cleared.");
        } catch (BackingStoreException e) {
          LOG.error(e.getMessage(), e);
        }
      }
    } catch (ParseException e) {
      LOG.error(e.getMessage(), e);
    }

    launch(args);
  }

  @NotNull
  private static Options createOptions() {
    Options options = new Options();
    options.addOption(Option
        .builder(CliOptions.CLEAR_PREFS)
        .desc("Clears any saved preferences")
        .build());
    return options;
  }

  private static final Injector INJECTOR = Guice.createInjector(new AbstractModule() {
    @Override
    protected void configure() {
      if (SystemUtils.IS_OS_WINDOWS) {
        bind(LinkerService.class).to(WindowsLinkerService.class).asEagerSingleton();
      } else {
        throw new UnsupportedOperationException("Only Windows is supported.");
      }
    }
  });
  public static LinkerService service() {
    return INJECTOR.getInstance(LinkerService.class);
  }

  @NotNull private Stage stage;
  @NotNull private Scene scene;

  @Override
  public void start(@NotNull Stage stage) throws Exception {
    URL location = Main.class.getResource("/layout/main.fxml");
    FXMLLoader loader = new FXMLLoader();
    loader.setLocation(location);
    loader.setResources(Bundle.BUNDLE);
    Parent root;
    try {
      root = loader.load();
      if (!SystemUtils.IS_OS_WINDOWS) {
        throw new Exception("Unsupported OS: " + SystemUtils.OS_NAME);
      }
    } catch (Exception e) {
      LOG.fatal(e.getMessage(), e);
      Utils.newExceptionAlert("alert.exception.while.starting", e).showAndWait();
      Platform.exit();
      return;
    }

    this.scene = new Scene(root);
    setupStylesheets(scene);

    this.stage = stage;
    stage.setTitle(Bundle.get("app.name"));
    setupIcons(stage);
    stage.setScene(scene);
    stage.sizeToScene();
    stage.show();
    stage.setMinWidth(stage.getWidth());
    stage.setMinHeight(stage.getHeight());

    MainController controller = loader.getController();
    controller.configure(stage, scene);
    controller.bindPreferences(PREFERENCES);
  }

  private static void setupStylesheets(@NotNull Scene scene) {
    final ObservableList<String> stylesheets = scene.getStylesheets();
  }

  private static void setupIcons(@NotNull Stage stage) {
    final ObservableList<Image> icons = stage.getIcons();
    icons.add(new Image("mipmap/icon_16x16.png"));
    icons.add(new Image("mipmap/icon_32x32.png"));
  }
}
