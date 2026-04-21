package com.prj.keplerv0;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import androidx.core.content.ContextCompat;

/**
 * Lightweight wrapper around Android's {@link LocationManager}.
 *
 * Immediately returns the best last-known position (no wait needed for most
 * users), then subscribes to live updates every 60 seconds / 500 metres.
 * That granularity is more than sufficient: star visibility only changes
 * noticeably over many minutes and hundreds of kilometres.
 *
 * Call {@link #start()} only after location permission has been granted.
 */
public class LocationProvider {

    public interface OnLocationReadyListener {
        void onLocationReady(double lat, double lon);
    }

    private final Context             context;
    private       LocationManager     locationManager;
    private       OnLocationReadyListener listener;

    // volatile so the GL thread can safely read these without a lock
    private volatile double latitude  = Double.NaN;
    private volatile double longitude = Double.NaN;

    public LocationProvider(Context context) {
        // Use application context to avoid Activity leaks
        this.context = context.getApplicationContext();
    }

    public void setOnLocationReadyListener(OnLocationReadyListener l) {
        this.listener = l;
    }

    public boolean hasLocation() { return !Double.isNaN(latitude); }
    public double  getLatitude()  { return latitude; }
    public double  getLongitude() { return longitude; }

    /**
     * Start fetching location. Must be called only after the location
     * permission has been granted; it silently returns if it hasn't been.
     */
    public void start() {
        boolean fineGranted   = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!fineGranted && !coarseGranted) return;

        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        // Use last-known fix first — instant, no wait required
        Location last = bestLastKnown();
        if (last != null) updateLocation(last);

        // Subscribe for live updates — 60 s interval, 500 m minimum displacement
        LocationListener locListener = new LocationListener() {
            @Override public void onLocationChanged(Location l) { updateLocation(l); }
            @Override public void onStatusChanged(String p, int s, Bundle e) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };

        register(LocationManager.GPS_PROVIDER,     locListener);
        register(LocationManager.NETWORK_PROVIDER, locListener);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Location bestLastKnown() {
        Location gps = safeLastKnown(LocationManager.GPS_PROVIDER);
        Location net = safeLastKnown(LocationManager.NETWORK_PROVIDER);
        if (gps != null && net != null)
            return gps.getAccuracy() <= net.getAccuracy() ? gps : net;
        return gps != null ? gps : net;
    }

    private Location safeLastKnown(String provider) {
        try {
            if (locationManager.isProviderEnabled(provider))
                return locationManager.getLastKnownLocation(provider);
        } catch (Exception ignored) {}
        return null;
    }

    private void register(String provider, LocationListener l) {
        try {
            if (locationManager.isProviderEnabled(provider))
                locationManager.requestLocationUpdates(provider, 60_000L, 500f, l);
        } catch (Exception ignored) {}
    }

    private void updateLocation(Location loc) {
        latitude  = loc.getLatitude();
        longitude = loc.getLongitude();
        if (listener != null) listener.onLocationReady(latitude, longitude);
    }
}
