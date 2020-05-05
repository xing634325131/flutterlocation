package com.lyokone.location;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

class FlutterLocation
        implements PluginRegistry.RequestPermissionsResultListener, PluginRegistry.ActivityResultListener {
    private static final String TAG = "FlutterLocation";
    private static final boolean DEBUG = false;

    private final Context applicationContext;

    @Nullable
    private Activity activity;

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;
    private static final int REQUEST_CHECK_SETTINGS = 0x1;

    private static final int GPS_ENABLE_REQUEST = 0x1001;

    @TargetApi(Build.VERSION_CODES.N)
    private OnNmeaMessageListener mMessageListener;

    private Double mLastMslAltitude;

    // Parameters of the request
    private static long updateIntervalMilliseconds = 5000;
    private static long fastestUpdateIntervalMilliseconds = updateIntervalMilliseconds / 2;
    private static float distanceFilter = 0f;

    public EventSink events;

    // Store result until a permission check is resolved
    public Result result;

    // Store result until a location is getting resolved
    public Result getLocationResult;

    private int locationPermissionState;

    private boolean waitingForPermission = false;
    private boolean hasGetLocation = false;
    private LocationManager locationManager;

    private final static String PROVIDER = LocationManager.GPS_PROVIDER;

    FlutterLocation(Context applicationContext, @Nullable Activity activity) {
        this.applicationContext = applicationContext;
        this.activity = activity;
    }

    void setActivity(@Nullable Activity activity) {
        this.activity = activity;
        locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE && permissions.length == 1
                && permissions[0].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Checks if this permission was automatically triggered by a location request
                if (getLocationResult != null || events != null) {
                    startRequestingLocation();
                }
                if (result != null) {
                    result.success(1);
                    result = null;
                }
            } else {
                if (!shouldShowRequestPermissionRationale()) {
                    if (getLocationResult != null) {
                        getLocationResult.error("PERMISSION_DENIED_NEVER_ASK",
                                "Location permission denied forever- please open app settings", null);
                        getLocationResult = null;
                    }
                    if (events != null) {
                        events.error("PERMISSION_DENIED_NEVER_ASK",
                                "Location permission denied forever - please open app settings", null);
                        events = null;
                    }
                    if (result != null) {
                        result.success(2);
                        result = null;
                    }

                } else {
                    if (getLocationResult != null) {
                        getLocationResult.error("PERMISSION_DENIED", "Location permission denied", null);
                        getLocationResult = null;
                    }
                    if (events != null) {
                        events.error("PERMISSION_DENIED", "Location permission denied", null);
                        events = null;
                    }
                    if (result != null) {
                        result.success(0);
                        result = null;
                    }

                }
            }
            return true;
        }
        return false;

    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.e("onActivityResult", "requestCode:" + requestCode + ",resultCode:" + resultCode);
        if (result == null) {
            return false;
        }
        switch (requestCode) {
            case GPS_ENABLE_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    result.success(1);
                } else {
                    if(checkServiceEnabled(null)) {
                        result.success(1);
                    } else {
                        result.success(0);
                    }
                }
                break;
            case REQUEST_CHECK_SETTINGS:
                if (resultCode == Activity.RESULT_OK) {
                    startRequestingLocation();
                    return true;
                }

                result.error("SERVICE_STATUS_DISABLED", "Failed to get location. Location services disabled", null);
                return false;
            default:
                return false;
        }
        return true;
    }

    /**
     * Return the current state of the permissions needed.
     */
    public boolean checkPermissions() {
        this.locationPermissionState = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return this.locationPermissionState == PackageManager.PERMISSION_GRANTED;
    }

    public void requestPermissions() {
        if (checkPermissions()) {
            result.success(1);
            return;
        }
        ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                REQUEST_PERMISSIONS_REQUEST_CODE);
    }

    public boolean shouldShowRequestPermissionRationale() {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    public boolean checkServiceEnabled(final Result result) {
        boolean gps_enabled = false;
        boolean network_enabled = false;

        try {
            gps_enabled = this.locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            network_enabled = this.locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
            result.error("SERVICE_STATUS_ERROR", "Location service status couldn't be determined", null);
            return false;
        }
        if (gps_enabled || network_enabled) {
            if (result != null) {
                result.success(1);
            }
            return true;

        } else {
            if (result != null) {
                result.success(0);
            }
            return false;
        }
    }

    public void requestService(final Result result) {
        if (this.checkServiceEnabled(null)) {
            result.success(1);
            return;
        }
        this.result = result;

        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        activity.startActivityForResult(intent, GPS_ENABLE_REQUEST);
    }

    public synchronized void startRequestingLocation() {
        hasGetLocation = false;
        LocationListener listener = new LocationListener() {

            @Override
            public void onLocationChanged(Location location) {
                Log.d(TAG, "onLocationChanged");
                if(location != null && !hasGetLocation) {
                    onGetLocation(location);
                    locationManager.removeUpdates(this);
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        new CountDownTimer(5000, 5000){

            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                if(!hasGetLocation) {
                    debug( "timeout for get location");
                    debug( "try get cached gps location");

                    Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    if(gpsLocation != null) {
                        onGetLocation(gpsLocation);
                    }  else {
                        debug("cached gps location is null");
                        debug("try get cached network location");

                        Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if(networkLocation != null) {
                            onGetLocation(networkLocation);
                        }
                    }
                } else {
                    debug("ignore. has get location");
                }
            }
        }.start();



        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5,
                listener, activity.getMainLooper());
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 5,
                listener, activity.getMainLooper());
    }

    private void onGetLocation(Location location) {
        debug("onGetLocation: " + location.toString());
        Map<String, Double> loc = generateResult(location);
        if(getLocationResult != null && !hasGetLocation) {
            hasGetLocation = true;
            getLocationResult.success(loc);
        }
    }

    private Map<String, Double> generateResult(Location location) {
        HashMap<String, Double> loc = new HashMap<>();
        loc.put("latitude", location.getLatitude());
        loc.put("longitude", location.getLongitude());
        loc.put("accuracy", (double) location.getAccuracy());
        loc.put("altitude", location.getAltitude());
        loc.put("speed", (double) location.getSpeed());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            loc.put("speed_accuracy", (double) location.getSpeedAccuracyMetersPerSecond());
        }
        loc.put("heading", (double) location.getBearing());
        loc.put("time", (double) location.getTime());
        return loc;
    }

    private void debug(String message) {
        if(DEBUG) {
            Log.d(TAG,message);
        }
    }

}
