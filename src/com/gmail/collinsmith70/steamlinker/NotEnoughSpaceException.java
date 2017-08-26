package com.gmail.collinsmith70.steamlinker;

import java.io.IOException;

public class NotEnoughSpaceException extends IOException {

  public NotEnoughSpaceException() {
    super();
  }

  public NotEnoughSpaceException(String message) {
    super(message);
  }

  public NotEnoughSpaceException(String message, Throwable cause) {
    super(message, cause);
  }

  public NotEnoughSpaceException(Throwable cause) {
    super(cause);
  }

}
