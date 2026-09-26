package com.exteragram.messenger.maps.yandex;

import android.content.Context;

import org.telegram.messenger.IMapsProvider;
import org.telegram.messenger.R;

// Lite build: Yandex MapKit is not included, this is a stub.
public class YandexMapsProvider implements IMapsProvider {

    public static boolean isSupported() {
        return false;
    }

    public static void terminate() {
    }

    @Override
    public void initializeMaps(Context context) {
    }

    @Override
    public boolean isApplicationRequired() {
        return false;
    }

    @Override
    public IMapView onCreateMapView(Context context) {
        return null;
    }

    @Override
    public IMarkerOptions onCreateMarkerOptions() {
        return null;
    }

    @Override
    public ICircleOptions onCreateCircleOptions() {
        return null;
    }

    @Override
    public ILatLngBoundsBuilder onCreateLatLngBoundsBuilder() {
        return null;
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLng(LatLng latLng) {
        return null;
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngZoom(LatLng latLng, float zoom) {
        return null;
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngBounds(ILatLngBounds bounds, int padding) {
        return null;
    }

    @Override
    public IMapStyleOptions loadRawResourceStyle(Context context, int resId) {
        return null;
    }

    @Override
    public boolean supportsOtherMapTypes() {
        return false;
    }

    @Override
    public String getMapsAppPackageName() {
        return "ru.yandex.yandexmaps";
    }

    @Override
    public int getInstallMapsString() {
        return R.string.InstallYandexMaps;
    }
}
