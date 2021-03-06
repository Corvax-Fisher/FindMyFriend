package com.example.fmi_fmf;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MapsActivity extends FragmentActivity implements LocationListener,
        PositionRequestDialogFragment.RequestDialogListener {

    public static final String UPDATE_FRIEND_LOCATION = "friend location update";
    public static final String ACTION_SHOW_REQUEST_DIALOG = "show request dialog";

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    private Marker mMyLocationMarker;
    private Marker mFriendLocationMarker;

    private final int MY_MARKER = 1;
    private final int MY_FRIENDS_MARKER = 2;

    private String mRequesterJabberId;
    public static boolean isActive = false;

    private Polyline mRoutes[];
    private int mRouteColors[] = { Color.BLACK, Color.GRAY, Color.LTGRAY };
    private ProgressDialog mLocationLoadingDialog;

    BroadcastReceiver br = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(UPDATE_FRIEND_LOCATION))
            {
                double locationExtra[] = intent.getDoubleArrayExtra(
                        FMFCommunicationService.EXTRA_FRIEND_LOCATION );
                LatLng point = new LatLng(locationExtra[0], locationExtra[1]);

                processLocationUpdate(MY_FRIENDS_MARKER, point);
            } else if(intent.getAction().equals(ACTION_SHOW_REQUEST_DIALOG)) {
                mRequesterJabberId = intent.getStringExtra(FMFCommunicationService.EXTRA_JABBER_ID);
                String from = intent.getStringExtra(FMFCommunicationService.EXTRA_FULL_NAME);
                PositionRequestDialogFragment.getInstance().setFullName(from);
                if(!PositionRequestDialogFragment.getInstance().isAdded())
                    PositionRequestDialogFragment.getInstance()
                            .show(getSupportFragmentManager(), "Position request");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        if(ContactListActivity.D) Log.d(MapsActivity.class.getSimpleName(), "onCreate");
        isActive = true;

        getActionBar().setDisplayHomeAsUpEnabled(true);

        // Getting Google Play availability status
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getBaseContext());

        if(status!= ConnectionResult.SUCCESS){ // Google Play Services are not available

            int requestCode = 10;
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(status, this, requestCode);
            dialog.show();

        }else { // Google Play Services are available

            // Initializing
            setUpMapIfNeeded();

            // Getting LocationManager object from System Service LOCATION_SERVICE
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

            // Getting the name of the best provider
            String provider = locationManager.getBestProvider(new Criteria(), true);

            // Getting Current Location From GPS
            Location location = locationManager.getLastKnownLocation(provider);

            if(location!=null){
                onLocationChanged(location);
            }

            locationManager.requestLocationUpdates(provider, 5000, 0, this);

        }

