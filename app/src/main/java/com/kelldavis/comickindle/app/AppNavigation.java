package com.kelldavis.comickindle.app;

import android.support.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class AppNavigation {

  public static final int ISSUES = 0;
  public static final int VOLUMES = 1;
  public static final int CHARACTERS = 2;
  public static final int COLLECTION = 3;
  public static final int WATCHLIST = 4;
  public static final int SETTINGS = 5;

  @Retention(RetentionPolicy.CLASS)
  @IntDef({ISSUES, VOLUMES, CHARACTERS, COLLECTION, WATCHLIST, SETTINGS})
  public @interface Section {

  }
}