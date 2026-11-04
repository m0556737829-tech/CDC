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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.android.rendertheme.AssetsRenderTheme;

public final class MainActivity extends Activity {
    private static final int PICK_MAP_DATA = 4102;
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    private MapView mapView;
    private MapFile mapStore;
    private Polyline routeLine;
    private LinearLayout searchResults;
    private EditText searchInput, startInput, endInput;
    private TextView routeStatus;
    private Button profileButton;
    private String routeProfile = "car";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AndroidGraphicFactory.createInstance(getApplication());
        showHome();
    }

    private File mapFile() { return new File(getFilesDir(), "maps/israel.map"); }
    private File dataFile() { return new File(getFilesDir(), "data/israel-data.db"); }

    private void showHome() {
        if (mapFile().isFile()) { showOfflineMap(mapFile()); return; }
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
        description.setText("מפת ישראל נשמרת במכשיר ופועלת ללא חיבור לאינטרנט.\n\nיש לייבא את חבילת המפה המקומית (ZIP) שהוכנה עבור האפליקציה.");
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
        note.setText("כולל תצוגת מפה וחיפוש מקומי. ניווט מסלולים עדיין בפיתוח.");
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
        File map = mapFile(), db = dataFile();
        if (!map.getParentFile().exists() && !map.getParentFile().mkdirs()) throw new Exception("לא ניתן ליצור תיקיית מפות");
        if (!db.getParentFile().exists() && !db.getParentFile().mkdirs()) throw new Exception("לא ניתן ליצור תיקיית נתונים");
        if (!displayName(uri).toLowerCase().endsWith(".zip")) throw new Exception("יש לבחור קובץ ZIP של חבילת המפה");
        boolean gotMap = false, gotDb = false;
        try (InputStream raw = getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new Exception("הקובץ לא נפתח");
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    String entryName = entry.getName().toLowerCase();
                    if (entryName.endsWith(".map")) { copy(zip, map); gotMap = true; }
                    else if (entryName.endsWith(".db")) { copy(zip, db); gotDb = true; }
                }
            }
        }
        if (!gotMap) throw new Exception("לא נמצא קובץ מפה בחבילה");
        MapFile check = null;
        try {
            check = new MapFile(map);
            if (check.getMapFileInfo() == null) throw new Exception("קובץ המפה אינו תקין");
        } catch (Exception e) { map.delete(); throw e; }
        finally { if (check != null) check.close(); }
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

        LinearLayout routeRow = new LinearLayout(this);
        routeRow.setOrientation(LinearLayout.HORIZONTAL);
        startInput = new EditText(this);
        startInput.setSingleLine(true);
        startInput.setTextSize(14);
        startInput.setHint("מוצא");
        routeRow.addView(startInput, new LinearLayout.LayoutParams(0, 48, 1f));
        endInput = new EditText(this);
        endInput.setSingleLine(true);
        endInput.setTextSize(14);
        endInput.setHint("יעד");
        routeRow.addView(endInput, new LinearLayout.LayoutParams(0, 48, 1f));
        Button routeButton = new Button(this);
        routeButton.setText("סע");
        routeButton.setOnClickListener(v -> buildOfflineRoute());
        routeRow.addView(routeButton, new LinearLayout.LayoutParams(-2, 48));
        root.addView(routeRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout profileRow = new LinearLayout(this);
        profileRow.setOrientation(LinearLayout.HORIZONTAL);
        profileButton = new Button(this);
        profileButton.setText("רכב");
        profileButton.setOnClickListener(v -> cycleProfile());
        profileRow.addView(profileButton, new LinearLayout.LayoutParams(-2, 42));
        routeStatus = new TextView(this);
        routeStatus.setText("חיפוש וניווט מקומי – ללא אינטרנט");
        routeStatus.setGravity(Gravity.CENTER_VERTICAL);
        routeStatus.setTextSize(12);
        profileRow.addView(routeStatus, new LinearLayout.LayoutParams(0, 42, 1f));
        root.addView(profileRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(15);
        searchInput.setHint("חיפוש יישוב, רחוב או מקום");
        searchBar.addView(searchInput, new LinearLayout.LayoutParams(0, 48, 1f));
        Button searchButton = new Button(this);
        searchButton.setText("חיפוש");
        searchButton.setOnClickListener(v -> searchOffline());
        searchBar.addView(searchButton, new LinearLayout.LayoutParams(-2, 48));
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
        root.addView(attribution, new LinearLayout.LayoutParams(-1, 22));
        setContentView(root);
    }

    private void cycleProfile() {
        if ("car".equals(routeProfile)) { routeProfile = "foot"; profileButton.setText("הליכה"); }
        else if ("foot".equals(routeProfile)) { routeProfile = "bike"; profileButton.setText("אופניים"); }
        else { routeProfile = "car"; profileButton.setText("רכב"); }
    }

    private void searchOffline() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) return;
        if (!dataFile().isFile()) { Toast.makeText(this, "חבילת החיפוש לא נמצאה. יש לייבא ZIP עדכני.", Toast.LENGTH_LONG).show(); return; }
        searchResults.removeAllViews();
        TextView loading = new TextView(this); loading.setText("מחפש במאגר המקומי…"); searchResults.addView(loading);
        searchExecutor.execute(() -> {
            List<SearchHit> hits = new ArrayList<>();
            SQLiteDatabase db = null;
            try {
                db = SQLiteDatabase.openDatabase(dataFile().getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                String match = ftsQuery(query);
                try (Cursor c = db.rawQuery("SELECT p.name,p.lat,p.lon,p.kind FROM places_fts f JOIN places p ON p.id=f.docid WHERE places_fts MATCH ? LIMIT 30", new String[]{match})) {
                    while (c.moveToNext()) hits.add(new SearchHit(c.getString(0), c.getDouble(1), c.getDouble(2), c.getString(3)));
                }
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "שגיאה בחיפוש: " + e.getMessage(), Toast.LENGTH_LONG).show()); }
            finally { if (db != null) db.close(); }
            runOnUiThread(() -> showSearchResults(hits));
        });
    }

    private String ftsQuery(String text) {
        String cleaned = text.replaceAll("[^\\p{L}\\p{N} ]", " ").trim();
        String[] parts = cleaned.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) if (!part.isEmpty()) { if (out.length() > 0) out.append(' '); out.append('"').append(part.replace("\"", "")).append('"').append('*'); }
        return out.toString();
    }

    private void showSearchResults(List<SearchHit> hits) {
        searchResults.removeAllViews();
        if (hits.isEmpty()) { TextView empty = new TextView(this); empty.setText("לא נמצאו תוצאות. נסה מילה אחרת."); empty.setPadding(12,12,12,12); searchResults.addView(empty); return; }
        for (SearchHit hit : hits) {
            Button item = new Button(this);
            item.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            item.setText(hit.name + "  ·  " + hit.kind);
            item.setOnClickListener(v -> { mapView.getModel().mapViewPosition.setCenter(new LatLong(hit.lat, hit.lon)); mapView.getModel().mapViewPosition.setZoomLevel((byte) 16); searchResults.removeAllViews(); });
            searchResults.addView(item, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void buildOfflineRoute() {
        String fromText = startInput.getText().toString().trim();
        String toText = endInput.getText().toString().trim();
        if (fromText.isEmpty() || toText.isEmpty()) { Toast.makeText(this, "יש למלא מוצא ויעד", Toast.LENGTH_SHORT).show(); return; }
        if (!dataFile().isFile()) { Toast.makeText(this, "חבילת נתוני הניתוב לא נמצאה", Toast.LENGTH_LONG).show(); return; }
        routeStatus.setText("מחשב מסלול אופליין…");
        searchExecutor.execute(() -> {
            RouteResult result = null;
            String error = null;
            SQLiteDatabase db = null;
            try {
                db = SQLiteDatabase.openDatabase(dataFile().getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                SearchHit from = findPlace(db, fromText);
                SearchHit to = findPlace(db, toText);
                if (from == null || to == null) throw new Exception("לא נמצא מוצא או יעד במאגר המקומי");
                result = findPath(db, from, to, routeProfile);
                if (result == null) throw new Exception("לא נמצא מסלול בין הנקודות במאגר הכבישים");
            } catch (Exception e) { error = e.getMessage(); }
            finally { if (db != null) db.close(); }
            RouteResult finalResult = result;
            String finalError = error;
            runOnUiThread(() -> {
                if (finalResult == null) { routeStatus.setText("לא נמצא מסלול"); Toast.makeText(this, finalError, Toast.LENGTH_LONG).show(); }
                else showRoute(finalResult);
            });
        });
    }

    private SearchHit findPlace(SQLiteDatabase db, String text) {
        String match = ftsQuery(text);
        try (Cursor c = db.rawQuery("SELECT p.name,p.lat,p.lon,p.kind FROM places_fts f JOIN places p ON p.id=f.docid WHERE places_fts MATCH ? LIMIT 1", new String[]{match})) {
            if (c.moveToFirst()) return new SearchHit(c.getString(0), c.getDouble(1), c.getDouble(2), c.getString(3));
        }
        return null;
    }

    private RouteResult findPath(SQLiteDatabase db, SearchHit from, SearchHit to, String profile) throws Exception {
        RoadNode start = nearestNode(db, from.lat, from.lon);
        RoadNode goal = nearestNode(db, to.lat, to.lon);
        if (start == null || goal == null) throw new Exception("לא נמצאה דרך קרובה למוצא או ליעד");
        String column = "car".equals(profile) ? "car" : ("bike".equals(profile) ? "bike" : "foot");
        PriorityQueue<RouteState> open = new PriorityQueue<>(Comparator.comparingDouble(s -> s.score));
        Map<Long, Double> costs = new HashMap<>();
        Map<Long, Long> previous = new HashMap<>();
        Set<Long> settled = new HashSet<>();
        costs.put(start.id, 0d);
        open.add(new RouteState(start.id, 0, distance(start.lat, start.lon, goal.lat, goal.lon)));
        int visited = 0;
        boolean reached = false;
        while (!open.isEmpty() && visited < 180000) {
            RouteState current = open.poll();
            if (!settled.add(current.id)) continue;
            visited++;
            if (current.id == goal.id) { reached = true; break; }
            try (Cursor c = db.rawQuery("SELECT r.to_id,r.distance_m,n.lat,n.lon FROM roads r JOIN road_nodes n ON n.id=r.to_id WHERE r.from_id=? AND r." + column + "=1", new String[]{Long.toString(current.id)})) {
                while (c.moveToNext()) {
                    long next = c.getLong(0);
                    if (settled.contains(next)) continue;
                    double nextCost = current.cost + c.getDouble(1);
                    Double old = costs.get(next);
                    if (old == null || nextCost < old) {
                        costs.put(next, nextCost);
                        previous.put(next, current.id);
                        open.add(new RouteState(next, nextCost, nextCost + distance(c.getDouble(2), c.getDouble(3), goal.lat, goal.lon)));
                    }
                }
            }
        }
        if (!reached) return null;
        List<Long> ids = new ArrayList<>();
        long cursor = goal.id;
        ids.add(cursor);
        while (cursor != start.id) {
            Long p = previous.get(cursor);
            if (p == null) return null;
            cursor = p;
            ids.add(cursor);
        }
        Collections.reverse(ids);
        List<LatLong> points = new ArrayList<>();
        for (Long id : ids) {
            RoadNode n = nodeById(db, id);
            if (n != null) points.add(new LatLong(n.lat, n.lon));
        }
        return new RouteResult(points, costs.get(goal.id));
    }

    private RoadNode nearestNode(SQLiteDatabase db, double lat, double lon) {
        RoadNode best = null;
        double bestDistance = Double.MAX_VALUE;
        for (double radius : new double[]{0.02, 0.08, 0.25}) {
            try (Cursor c = db.rawQuery("SELECT id,lat,lon FROM road_nodes WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? ORDER BY ((lat-?)*(lat-?)+(lon-?)*(lon-?)) LIMIT 5000",
                    new String[]{Double.toString(lat-radius), Double.toString(lat+radius), Double.toString(lon-radius), Double.toString(lon+radius), Double.toString(lat), Double.toString(lat), Double.toString(lon), Double.toString(lon)})) {
                while (c.moveToNext()) {
                    double d = distance(lat, lon, c.getDouble(1), c.getDouble(2));
                    if (d < bestDistance) { bestDistance = d; best = new RoadNode(c.getLong(0), c.getDouble(1), c.getDouble(2)); }
                }
            }
            if (best != null && bestDistance < 1200) break;
        }
        return best;
    }

    private RoadNode nodeById(SQLiteDatabase db, long id) {
        try (Cursor c = db.rawQuery("SELECT lat,lon FROM road_nodes WHERE id=?", new String[]{Long.toString(id)})) {
            if (c.moveToFirst()) return new RoadNode(id, c.getDouble(0), c.getDouble(1));
        }
        return null;
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2-lat1), dl = Math.toRadians(lon2-lon1);
        double sinDp = Math.sin(dp/2), sinDl = Math.sin(dl/2);
        double a = sinDp*sinDp + Math.cos(p1)*Math.cos(p2)*sinDl*sinDl;
        return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }

    private void showRoute(RouteResult result) {
        if (routeLine != null) mapView.getLayerManager().getLayers().remove(routeLine);
        Paint paint = AndroidGraphicFactory.INSTANCE.createPaint();
        paint.setColor(AndroidGraphicFactory.INSTANCE.createColor(0xFF1565C0));
        paint.setStrokeWidth(7 * mapView.getModel().displayModel.getScaleFactor());
        paint.setStyle(Style.STROKE);
        routeLine = new Polyline(paint, AndroidGraphicFactory.INSTANCE);
        routeLine.setPoints(result.points);
        mapView.getLayerManager().getLayers().add(routeLine);
        if (!result.points.isEmpty()) {
            LatLong middle = result.points.get(result.points.size()/2);
            mapView.getModel().mapViewPosition.setCenter(middle);
            mapView.getModel().mapViewPosition.setZoomLevel((byte) 11);
        }
        routeStatus.setText("מסלול אופליין: " + String.format(java.util.Locale.US, "%.1f", result.distance/1000d) + " ק״מ · " + result.points.size() + " נקודות");
    }

    private static final class SearchHit {
        final String name; final double lat, lon; final String kind;
        SearchHit(String name, double lat, double lon, String kind) { this.name=name; this.lat=lat; this.lon=lon; this.kind=kind; }
    }
    private static final class RoadNode {
        final long id; final double lat, lon;
        RoadNode(long id,double lat,double lon) { this.id=id; this.lat=lat; this.lon=lon; }
    }
    private static final class RouteState {
        final long id; final double cost, score;
        RouteState(long id,double cost,double score) { this.id=id; this.cost=cost; this.score=score; }
    }
    private static final class RouteResult {
        final List<LatLong> points; final double distance;
        RouteResult(List<LatLong> points,double distance) { this.points=points; this.distance=distance; }
    }

    @Override protected void onDestroy() {
        if (mapView != null) mapView.destroyAll();
        if (mapStore != null) mapStore.close();
        searchExecutor.shutdownNow();
        super.onDestroy();
    }
}
