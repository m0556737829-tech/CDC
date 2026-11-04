package il.co.hebrewmapsoffline;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.android.rendertheme.AssetsRenderTheme;

public final class MainActivity extends Activity {
    private static final int PICK_MAP_DATA = 4102;
    private MapView mapView;
    private LinearLayout root;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AndroidGraphicFactory.createInstance(getApplication());
        showHome();
    }

    private File mapFile() {
        return new File(getFilesDir(), "maps/israel.map");
    }

    private void showHome() {
        if (mapFile().isFile()) {
            showOfflineMap(mapFile());
            return;
        }
        root = new LinearLayout(this);
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
        description.setText("מפת ישראל נשמרת במכשיר ופועלת ללא חיבור לאינטרנט.\n\nכדי להתחיל, יש לייבא את קובץ המפה המקומי (israel.map) או חבילת ZIP שמכילה אותו.");
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
        note.setText("הערה: גרסה זו כוללת את מנוע הצגת המפה וייבוא הנתונים. חיפוש כתובות וניווט מסלולים יופעלו לאחר הוספת מסד החיפוש ונתוני הניתוב לחבילת ישראל.");
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
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/octet-stream", "application/x-mapsforge-map"});
        startActivityForResult(intent, PICK_MAP_DATA);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_MAP_DATA || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            importMap(data.getData());
            Toast.makeText(this, "המפה נשמרה במכשיר", Toast.LENGTH_LONG).show();
            showHome();
        } catch (Exception e) {
            Toast.makeText(this, "לא הצלחתי לייבא את הקובץ: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importMap(Uri uri) throws Exception {
        File target = mapFile();
        File dir = target.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("לא ניתן ליצור תיקיית מפות");
        String name = displayName(uri).toLowerCase();
        try (InputStream raw = getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new Exception("הקובץ לא נפתח");
            if (name.endsWith(".zip")) {
                boolean found = false;
                try (ZipInputStream zip = new ZipInputStream(raw)) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".map")) {
                            copy(zip, target);
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) throw new Exception("לא נמצא קובץ .map בתוך ה-ZIP");
            } else {
                copy(raw, target);
            }
        }
        try (MapFile check = new MapFile(target)) {
            if (check.getMapFileInfo() == null) throw new Exception("קובץ מפה לא תקין");
        } catch (Exception e) {
            target.delete();
            throw e;
        }
    }

    private String displayName(Uri uri) {\n        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {\n            if (cursor != null && cursor.moveToFirst()) {\n                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);\n                if (index >= 0) return cursor.getString(index);\n            }\n        } catch (Exception ignored) {}\n        return uri.getLastPathSegment() == null ? "map.data" : uri.getLastPathSegment();\n    }\n\n    private void copy(InputStream in, File out) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(out)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) stream.write(buffer, 0, count);
            stream.getFD().sync();
        }
    }

    private void showOfflineMap(File file) {
        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.getMapScaleBar().setVisible(true);
        mapView.setBuiltInZoomControls(true);
        mapView.getModel().mapViewPosition.setZoomLevel((byte) 8);
        MapDataStore store = new MapFile(file);
        TileRendererLayer layer = new TileRendererLayer(
            AndroidUtil.createTileCache(this, "israel-map-cache",
                mapView.getModel().displayModel.getTileSize(), 1f,
                mapView.getModel().frameBufferModel.getOverdrawFactor()),
            store, mapView.getModel().mapViewPosition, AndroidGraphicFactory.INSTANCE);
        layer.setXmlRenderTheme(new AssetsRenderTheme(getAssets(), "", "israel-render-theme.xml"));
        mapView.getLayerManager().getLayers().add(layer);
        setContentView(mapView);
    }

    @Override protected void onDestroy() {
        if (mapView != null) mapView.destroyAll();
        super.onDestroy();
    }
}