//        Intent i = new Intent(this,FMFCommunicationService.class)
//                .setAction(FMFCommunicationService.ACTION_CANCEL_NOTIFICATION)
//                .putExtra(FMFCommunicationService.EXTRA_NOTIFICATION_ID, 1338);
//        startService(i);
        if(getIntent() != null){
            if(getIntent().getAction()!= null){
                if(getIntent().getAction().equals(ACTION_SHOW_REQUEST_DIALOG)) {
                    mRequesterJabberId = getIntent().getStringExtra(FMFCommunicationService.EXTRA_JABBER_ID);
                    if(!PositionRequestDialogFragment.getInstance().isAdded()) {
                        PositionRequestDialogFragment.getInstance().show(getSupportFragmentManager(),"Position request");
//                        i.putExtra(FMFCommunicationService.EXTRA_NOTIFICATION_ID,1337);
//                        startService(i);
                    }
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.action_alternatives:
                toggleVisibilityForAlternatives();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleVisibilityForAlternatives() {
        if(mRoutes != null)
            for(int i=1;i < mRoutes.length;i++) mRoutes[i].setVisible(!mRoutes[i].isVisible());
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        LocalBroadcastManager.getInstance(this).registerReceiver(br,
                new IntentFilter(UPDATE_FRIEND_LOCATION));
        LocalBroadcastManager.getInstance(this).registerReceiver(br,
                new IntentFilter(ACTION_SHOW_REQUEST_DIALOG));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(br);
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        lm.removeUpdates(this);
        startService(new Intent(this,FMFCommunicationService.class)
                .setAction(FMFCommunicationService.ACTION_SEND_STOP));
        isActive = false;
        if(mLocationLoadingDialog != null) {
            if(mLocationLoadingDialog.isShowing()) mLocationLoadingDialog.dismiss();
        }
        finish();
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        if(mMyLocationMarker != null)
        {
            mMap.moveCamera(CameraUpdateFactory.newLatLng(mMyLocationMarker.getPosition()));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(15));
        }
        if(mMyLocationMarker == null || mFriendLocationMarker == null) {
            mLocationLoadingDialog = new ProgressDialog(this);
//            mLocationLoadingDialog.setTitle(R.string.title_locations_loading);
            mLocationLoadingDialog.setMessage(getText(R.string.message_locations_loading));
            mLocationLoadingDialog.setIndeterminate(true);
            mLocationLoadingDialog.setCancelable(true);
            mLocationLoadingDialog.show();
        }
    }

    private void processLocationUpdate(int markerId, LatLng point) {
        Marker marker, theOtherMarker;
        String markerTitle;
        float hue;
        if(markerId == MY_MARKER) {
            markerTitle = "Ich";
            hue = BitmapDescriptorFactory.HUE_RED;
            marker = mMyLocationMarker;
            theOtherMarker = mFriendLocationMarker;
        }
        else {
            markerTitle = "Mein Freund";
            hue = BitmapDescriptorFactory.HUE_GREEN;
            marker = mFriendLocationMarker;
            theOtherMarker = mMyLocationMarker;
        }
        if(marker == null)
        {
            marker = mMap.addMarker(new MarkerOptions()
                    .position(point)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue))
                    .draggable(false)
                    .title(markerTitle));

            if(markerId == MY_MARKER) {
                marker.showInfoWindow();
                mMyLocationMarker = marker;
            }
            else mFriendLocationMarker = marker;
            if(theOtherMarker != null) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(
                                new LatLngBounds.Builder()
                                        .include(marker.getPosition())
                                        .include(theOtherMarker.getPosition())
                                        .build()
                                ,100)
                );
            } else {
                mMap.moveCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
                mMap.animateCamera(CameraUpdateFactory.zoomTo(15));
            }
        }
        else marker.setPosition(point);

        if(theOtherMarker != null)
        {
            mLocationLoadingDialog.dismiss();

            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                        new CameraPosition.Builder(mMap.getCameraPosition())
                                .bearing((float)(180/Math.PI*Math.atan2(
                                        mFriendLocationMarker.getPosition().longitude-
                                        mMyLocationMarker.getPosition().longitude,
                                        mFriendLocationMarker.getPosition().latitude-
                                        mMyLocationMarker.getPosition().latitude)))
                                .build())
            );
            // Getting URL to the Google Directions API
            String url = getDirectionsUrl(theOtherMarker.getPosition(),
                    marker.getPosition());

            DownloadTask downloadTask = new DownloadTask();

            // Start downloading json data from Google Directions API
            downloadTask.execute(url);
        }
    }

    private String getDirectionsUrl(LatLng origin,LatLng dest){

        // Origin of route
        String str_origin = "origin="+origin.latitude+","+origin.longitude;

        // Destination of route
        String str_dest = "destination="+dest.latitude+","+dest.longitude;

        // Sensor enabled
        String sensor = "sensor=false";

        // Mode walking
        String mode = "mode=walking";

        // Alternatives enabled
        String alternatives = "alternatives=true";

        // Metric units
//        String units = "units=metric";

        // Building the parameters to the web service
        String parameters = str_origin+"&"+str_dest+"&"+sensor+"&"+mode+"&"+alternatives+"&";//+units;

        // Output format
        String output = "json";

        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/"+output+"?"+parameters;

        return url;
    }

    /** A method to download json data from url */
    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try{
            URL url = new URL(strUrl);

            // Creating an http connection to communicate with url
            urlConnection = (HttpURLConnection) url.openConnection();

            // Connecting to url
            urlConnection.connect();

            // Reading data from url
            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb  = new StringBuffer();

            String line = "";
            while( ( line = br.readLine())  != null){
                sb.append(line);
            }

            data = sb.toString();

            br.close();

        }catch(Exception e){
            Log.d("Exception while downloading url", e.toString());
        }finally{
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

    @Override
    public void onCancel() {
        Intent i = new Intent(this,FMFCommunicationService.class)
                .setAction(FMFCommunicationService.ACTION_PROCESS_REQUEST_RESULT)
                .putExtra(FMFCommunicationService.EXTRA_JABBER_ID, mRequesterJabberId)
                .putExtra(FMFCommunicationService.EXTRA_REQUEST_ACCEPTED,false);
        startService(i);
    }

    @Override
    public void onDialogPositiveClick() {
        Intent i = new Intent(this,FMFCommunicationService.class)
                .setAction(FMFCommunicationService.ACTION_PROCESS_REQUEST_RESULT)
                .putExtra(FMFCommunicationService.EXTRA_JABBER_ID, mRequesterJabberId)
                .putExtra(FMFCommunicationService.EXTRA_REQUEST_ACCEPTED,true);
        startService(i);
    }

    @Override
    public void onDialogNegativeClick() {
        Intent i = new Intent(this,FMFCommunicationService.class)
                .setAction(FMFCommunicationService.ACTION_PROCESS_REQUEST_RESULT)
                .putExtra(FMFCommunicationService.EXTRA_JABBER_ID, mRequesterJabberId)
                .putExtra(FMFCommunicationService.EXTRA_REQUEST_ACCEPTED,false);
        startService(i);
    }


    /** A class to download data from Google Directions URL */
private class DownloadTask extends AsyncTask<String, Void, String> {

    // Downloading data in non-ui thread
    @Override
    protected String doInBackground(String... url) {

        // For storing data from web service
        String data = "";

        try{
            // Fetching the data from web service
            data = downloadUrl(url[0]);
        }catch(Exception e){
            Log.d("Background Task",e.toString());
        }
        return data;
    }

    // Executes in UI thread, after the execution of
    // doInBackground()
    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);

        ParserTask parserTask = new ParserTask();

        // Invokes the thread for parsing the JSON data
        parserTask.execute(result);

    }
}

