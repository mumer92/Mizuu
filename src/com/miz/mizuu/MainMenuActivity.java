package com.miz.mizuu;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import com.miz.base.MizActivity;
import com.miz.db.DbAdapter;
import com.miz.db.DbAdapterTvShow;

import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.miz.functions.AsyncTask;
import com.miz.functions.MenuItem;
import com.miz.functions.MizLib;

@SuppressLint("NewApi")
public class MainMenuActivity extends MizActivity {

	public static final int MOVIES = 0, SHOWS = 1, WATCHLIST = 2, WEB_MOVIES = 3, WEB_VIDEOS = 4;
	private int mNumMovies, mNumShows, mNumWatchlist, selectedIndex;
	private Typeface tf, tfLight;
	private DrawerLayout mDrawerLayout;
	protected ListView mDrawerList;
	private TextView tab1, tab2;
	private ActionBarDrawerToggle mDrawerToggle;
	private DbAdapter dbHelper;
	private DbAdapterTvShow dbHelperTv;
	private boolean confirmExit, hasTriedOnce = false;
	private String startup;
	private ArrayList<MenuItem> menu = new ArrayList<MenuItem>(), thirdPartyApps = new ArrayList<MenuItem>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.menu_drawer);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		confirmExit = settings.getBoolean("prefsConfirmBackPress", false);
		startup = settings.getString("prefsStartup", "1");

		dbHelper = MizuuApplication.getMovieAdapter();
		dbHelperTv = MizuuApplication.getTvDbAdapter();

		tf = Typeface.createFromAsset(getAssets(), "Roboto-Thin.ttf");
		tfLight = Typeface.createFromAsset(getAssets(), "Roboto-Light.ttf");

		setupMenuItems();

		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mDrawerLayout.setDrawerShadow(R.drawable.drawer_list_shadow, GravityCompat.START);

		if (!MizLib.runsOnTablet(this) && !MizLib.runsInPortraitMode(this)) {
			findViewById(R.id.personalizedArea).setVisibility(View.GONE);
		} else
			setupUserDetails();

		((TextView) findViewById(R.id.username)).setTextSize(26f);
		((TextView) findViewById(R.id.username)).setTypeface(tf);

		tab1 = (TextView) findViewById(R.id.tab1);
		tab2 = (TextView) findViewById(R.id.tab2);

		mDrawerList = (ListView) findViewById(R.id.listView1);
		mDrawerList.setAdapter(new MenuAdapter());
		mDrawerList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				if (tab1.isSelected()) {
					Intent i = new Intent();
					i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK);
					i.setClass(getApplicationContext(), menu.get(arg2).getClassName());
					startActivity(i);
					overridePendingTransition(R.anim.mainfadein, R.anim.splashfadeout);
				} else {
					final PackageManager pm = getPackageManager();
					Intent i = pm.getLaunchIntentForPackage(thirdPartyApps.get(arg2).getPackageName());
					if (i != null) {
						i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(i);
					}
					mDrawerList.setItemChecked(arg2, false);
				}
			}
		});
		
		if (savedInstanceState != null && savedInstanceState.containsKey("tabIndex")) {
			selectedIndex = savedInstanceState.getInt("selectedIndex");
			changeTabSelection(savedInstanceState.getInt("tabIndex"));
		} else {
			tab1.setSelected(true);
		}

		getActionBar().setDisplayHomeAsUpEnabled(true);
		if (MizLib.hasICS())
			getActionBar().setHomeButtonEnabled(true);

		mDrawerToggle = new ActionBarDrawerToggle(
				this,                  /* host Activity */
				mDrawerLayout,         /* DrawerLayout object */
				R.drawable.ic_drawer,  /* nav drawer image to replace 'Up' caret */
				R.string.drawer_open,  /* "open drawer" description for accessibility */
				R.string.drawer_close  /* "close drawer" description for accessibility */
				) {
			public void onDrawerClosed(View view) {}

			public void onDrawerOpened(View drawerView) {}
		};
		mDrawerLayout.setDrawerListener(mDrawerToggle);

		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter("mizuu-movies-update"));
		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter("mizuu-library-change"));
		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter("mizuu-shows-update"));
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putInt("tabIndex", tab1.isSelected() ? 0 : 1);
		outState.putInt("selectedIndex", selectedIndex);
	}

	private AsyncTask<Void, Void, Void> asyncLoader;

	private void setupUserDetails() {

		if (asyncLoader != null)
			asyncLoader.cancel(true);
		asyncLoader = new AsyncTask<Void, Void, Void>() {
			private Bitmap cover = null, profile = null;
			private String full_name;

			@Override
			protected Void doInBackground(Void... params) {
				String filepath = MizLib.getLatestBackdropPath(getApplicationContext());

				int width = MizLib.convertDpToPixels(getApplicationContext(), 320),
						height = MizLib.convertDpToPixels(getApplicationContext(), 170);

				if (!isCancelled()) {
					if (!MizLib.isEmpty(filepath)) {
						cover = MizLib.decodeSampledBitmapFromFile(filepath, width, height);
					} else {
						cover = MizLib.decodeSampledBitmapFromResource(getResources(), R.drawable.cover_image, width, height);
					}
				}

				full_name = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("traktFullName", "");

				if (!isCancelled()) {
					if (!MizLib.isEmpty(full_name)) {
						int size = MizLib.convertDpToPixels(getApplicationContext(), 50);
						if (new File(MizLib.getCacheFolder(getApplicationContext()), "avatar.jpg").exists())
							profile = MizLib.getRoundedCornerBitmap(
									MizLib.decodeSampledBitmapFromFile(new File(MizLib.getCacheFolder(getApplicationContext()), "avatar.jpg").getAbsolutePath(), size, size),
									size);
					}
				}

				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				if (!isCancelled()) {
					if (cover != null)
						((ImageView) findViewById(R.id.userCover)).setImageBitmap(cover);

					if (profile != null) {
						((ImageView) findViewById(R.id.userPhoto)).setImageBitmap(profile);
						((TextView) findViewById(R.id.username)).setText(full_name);
					}
				}

				cover = null;
				profile = null;
			}
		}.execute();
	}

	private void changeTabSelection(int index) {
		if (index == 0) {
			tab1.setSelected(true);
			tab2.setSelected(false);
			((BaseAdapter) mDrawerList.getAdapter()).notifyDataSetChanged();
			mDrawerList.setItemChecked(selectedIndex, true);
		} else {
			tab1.setSelected(false);
			tab2.setSelected(true);
			((BaseAdapter) mDrawerList.getAdapter()).notifyDataSetChanged();
			selectedIndex = mDrawerList.getCheckedItemPosition();
			mDrawerList.setItemChecked(mDrawerList.getCheckedItemPosition(), false);
		}
	}

	public void myLibraries(View v) {
		changeTabSelection(0);
	}

	public void mediaApps(View v) {
		changeTabSelection(1);
	}

	private void setupThirdPartyApps() {
		thirdPartyApps.clear();

		final PackageManager pm = getPackageManager();
		List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

		for (ApplicationInfo ai : packages) {
			if (MizLib.isMediaApp(ai)) {
				thirdPartyApps.add(new MenuItem(pm.getApplicationLabel(ai).toString(), 0, false, ai.packageName));
			}
		}

		Collections.sort(thirdPartyApps, new Comparator<MenuItem>() {
			@Override
			public int compare(MenuItem o1, MenuItem o2) {
				return o1.getTitle().compareToIgnoreCase(o2.getTitle());
			}
		});
	}

	private void setupMenuItems() {
		menu.clear();

		menu.add(new MenuItem(getString(R.string.drawerMyMovies), mNumMovies, false, MainMovies.class));
		menu.add(new MenuItem(getString(R.string.drawerMyTvShows), mNumShows, false, MainTvShows.class));
		menu.add(new MenuItem(getString(R.string.chooserWatchList), mNumWatchlist, false, MainWatchlist.class));
		menu.add(new MenuItem(getString(R.string.drawerOnlineMovies), 0, false, MovieDiscovery.class));
		menu.add(new MenuItem(getString(R.string.drawerWebVideos), 0, false, MainWeb.class));

		setupThirdPartyApps();
	}

	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateLibraryCounts();
		}
	};

	protected void selectListIndex(int index) {
		if (!menu.get(index).isThirdPartyApp()) {
			selectedIndex = index;
			mDrawerList.setItemChecked(index, true);
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		updateLibraryCounts();
	}

	private void updateLibraryCounts() {
		new Thread() {
			@Override
			public void run() {
				try {					
					mNumMovies = dbHelper.count();
					mNumWatchlist = dbHelper.countWatchlist();
					mNumShows = dbHelperTv.count();

					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							setupMenuItems();
							((BaseAdapter) mDrawerList.getAdapter()).notifyDataSetChanged();
						}
					});
				} catch (Exception e) {} // Problemer med at kontakte databasen
			}
		}.start();
	}

	@Override
	public void onDestroy() {
		// Unregister since the activity is about to be closed.
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);

		super.onDestroy();
	}

	@Override
	public boolean onOptionsItemSelected(android.view.MenuItem item) {
		if (mDrawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		switch(item.getItemId()) {
		case android.R.id.home:
			if (!mDrawerLayout.isDrawerOpen(mDrawerList)) {
				mDrawerLayout.openDrawer(mDrawerList);
			} else {
				mDrawerLayout.closeDrawer(mDrawerList);
			}
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		mDrawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		mDrawerToggle.onConfigurationChanged(newConfig);
	}

	public class MenuAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return tab1.isSelected() ? menu.size() : thirdPartyApps.size();
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public int getViewTypeCount() {
			return 1;
		}

		@Override
		public int getItemViewType(int position) {
			return 0;
		}

		@Override
		public boolean isEnabled(int position) {
			return true;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			convertView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.menu_drawer_item, null);
			TextView title = (TextView) convertView.findViewById(R.id.title);
			if (MizLib.runsOnTablet(getApplicationContext()))
				title.setTextSize(22f);
			TextView description = (TextView) convertView.findViewById(R.id.count);

			description.setTypeface(tfLight);
			description.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

			title.setTypeface(tfLight);
			title.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

			if (getMenuItem(position).isThirdPartyApp()) {
				title.setText(getMenuItem(position).getTitle());
			} else {
				title.setText(getMenuItem(position).getTitle());
			}
			if (getMenuItem(position).getCount() > 0)
				description.setText(String.valueOf(getMenuItem(position).getCount()));
			else
				description.setVisibility(View.GONE);

			return convertView;
		}

		private MenuItem getMenuItem(int position) {
			if (tab1.isSelected())
				return menu.get(position);
			return thirdPartyApps.get(position);
		}
	}

	@Override
	public void onBackPressed() {
		if (startup.equals("0") && !mDrawerLayout.isDrawerOpen(findViewById(R.id.left_drawer)) && MizLib.runsOnTablet(this)) { // Welcome screen
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
			i.setClass(getApplicationContext(), Welcome.class);
			startActivity(i);
			finish();
		}

		if (!mDrawerLayout.isDrawerOpen(findViewById(R.id.left_drawer)) && confirmExit) {
			if (hasTriedOnce) {
				super.onBackPressed();
			} else {
				Toast.makeText(this, getString(R.string.pressBackToExit), Toast.LENGTH_SHORT).show();
				hasTriedOnce = true;
			}
		} else {
			super.onBackPressed();
		}
	}
}