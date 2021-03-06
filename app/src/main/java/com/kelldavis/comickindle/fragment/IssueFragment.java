package com.kelldavis.comickindle.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.BindBool;
import butterknife.BindInt;
import butterknife.BindString;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.evernote.android.state.State;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.SimpleShowcaseEventListener;
import com.hannesdorfmann.fragmentargs.annotation.FragmentWithArgs;
import com.hannesdorfmann.mosby3.mvp.viewstate.lce.LceViewState;
import com.hannesdorfmann.mosby3.mvp.viewstate.lce.data.RetainingLceViewState;
import com.kelldavis.comickindle.adapter.ComicSyncAdapter;
import com.kelldavis.comickindle.adapter.IssueAdapter;
import com.kelldavis.comickindle.app.ComicKindleApp;
import com.kelldavis.comickindle.R;
import com.kelldavis.comickindle.component.IssueComponent;
import com.kelldavis.comickindle.model.IssueInfoList;
import com.kelldavis.comickindle.modules.ComicLocalDataModule;
import com.kelldavis.comickindle.modules.ComicRemoteDataModule;
import com.kelldavis.comickindle.presenter.IssuePresenter;
import com.kelldavis.comickindle.ui.IssueDetailsActivity;
import com.kelldavis.comickindle.ui.MainActivity;
import com.kelldavis.comickindle.utils.DateTextUtils;
import com.kelldavis.comickindle.utils.FragmentUtils;
import com.kelldavis.comickindle.utils.ToolbarActionItemTarget;
import com.kelldavis.comickindle.utils.ViewUtils;
import com.kelldavis.comickindle.view.IssueView;
import com.miguelcatalan.materialsearchview.MaterialSearchView;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import timber.log.Timber;

