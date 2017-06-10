package com.gmail.collinsmith70.steamlinker;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class ScrollBarMark {

  private final Rectangle rect;
  private final DoubleProperty position = new SimpleDoubleProperty();

  public ScrollBarMark() {
    rect = new Rectangle(5, 5, Color.RED.deriveColor(0, 1, 1, 0.5));
    rect.setManaged(false);
  }

  public void attach(ScrollBar scrollBar) {
    StackPane sp = (StackPane) scrollBar.lookup(".track");
    rect.widthProperty().bind(sp.widthProperty());
    sp.getChildren().add(rect);
    rect.layoutYProperty().bind(Bindings.createDoubleBinding(() -> {
          double height = sp.getLayoutBounds().getHeight();
          double visibleAmount = scrollBar.getVisibleAmount();
          double max = scrollBar.getMax();
          double min = scrollBar.getMin();
          double pos = position.get();
          double delta = max - min;

          height *= 1 - visibleAmount / delta;

          return height * (pos - min) / delta;
        },
        position,
        sp.layoutBoundsProperty(),
        scrollBar.visibleAmountProperty(),
        scrollBar.minProperty(),
        scrollBar.maxProperty()));
  }

  public final double getPosition() {
    return this.position.get();
  }

  public final void setPosition(double value) {
    this.position.set(value);
  }

  public final DoubleProperty positionProperty() {
    return this.position;
  }

  public void detach() {
    StackPane parent = (StackPane) rect.getParent();
    if (parent != null) {
      parent.getChildren().remove(rect);
      rect.layoutYProperty().unbind();
      rect.widthProperty().unbind();
    }
  }

}