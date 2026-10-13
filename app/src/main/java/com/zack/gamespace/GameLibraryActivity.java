package com.zack.gamespace;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Perpustakaan game: daftar aplikasi yang bisa diluncurkan, dengan favorit dan pencarian.
 */
public class GameLibraryActivity extends AppCompatActivity {

    private static final class AppEntry {
        final String label;
        final String packageName;
        final Drawable icon;

        AppEntry(String label, String packageName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    private TapSettings settings;
    private EditText searchInput;
    private TextView countText;
    private TextView emptyText;
    private ListView listView;
    private SwitchCompat autoOverlaySwitch;

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> shown = new ArrayList<>();
    private Set<String> favorites;
    private AppAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_library);
        settings = new TapSettings(this);

        searchInput = findViewById(R.id.et_search);
        countText = findViewById(R.id.tv_count);
        emptyText = findViewById(R.id.tv_empty);
        listView = findViewById(R.id.list_apps);
        autoOverlaySwitch = findViewById(R.id.switch_auto_overlay);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        adapter = new AppAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> launch(shown.get(position)));

        autoOverlaySwitch.setChecked(settings.isAutoOverlay());
        autoOverlaySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> settings.setAutoOverlay(isChecked));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter();
            }
        });

        loadApps();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        super.onDestroy();
    }

    private void loadApps() {
        final PackageManager pm = getPackageManager();
        final String ownPackage = getPackageName();
        executor.execute(() -> {
            Intent query = new Intent(Intent.ACTION_MAIN);
            query.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolved = pm.queryIntentActivities(query, 0);
            final List<AppEntry> loaded = new ArrayList<>();
            for (ResolveInfo info : resolved) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(ownPackage)) {
                    continue;
                }
                loaded.add(new AppEntry(String.valueOf(info.loadLabel(pm)), pkg, info.loadIcon(pm)));
            }
            runOnUiThread(() -> {
                if (destroyed) {
                    return;
                }
                allApps.clear();
                allApps.addAll(loaded);
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        favorites = settings.getFavorites();
        String query = searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);

        shown.clear();
        for (AppEntry entry : allApps) {
            if (query.isEmpty()
                    || entry.label.toLowerCase(Locale.ROOT).contains(query)
                    || entry.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                shown.add(entry);
            }
        }
        Collections.sort(shown, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                boolean fa = favorites.contains(a.packageName);
                boolean fb = favorites.contains(b.packageName);
                if (fa != fb) {
                    return fa ? -1 : 1;
                }
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        adapter.notifyDataSetChanged();
        countText.setText(getString(R.string.library_count, shown.size()));
        emptyText.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void launch(AppEntry entry) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(entry.packageName);
        if (intent == null) {
            Toast.makeText(this, R.string.toast_launch_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (settings.isAutoOverlay()) {
            AutoTapService service = AutoTapService.getInstance();
            if (service != null) {
                service.showOverlay();
            }
        }
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.toast_launch_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private final class AppAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(GameLibraryActivity.this)
                        .inflate(R.layout.item_game, parent, false);
            }
            final AppEntry entry = shown.get(position);
            ImageView icon = row.findViewById(R.id.iv_app_icon);
            TextView label = row.findViewById(R.id.tv_app_label);
            TextView pkg = row.findViewById(R.id.tv_app_package);
            TextView star = row.findViewById(R.id.btn_star);

            icon.setImageDrawable(entry.icon);
            label.setText(entry.label);
            pkg.setText(entry.packageName);
            boolean favorite = favorites != null && favorites.contains(entry.packageName);
            star.setText(favorite ? R.string.star_on : R.string.star_off);
            star.setTextColor(getColor(favorite ? R.color.neon_cyan : R.color.text_secondary));
            star.setOnClickListener(v -> {
                settings.toggleFavorite(entry.packageName);
                applyFilter();
            });
            return row;
        }
    }
}
