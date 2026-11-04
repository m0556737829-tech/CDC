package il.co.hebrewmapsoffline;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.rendertheme.InternalRenderTheme;

public final class MainActivity extends Activity {
    private MapView mapView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidGraphicFactory.createInstance(getApplication());

        File mapFile = new File(getFilesDir(), "maps/israel.map");
        if (mapFile.isFile()) {
            showOfflineMap(mapFile);
        } else {
            showDataInstallerNotice();
        }
    }

    private void showOfflineMap(File file) {
        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.getMapScaleBar().setVisible(true);
        mapView.setBuiltInZoomControls(true);
        mapView.getModel().mapViewPosition.setZoomLevel((byte) 8);

        MapDataStore dataStore = new MapFile(file);
        TileRendererLayer layer = new TileRendererLayer(
            AndroidUtil.createTileCache(this, "israel-map-cache",
                mapView.getModel().displayModel.getTileSize(), 1f,
                mapView.getModel().frameBufferModel.getOverdrawFactor()),
            dataStore,
            mapView.getModel().mapViewPosition,
            AndroidGraphicFactory.INSTANCE
        );
        layer.setXmlRenderTheme(InternalRenderTheme.DEFAULT);
        mapView.getLayerManager().getLayers().add(layer);
        setContentView(mapView);
    }

    private void showDataInstallerNotice() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(36, 36, 36, 36);
        root.setLayoutDirection(ViewGroup.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("מפות ישראל אופליין");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView message = new TextView(this);
        message.setText("האפליקציה הותקנה. כדי להציג מפה ללא אינטרנט, יש להתקין את חבילת נתוני המפה המקומית. חבילת הנתונים נבנית בנפרד כדי לשמור על APK קטן.");
        message.setTextSize(17);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 24, 0, 0);
        root.addView(message, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) mapView.destroyAll();
    }
}