@SuppressWarnings("WeakerAccess")
@FragmentWithArgs
public class IssueFragment extends
        BaseLceFragment<SwipeRefreshLayout, List<IssueInfoList>, IssueView, IssuePresenter>
        implements IssueView, SwipeRefreshLayout.OnRefreshListener {

    @BindString(R.string.error_data_not_available)
    String errorNoInternetText;
    @BindString(R.string.msg_no_issues_today)
    String emptyViewText;
    @BindString(R.string.issues_title_format)
    String titleFormatString;
    @BindInt(R.integer.grid_columns_count)
    int gridColumnsCount;
    @BindBool(R.bool.is_tablet_layout)
    boolean twoPaneMode;

    @BindView(R.id.emptyView)
    TextView emptyView;
    @BindView(R.id.refreshLayout)
    SwipeRefreshLayout refreshLayout;
    @BindView(R.id.contentView)
    RecyclerView contentView;

    private boolean pendingStartupAnimation;

    @State
    String title;

    @State
    String chosenDate;

    @State
    String searchQuery;

    private IssueComponent issueComponent;
    private IssueAdapter adapter;
    private UpdatingBroadcastReceiver updatingBroadcastReceiver;
    private IntentFilter updatingIntentFilter;

    private Menu currentMenu;

    // --- FRAGMENTS LIFECYCLE ---

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setRetainInstance(true);

        if (savedInstanceState == null) {
            pendingStartupAnimation = true;
        }

        refreshLayout.setOnRefreshListener(this);

        updatingBroadcastReceiver = new UpdatingBroadcastReceiver();
        updatingIntentFilter = new IntentFilter();
        updatingIntentFilter.addAction(ComicSyncAdapter.ACTION_DATA_UPDATED);

        adapter = new IssueAdapter(issueId -> {
            if (twoPaneMode) {
                FragmentManager manager = getActivity().getSupportFragmentManager();
                Fragment fragment = new IssueDetailFragmentBuilder(issueId).build();

                FragmentUtils.replaceFragmentIn(
                        manager, fragment, R.id.content_frame, "IssueDetailFragment", true);
            } else {
                startActivity(IssueDetailsActivity.prepareIntent(getContext(), issueId));
            }
        });
        adapter.setHasStableIds(true);

        StaggeredGridLayoutManager manager =
                new StaggeredGridLayoutManager(gridColumnsCount, StaggeredGridLayoutManager.VERTICAL);

        contentView.setLayoutManager(manager);
        contentView.setHasFixedSize(true);
        contentView.setAdapter(adapter);

        setHasOptionsMenu(true);

        if (isNotNullOrEmpty(searchQuery)) {
            loadDataFiltered(searchQuery);
        } else if (isNotNullOrEmpty(chosenDate)) {
            loadDataForChosenDate(chosenDate);
        } else if (savedInstanceState != null) {
            loadData(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(updatingBroadcastReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(updatingBroadcastReceiver, updatingIntentFilter);
    }

    // --- OPTIONS MENU ---

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_issues_list, menu);

        currentMenu = menu;

        ViewUtils.tintMenuIcon(getContext(), menu, R.id.action_search, R.color.material_color_white);
        ViewUtils.tintMenuIcon(getContext(), menu, R.id.action_choose_date, R.color.material_color_white);

        setUpSearchItem(menu);

        if (isNotNullOrEmpty(searchQuery)) {
            showClearQueryMenuItem(true);
        } else {
            showClearQueryMenuItem(false);
        }

        if (pendingStartupAnimation) {
            hideToolbar();
            pendingStartupAnimation = false;
            startToolbarAnimation();
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_choose_date:
                choseDateAndLoadData();
                break;
            case R.id.action_clear_search_query:
                showClearQueryMenuItem(false);
                searchQuery = "";
                if (isNotNullOrEmpty(chosenDate)) {
                    loadDataForChosenDate(chosenDate);
                } else {
                    loadData(false);
                }
                break;
        }
        return true;
    }

    // --- BASE LCE FRAGMENT ---

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_issues;
    }

    @Override
    protected String getErrorMessage(Throwable e, boolean pullToRefresh) {
        String actualMessage = e.getMessage();
        if (actualMessage.contains("Unable to resolve host") ||
                actualMessage.contains("timeout")) {
            return errorNoInternetText;
        }
        return e.getMessage();
    }

    @NonNull
    @Override
    public IssuePresenter createPresenter() {
        return issueComponent.presenter();
    }

    @Override
    protected void onToolbarAnimationEnd() {
        showcaseToolbarItems();
    }

    @Override
    protected void injectDependencies() {
        issueComponent = ComicKindleApp.getAppComponent()
                .plusRemoteComponent(new ComicRemoteDataModule())
                .plusLocalComponent(new ComicLocalDataModule())
                .plusIssuesComponent();
        issueComponent.inject(this);
    }

    // --- MVP VIEW STATE ---

    @Override
    public List<IssueInfoList> getData() {
        return adapter == null ? null : adapter.getIssueInfoList();
    }

    @NonNull
    @Override
    public LceViewState<List<IssueInfoList>, IssueView> createViewState() {
        return new RetainingLceViewState<>();
    }

    // --- MVP VIEW ---

    @Override
    public void setTitle(String date) {
        title = String.format(Locale.US, titleFormatString, date);
        updateTitle();
    }

    @Override
    public void showEmptyView(boolean show) {
        refreshLayout.setRefreshing(false);
        if (show) {
            emptyView.setText(emptyViewText);
            emptyView.setVisibility(View.VISIBLE);
            contentView.setVisibility(View.GONE);
            errorView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    @Override
    public void showErrorToast(String message) {
        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showContent() {
        super.showContent();
        refreshLayout.setRefreshing(false);
    }

    @Override
    public void showError(Throwable e, boolean pullToRefresh) {
        super.showError(e, pullToRefresh);
        refreshLayout.setRefreshing(false);
        loadingView.setVisibility(View.GONE);
    }

    @Override
    public void showLoading(boolean pullToRefresh) {
        super.showLoading(pullToRefresh);
        refreshLayout.setRefreshing(pullToRefresh);

        if (!pullToRefresh) {
            loadingView.setVisibility(View.GONE);
        }
    }

    @Override
    public void choseDateAndLoadData() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog dpd = DatePickerDialog.newInstance(
                (view, year, monthOfYear, dayOfMonth) -> {
                    chosenDate = String.format(Locale.US, "%d-%02d-%02d", year, monthOfYear + 1, dayOfMonth);
                    loadDataForChosenDate(chosenDate);
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH));

        dpd.setAccentColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
        dpd.show(getActivity().getFragmentManager(), "DatePickerDialog");
    }

    @Override
    public void loadDataForChosenDate(String date) {
        presenter.loadIssuesByDate(date);
    }

    @Override
    public void loadDataFiltered(String filter) {
        String date = (isNotNullOrEmpty(chosenDate)) ? chosenDate : DateTextUtils.getTodayDateString();
        presenter.loadIssuesByDateAndName(date, filter);
    }

    @Override
    public void setData(List<IssueInfoList> data) {
        adapter.setIssueInfoList(data);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void loadData(boolean pullToRefresh) {
        chosenDate = "";
        presenter.loadTodayIssues(pullToRefresh);
    }

    // --- SWIPE TO REFRESH LAYOUT ---

    @Override
    public void onRefresh() {
        loadData(true);
    }

    // --- MISC UTILITY FUNCTIONS ---

    private void setUpSearchItem(Menu menu) {
        // Find items
        MaterialSearchView searchView = ButterKnife.findById(getActivity(), R.id.search_view);
        MenuItem menuItem = menu.findItem(R.id.action_search);

        // Tweaks
        searchView.setVoiceSearch(false);
        searchView.setMenuItem(menuItem);

        // Listeners
        searchView.setOnQueryTextListener(new MaterialSearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchQuery = query;

                if (searchQuery.length() > 0) {
                    showClearQueryMenuItem(true);
                    loadDataFiltered(searchQuery);
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //Do some magic
                return false;
            }
        });
    }

    private void updateTitle() {
        ActionBar supportActionBar = ((MainActivity) getActivity()).getSupportActionBar();

        if (supportActionBar != null) {
            supportActionBar.setTitle(title);
        }
    }

    void showcaseToolbarItems() {

        if (presenter.shouldNotDisplayShowcases()) {
            return;
        }

        Toolbar toolbar = ButterKnife.findById(getActivity(), R.id.toolbar);

        if (toolbar != null) {

            // Show first showcase
            new ShowcaseView.Builder(getActivity())
                    .setTarget(new ToolbarActionItemTarget(toolbar, R.id.action_choose_date))
                    .withMaterialShowcase()
                    .hideOnTouchOutside()
                    .setStyle(R.style.ShowCaseDarkGreenPopup)
                    .setContentTitle(R.string.showcase_issues_title)
                    .setContentText(R.string.showcase_issues_datepicker)
                    .setShowcaseEventListener(new SimpleShowcaseEventListener() {
                        @Override
                        public void onShowcaseViewHide(ShowcaseView showcaseView) {
                            super.onShowcaseViewHide(showcaseView);
                            // Show second showcase
                            new ShowcaseView.Builder(getActivity())
                                    .setTarget(new ToolbarActionItemTarget(toolbar, R.id.action_search))
                                    .withMaterialShowcase()
                                    .hideOnTouchOutside()
                                    .setStyle(R.style.ShowCaseDarkOrangePopup)
                                    .setContentTitle(R.string.showcase_issues_title)
                                    .setContentText(R.string.showcase_issues_search)
                                    .build()
                                    .show();
                        }
                    })
                    .build()
                    .show();

            presenter.showcaseWasDisplayed();
        }
    }

    private boolean isNotNullOrEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    void showClearQueryMenuItem(boolean show) {
        currentMenu.findItem(R.id.action_choose_date).setVisible(!show);
        currentMenu.findItem(R.id.action_search).setVisible(!show);
        currentMenu.findItem(R.id.action_clear_search_query).setVisible(show);
    }

    private class UpdatingBroadcastReceiver extends BroadcastReceiver {

        UpdatingBroadcastReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (ComicSyncAdapter.ACTION_DATA_UPDATED.equals(action)) {
                Timber.d("Action received: " + action);
                presenter.loadTodayIssuesFromDB();
            }
        }
    }
}