/** A class to parse the Google Directions in JSON format */
private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String,String>>> > {

    // Parsing the data in non-ui thread
    @Override
    protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {

        JSONObject jObject;
        List<List<HashMap<String, String>>> routes = null;

        try {
            jObject = new JSONObject(jsonData[0]);
            DirectionsJSONParser parser = new DirectionsJSONParser();

            // Starts parsing data
            routes = parser.parse(jObject);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return routes;
    }

    // Executes in UI thread, after the parsing process
    @Override
    protected void onPostExecute(List<List<HashMap<String, String>>> result) {
        if(result == null) return;
        ArrayList<LatLng> points = null;
        PolylineOptions lineOptions = null;
        int numRoutes;
        boolean showAlternatives = false;

        if(mRoutes != null) {
            if(mRoutes.length >1) showAlternatives = mRoutes[1].isVisible();
            for(Polyline route : mRoutes) route.remove();
        }
        if(result.size() > 3) numRoutes = 3;
        else numRoutes = result.size();
        mRoutes = new Polyline[numRoutes];
        // Traversing through all the routes
        for (int i = 0; i < numRoutes; i++) {
            points = new ArrayList<LatLng>();
            lineOptions = new PolylineOptions();

            // Fetching i-th route
            List<HashMap<String, String>> path = result.get(i);

            // Fetching all the points in i-th route
            for (int j = 0; j < path.size(); j++) {
                HashMap<String, String> point = path.get(j);

                double lat = Double.parseDouble(point.get("lat"));
                double lng = Double.parseDouble(point.get("lng"));
                LatLng position = new LatLng(lat, lng);

                points.add(position);
            }

            // Adding all the points in the route to LineOptions
            lineOptions.addAll(points);
            lineOptions.width(4);
            lineOptions.color(mRouteColors[i % mRouteColors.length]);
            if(i>0) lineOptions.visible(showAlternatives);

            // Drawing polyline in the Google Map for the i-th route
            mRoutes[i] = mMap.addPolyline(lineOptions);
        }

    }

}

    @Override
    public void onLocationChanged(Location location) {

        LatLng point = new LatLng(location.getLatitude(), location.getLongitude());
        processLocationUpdate(MY_MARKER,point);
    }

    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
    }

}
