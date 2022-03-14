package com.dcerna.openstreetmaps;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer;
import org.osmdroid.bonuspack.location.GeoNamesPOIProvider;
import org.osmdroid.bonuspack.location.GeocoderNominatim;
import org.osmdroid.bonuspack.location.OverpassAPIProvider;
import org.osmdroid.bonuspack.location.POI;
import org.osmdroid.bonuspack.routing.GoogleRoadManager;
import org.osmdroid.bonuspack.routing.GraphHopperRoadManager;
import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.bonuspack.routing.RoadNode;
import org.osmdroid.bonuspack.utils.BonusPackHelper;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.TileSystem;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow;
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow;
import org.osmdroid.views.overlay.mylocation.DirectedLocationOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    static final String userAgent = BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME;
    protected MapView map;
    protected FolderOverlay mItineraryMarkers;
    protected static int START_INDEX = -2, DEST_INDEX = -1;
    final private int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;
    protected Polyline mRoadOverlay;
    protected GeoPoint startPoint, destinationPoint;
    protected Marker markerStart, markerDestination;
    public Road mRoad;
    protected Polygon mDestinationPolygon;

    LocationManager locationManager;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.activity_main, null);
        setContentView(v);

        // Inicializa la configuracion de osmdroid
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        map = (MapView) v.findViewById(R.id.map);

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setTilesScaledToDpi(true);
        map.setMultiTouchControls(true);
        map.setMinZoomLevel(1.0);
        map.setMaxZoomLevel(21.0);
        map.setVerticalMapRepetitionEnabled(false);
        map.setScrollableAreaLimitLatitude(TileSystem.MaxLatitude, -TileSystem.MaxLatitude, 0/*map.getHeight()/2*/);

        IMapController mapController = map.getController();
        mapController.setZoom(15f);
        mapController.setCenter(new GeoPoint(20.1436787, -101.157365820));

        startPoint = null;
        destinationPoint = null;

        // Itinerary markers:
        mItineraryMarkers = new FolderOverlay();
        mItineraryMarkers.setName("Puntos de ruta");
        map.getOverlays().add(mItineraryMarkers);

        EditText destinationText = findViewById(R.id.editDestination);

        SharedPreferences prefs = getSharedPreferences("NAVEGACION", MODE_PRIVATE);
        String destination = prefs.getString("SAVED_DESTINATION", "");

        destinationText.setText(destination);
        Button searchDestButton = (Button) findViewById(R.id.buttonSearchDest);
        searchDestButton.setOnClickListener(view -> searchPlace(DEST_INDEX, R.id.editDestination));

        updateUIWithRoads(null);
        checkPermissions();

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        getCurrentLocation();

    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            locationManager.getCurrentLocation(
                    LocationManager.GPS_PROVIDER,
                    null,
                    this.getMainExecutor(),
                    this::setCurrentLocation);
        }
    }

    public void setCurrentLocation(Location p){
        startPoint = new GeoPoint(p.getLatitude(), p.getLongitude());
        markerStart = updateMarker(markerStart, startPoint, START_INDEX,
                "Origen", "Localizacion actual");
        map.getController().setCenter(startPoint);
    }

    void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        String message = "Application permissions:";
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            message += "\nLocation to show user location.";
        }
        if (!permissions.isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            String[] params = permissions.toArray(new String[permissions.size()]);
            ActivityCompat.requestPermissions(this, params, REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: {
                Map<String, Integer> perms = new HashMap<>();
                perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                for (int i = 0; i < permissions.length; i++)
                    perms.put(permissions[i], grantResults[i]);
            }
        }
        getCurrentLocation();
    }


    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public void searchPlace(int index, int editResId) {
        EditText locationEdit = (EditText) findViewById(editResId);
        //Hide the soft keyboard:
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(locationEdit.getWindowToken(), 0);
        String locationAddress = locationEdit.getText().toString();

        SharedPreferences prefs = getSharedPreferences("NAVEGACION", MODE_PRIVATE);
        prefs.edit().putString("SAVED_DESTINATION", locationAddress).apply();

        new GeocodingTask().execute(locationAddress, index);
    }

    //add or replace the polygon overlay
    public void updateUIWithPolygon(ArrayList<GeoPoint> polygon, String name) {
        List<Overlay> mapOverlays = map.getOverlays();
        int location = -1;
        if (mDestinationPolygon != null)
            location = mapOverlays.indexOf(mDestinationPolygon);
        mDestinationPolygon = new Polygon();
        mDestinationPolygon.setFillColor(0x15FF8080);
        mDestinationPolygon.setStrokeColor(0x808000FF);
        mDestinationPolygon.setStrokeWidth(10.0f);
        mDestinationPolygon.setTitle(name);
        if (polygon != null) {
            mDestinationPolygon.setPoints(polygon);
        }
        if (location != -1)
            mapOverlays.set(location, mDestinationPolygon);
        else
            mapOverlays.add(1, mDestinationPolygon);
        map.invalidate();
    }

    /**
     * Update (or create if null) a marker in itineraryMarkers.
     */
    public Marker updateMarker(Marker marker, GeoPoint p, int index, String title, String address) {
        if (marker == null) {
            marker = new Marker(map);
            mItineraryMarkers.add(marker);
        }
        marker.setTitle(title);
        marker.setPosition(p);
        marker.setTextLabelFontSize(32);
        marker.setTextIcon(title);
        marker.setRelatedObject(index);
        map.invalidate();
        if (address != null)
            marker.setSnippet(address);

        return marker;
    }

    void setRouteInfo() {
        //Set route info in the text view:
        TextView textView = (TextView) findViewById(R.id.routeInfo);
        textView.setText(mRoad.getLengthDurationText(this, -1));
        map.invalidate();
    }

    void updateUIWithRoads(Road road) {
        TextView textView = (TextView) findViewById(R.id.routeInfo);
        textView.setText("");
        List<Overlay> mapOverlays = map.getOverlays();
        if (mRoadOverlay != null) {
            mapOverlays.remove(mRoadOverlay);
            mRoadOverlay = null;
        }
        if (road == null) return;

        Polyline roadPolyline = RoadManager.buildRoadOverlay(road);
        mRoadOverlay = roadPolyline;
        String routeDesc = road.getLengthDurationText(this, -1);
        roadPolyline.setTitle("Ruta - " + routeDesc);
        roadPolyline.setInfoWindow(new BasicInfoWindow(org.osmdroid.bonuspack.R.layout.bonuspack_bubble, map));
        roadPolyline.setRelatedObject(0);
        mapOverlays.add(1, roadPolyline);
        //we insert the road overlays at the "bottom", just above the MapEventsOverlay,
        //to avoid covering the other overlays.
        setRouteInfo();
    }

    public void getRoadAsync() {
        mRoad = null;
        GeoPoint roadStartPoint = null;
        if (startPoint != null) {
            roadStartPoint = startPoint;
        }
        if (roadStartPoint == null || destinationPoint == null) {
            updateUIWithRoads(null);
            return;
        }
        ArrayList<GeoPoint> waypoints = new ArrayList(2);
        waypoints.add(roadStartPoint);
        waypoints.add(destinationPoint);
        new UpdateRoadTask(this).execute(waypoints);
    }


    /**
     * Async task to get the road in a separate thread.
     */
    private class UpdateRoadTask extends AsyncTask<ArrayList<GeoPoint>, Void, Road[]> {
        private final Context mContext;

        public UpdateRoadTask(Context context) {
            this.mContext = context;
        }

        protected Road[] doInBackground(ArrayList<GeoPoint>... params) {
            ArrayList<GeoPoint> waypoints = params[0];
            RoadManager roadManager = new OSRMRoadManager(mContext, userAgent);
            return roadManager.getRoads(waypoints);
        }

        protected void onPostExecute(Road[] result) {
            if(result.length==0) return;
            mRoad = result[0];
            updateUIWithRoads(result[0]);

        }
    }

    /**
     * Async task to get a location point in a separate thread
     */
    private class GeocodingTask extends AsyncTask<Object, Void, List<Address>> {
        int mIndex;

        protected List<Address> doInBackground(Object... params) {
            String locationAddress = (String) params[0];
            mIndex = (Integer) params[1];
            GeocoderNominatim geocoder = new GeocoderNominatim(userAgent);
            geocoder.setOptions(true);
            try {
                BoundingBox viewbox = map.getBoundingBox();
                List<Address> foundAdresses = geocoder.getFromLocationName(locationAddress, 1,
                        viewbox.getLatSouth(), viewbox.getLonEast(),
                        viewbox.getLatNorth(), viewbox.getLonWest(), false);
                return foundAdresses;
            } catch (Exception e) {
                return null;
            }
        }

        protected void onPostExecute(List<Address> foundAdresses) {
            if (foundAdresses == null) {
                Toast.makeText(getApplicationContext(), "Geocoding error", Toast.LENGTH_SHORT).show();
            } else if (foundAdresses.size() == 0) { //if no address found, display an error
                Toast.makeText(getApplicationContext(), "Address not found.", Toast.LENGTH_SHORT).show();
            } else {
                Address address = foundAdresses.get(0); //get first address
                String addressDisplayName = address.getExtras().getString("display_name");
                if (mIndex == DEST_INDEX) {
                    destinationPoint = new GeoPoint(address.getLatitude(), address.getLongitude());
                    markerDestination = updateMarker(markerDestination, destinationPoint, DEST_INDEX,
                            "Destino", addressDisplayName);
                    map.getController().setCenter(destinationPoint);
                }
                getRoadAsync();
                //get and display enclosing polygon:
                Bundle extras = address.getExtras();
                ArrayList<GeoPoint> polygon = extras.getParcelableArrayList("polygonpoints");
                updateUIWithPolygon(polygon, addressDisplayName);
            }
        }
    }
}