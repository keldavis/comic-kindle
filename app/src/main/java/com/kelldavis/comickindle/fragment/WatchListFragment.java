package com.kelldavis.comickindle.fragment;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.View;
import android.widget.TextView;
import butterknife.BindBool;
import butterknife.BindInt;
import butterknife.BindString;
import butterknife.BindView;
import com.hannesdorfmann.fragmentargs.annotation.FragmentWithArgs;

import com.kelldavis.comickindle.R;
import com.kelldavis.comickindle.adapter.WatchListAdapter;
import com.kelldavis.comickindle.contract.ComicContract;
import com.kelldavis.comickindle.ui.MainActivity;
import com.kelldavis.comickindle.ui.VolumeDetailActivity;
import com.kelldavis.comickindle.utils.FragmentUtils;


@FragmentWithArgs
public class WatchListFragment extends BaseFragment
    implements LoaderManager.LoaderCallbacks<Cursor> {

  private static final int TRACKER_LOADER_ID = 1234;

  @BindView(R.id.contentView)
  RecyclerView contentView;
  @BindView(R.id.emptyView)
  TextView emptyView;

  @BindString(R.string.msg_no_volumes_tracked)
  String emptyViewText;
  @BindInt(R.integer.grid_columns_count)
  int gridColumnsCount;
  @BindBool(R.bool.is_tablet_layout)
  boolean twoPaneMode;

  private WatchListAdapter adapter;

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    adapter = new WatchListAdapter(volumeId -> {
      if (twoPaneMode) {
        FragmentManager manager = getActivity().getSupportFragmentManager();
        Fragment fragment = new VolumeDetailFragmentBuilder(volumeId).build();

        FragmentUtils.replaceFragmentIn(
            manager, fragment, R.id.content_frame, "VolumeDetailFragment", true);
      } else {
        startActivity(VolumeDetailActivity.prepareIntent(getContext(), volumeId));
      }
    });

    emptyView.setText(emptyViewText);

    adapter.setHasStableIds(true);

    StaggeredGridLayoutManager manager =
        new StaggeredGridLayoutManager(gridColumnsCount, StaggeredGridLayoutManager.VERTICAL);

    contentView.setLayoutManager(manager);
    contentView.setHasFixedSize(true);
    contentView.setAdapter(adapter);

    ActionBar supportActionBar = ((MainActivity) getActivity()).getSupportActionBar();

    if (supportActionBar != null) {
      supportActionBar.setTitle(R.string.volumes_tracker_fragment_title);
    }

    if (savedInstanceState == null) {
      hideToolbar();
      startToolbarAnimation();
    }

    getActivity().getSupportLoaderManager().initLoader(TRACKER_LOADER_ID, null, this);
  }

  @Override
  public void onResume() {
    super.onResume();
    getActivity().getSupportLoaderManager().restartLoader(TRACKER_LOADER_ID, null, this);
  }

  @Override
  protected int getLayoutRes() {
    return R.layout.fragment_volumes_tracker;
  }

  @SuppressLint("StaticFieldLeak")
  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new AsyncTaskLoader<Cursor>(getContext()) {

      Cursor data = null;

      @Override
      protected void onStartLoading() {

        if (data != null) {
          deliverResult(data);
        } else {
          forceLoad();
        }
      }

      @Override
      public Cursor loadInBackground() {
        return getActivity().getContentResolver()
            .query(ComicContract.TrackedVolumeEntry.CONTENT_URI_TRACKED_VOLUMES,
                null, null, null, null);
      }

      @Override
      public void deliverResult(Cursor cursor) {
        data = cursor;
        super.deliverResult(cursor);
      }
    };
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

    if (data.getCount() > 0) {
      emptyView.setVisibility(View.GONE);
    } else {
      emptyView.setVisibility(View.VISIBLE);
    }

    adapter.swapCursor(data);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    adapter.swapCursor(null);
  }
}
