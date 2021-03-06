package com.kelldavis.comickindle.factory;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Binder;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.kelldavis.comickindle.R;
import com.kelldavis.comickindle.app.ComicKindleApp;
import com.kelldavis.comickindle.contract.ComicContract;
import com.kelldavis.comickindle.ui.MainActivity;
import com.kelldavis.comickindle.utils.IssueTextUtils;

import javax.inject.Inject;

import timber.log.Timber;

@SuppressWarnings("WeakerAccess")
public class ComicWidgetFactory implements RemoteViewsService.RemoteViewsFactory {

  // Query projection
  private static final String[] PROJECTION = {
      ComicContract.IssueEntry.COLUMN_ISSUE_VOLUME_NAME,
      ComicContract.IssueEntry.COLUMN_ISSUE_NUMBER
  };

  // Columns indexes
  private static final int INDEX_VOLUME_NAME = 0;
  private static final int INDEX_ISSUE_NUMBER = 1;

  @Inject
  ContentResolver contentResolver;

  private final Context context;
  private Cursor cursor;

  public ComicWidgetFactory(Context context) {

    this.context = context;
    this.cursor = null;

    ComicKindleApp
        .getAppComponent()
        .plusWidgetComponent()
        .inject(this);
  }

  @Override
  public void onCreate() {

  }

  @Override
  public void onDataSetChanged() {

    if (cursor != null) {
      cursor.close();
    }

    final long identityToken = Binder.clearCallingIdentity();

    cursor = contentResolver.query(
        ComicContract.IssueEntry.CONTENT_URI_TODAY_ISSUES,
        PROJECTION,
        null,
        null,
        null
    );

    Binder.restoreCallingIdentity(identityToken);

    Timber.d("Cursor size: " + cursor.getCount());
  }

  @Override
  public void onDestroy() {
    if (cursor != null) {
      cursor.close();
      cursor = null;
    }
  }

  @Override
  public int getCount() {
    return cursor != null ? cursor.getCount() : 0;
  }

  @Override
  public RemoteViews getViewAt(int position) {

    if (cursor == null || !cursor.moveToPosition(position)) {
      return null;
    }

    RemoteViews views = new RemoteViews(context.getPackageName(),
        R.layout.today_issues_widget_list_item);

    String volume = cursor.getString(INDEX_VOLUME_NAME);
    int issueNumber = cursor.getInt(INDEX_ISSUE_NUMBER);

    String issueName = IssueTextUtils.getFormattedIssueTitle(volume, issueNumber);

    views.setTextViewText(R.id.widget_issue_name, issueName);

    Intent intent = new Intent(context, MainActivity.class);
    views.setOnClickFillInIntent(R.id.widget_list_item, intent);

    return views;
  }

  @Override
  public RemoteViews getLoadingView() {
    return new RemoteViews(context.getPackageName(), R.layout.today_issues_widget_list_item);
  }

  @Override
  public int getViewTypeCount() {
    return 1;
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public boolean hasStableIds() {
    return true;
  }
}
