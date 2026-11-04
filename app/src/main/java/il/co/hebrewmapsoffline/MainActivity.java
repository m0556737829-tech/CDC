package il.co.hebrewmapsoffline;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.android.rendertheme.AssetsRenderTheme;

public final class MainActivity extends Activity {
    private static final int PICK_MAP_DATA = 4102;
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    private MapView mapView;
    private MapFile mapStore;
    private LinearLayout searchResults;
    private EditText searchInput;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AndroidGraphicFactory.createInstance(getApplication());
        showHome();
    }

    private File mapFile() {
        return new File(getFilesDir(), "maps/israel.map");
    }

    private File dataFile() {
        return new File(getFilesDir(), "data/israel-data.db");
    }

    private void showHome() {
        if (mapFile().isFile()) {
            showOfflineMap(mapFile());
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(32, 32, 32, 32);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(0xFFF4F1E8);

        TextView title = new TextView(this);
        title.setText("מפות ישראל אופליין");
        title.setTextSize(28);
        title.setTextColor(0xFF17324D);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView description = new TextView(this);
        description.setText("מפת ישראל נשמרת במכשיר ופועלת ללא חיבור לאינטרנט.\n\nכדי להתחיל, יש לייבא את חבילת המפה המקומית (ZIP) שהוכנה עבור האפליקציה.");
        description.setTextSize(17);
        description.setTextColor(0xFF34495E);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 24, 0, 24);
        root.addView(description, new LinearLayout.LayoutParams(-1, -2));

        Button importButton = new Button(this);
        importButton.setText("ייבוא חבילת מפה מהמכשיר");
        importButton.setOnClickListener(v -> chooseMapFile());
        root.addView(importButton, new LinearLayout.LayoutParams(-1, -2));

        TextView note = new TextView(this);
        note.setText("החבילה כוללת מפה ונתוני חיפוש. ניווט מסלולים מלא עדיין בפיתוח.");
        note.setTextSize(13);
        note.setTextColor(0xFF657786);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, 22, 0, 0);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void chooseMapFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/octet-stream"});
        startActivityForResult(intent, PICK_MAP_DATA);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_MAP_DATA || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            importMapPackage(data.getData());
            Toast.makeText(this, "חבילת המפה נשמרה במכשיר", Toast.LENGTH_LONG).show();
            showHome();
        } catch (Exception e) {
            Toast.makeText(this, "ייבוא החבילה נכשל: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importMapPackage(Uri uri) throws Exception {
        File map = mapFile();
        File db = dataFile();
        if (!map.getParentFile().exists() && !map.getParentFile().mkdirs()) throw new Exception("לא ניתן ליצור תיקיית מפות");
        if (!db.getParentFile().exists() && !db.getParentFile().mkdirs()) throw new Exception("לא ניתן ליצור תיקיית נתונים");
        String name = displayName(uri).toLowerCase();
        if (!name.endsWith(".zip")) throw new Exception("יש לבחור קובץ ZIP של חבילת המפה");
        boolean gotMap = false;
        boolean gotDb = false;
        try (InputStream raw = getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(raw)) {
            if (raw == null) throw new Exception("הקובץ לא נפתח");
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName().toLowerCase();
                if (entry.isDirectory()) continue;
                if (entryName.endsWith("israel.map") || entryName.endsWith(".map")) {
                    copy(zip, map);
                    gotMap = true;
                } else if (entryName.endsWith("israel-data.db") || entryName.endsWith(".db")) {
                    copy(zip, db);
                    gotDb = true;
                }
            }
        }
        if (!gotMap) throw new Exception("לא נמצא קובץ מפה בחבילה");
        MapFile check = null;
        try {
            check = new MapFile(map);
            if (check.getMapFileInfo() == null) throw new Exception("קובץ המפה אינו תקין");
        } catch (Exception e) {
            map.delete();
            throw e;
        } finally {
            if (check != null) check.close();
        }
        if (!gotDb) db.delete();
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() == null ? "map-package.zip" : uri.getLastPathSegment();
    }

    private void copy(InputStream in, File out) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(out)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) stream.write(buffer, 0, count);
            stream.getFD().sync();
        }
    }

    private void showOfflineMap(File file) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F1E8);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(16);
        searchInput.setHint("חיפוש יישוב, רחוב או מקום");
        searchBar.addView(searchInput, new LinearLayout.LayoutParams(0, 52, 1f));

        Button searchButton = new Button(this);
        searchButton.setText("חיפוש");
        searchButton.setOnClickListener(v -> searchOffline());
        searchBar.addView(searchButton, new LinearLayout.LayoutParams(-2, 52));
        root.addView(searchBar, new LinearLayout.LayoutParams(-1, -2));

        ScrollView resultsScroll = new ScrollView(this);
        searchResults = new LinearLayout(this);
        searchResults.setOrientation(LinearLayout.VERTICAL);
        resultsScroll.addView(searchResults);
        root.addView(resultsScroll, new LinearLayout.LayoutParams(-1, 0));

        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.getMapScaleBar().setVisible(true);
        mapView.setBuiltInZoomControls(true);
        mapView.getModel().mapViewPosition.setZoomLevel((byte) 8);
        mapView.getModel().mapViewPosition.setCenter(new LatLong(31.7683, 35.2137));
        mapStore = new MapFile(file);
        MapDataStore store = mapStore;
        TileRendererLayer layer = new TileRendererLayer(
            AndroidUtil.createTileCache(this, "israel-map-cache",
                mapView.getModel().displayModel.getTileSize(), 1f,
                mapView.getModel().frameBufferModel.getOverdrawFactor()),
            store, mapView.getModel().mapViewPosition, AndroidGraphicFactory.INSTANCE);
        layer.setXmlRenderTheme(new AssetsRenderTheme(getAssets(), "", "israel-render-theme.xml"));
        mapView.getLayerManager().getLayers().add(layer);
        root.addView(mapView, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView attribution = new TextView(this);
        attribution.setText("© OpenStreetMap contributors");
        attribution.setTextSize(11);
        attribution.setTextColor(0xFF52616B);
        attribution.setGravity(Gravity.CENTER);
        root.addView(attribution, new LinearLayout.LayoutParams(-1, 24));
        setContentView(root);
    }

    private void searchOffline() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) return;
        if (!dataFile().isFile()) {
            Toast.makeText(this, "חבילת החיפוש לא נמצאה. יש לייבא ZIP עדכני.", Toast.LENGTH_LONG).show();
            return;
        }
        searchResults.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("מחפש במאגר המקומי…");
        searchResults.addView(loading);
        searchExecutor.execute(() -> {
            List<SearchHit> hits = new ArrayList<>();
            SQLiteDatabase db = null;
            try {
                db = SQLiteDatabase.openDatabase(dataFile().getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                String match = query.replace("\"", "").trim();
                if (!match.isEmpty()) {
                    try (Cursor c = db.rawQuery(
                        "SELECT p.name,p.lat,p.lon,p.kind FROM places_fts f JOIN places p ON p.id=f.docid WHERE places_fts MATCH ? LIMIT 30",
                        new String[]{match})) {
                        while (c.moveToNext()) hits.add(new SearchHit(c.getString(0), c.getDouble(1), c.getDouble(2), c.getString(3)));
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "שגיאה בחיפוש המקומי: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (db != null) db.close();
            }
            runOnUiThread(() -> showSearchResults(hits));
        });
    }

    private void showSearchResults(List<SearchHit> hits) {
        searchResults.removeAllViews();
        if (hits.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("לא נמצאו תוצאות. נסה מילה אחרת.");
            empty.setPadding(12, 12, 12, 12);
            searchResults.addView(empty);
            return;
        }
        for (SearchHit hit : hits) {
            Button item = new Button(this);
            item.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            item.setText(hit.name + "  ·  " + hit.kind);
            item.setOnClickListener(v -> {
                mapView.getModel().mapViewPosition.setCenter(new LatLong(hit.lat, hit.lon));
                mapView.getModel().mapViewPosition.setZoomLevel((byte) 16);
                searchResults.removeAllViews();
            });
            searchResults.addView(item, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private static final class SearchHit {
        final String name;
        final double lat, lon;
        final String kind;
        SearchHit(String name, double lat, double lon, String kind) {
            this.name = name; this.lat = lat; this.lon = lon; this.kind = kind;
        }
    }

    @Override protected void onDestroy() {
        if (mapView != null) mapView.destroyAll();
        if (mapStore != null) mapStore.close();
        searchExecutor.shutdownNow();
        super.onDestroy();
    }
}
