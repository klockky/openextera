package com.exteragram.messenger.maps.yandex;

import android.content.Context;
import android.location.Location;

import androidx.core.util.Consumer;

import org.telegram.messenger.ILocationServiceProvider;

// Lite build: Yandex MapKit is not included, this is a stub.
public class YandexLocationProvider implements ILocationServiceProvider {

    @Override
    public void init(Context context) {
    }

    @Override
    public ILocationRequest onCreateLocationRequest() {
        return null;
    }

    @Override
    public IMapApiClient onCreateLocationServicesAPI(Context context, IAPIConnectionCallbacks connectionCallbacks, IAPIOnConnectionFailedListener failedListener) {
        return null;
    }

    @Override
    public boolean checkServices() {
        return false;
    }

    @Override
    public void getLastLocation(Consumer<Location> callback) {
    }

    @Override
    public void requestLocationUpdates(ILocationRequest request, ILocationListener locationListener) {
    }

    @Override
    public void removeLocationUpdates(ILocationListener locationListener) {
    }

    @Override
    public void checkLocationSettings(ILocationRequest request, Consumer<Integer> callback) {
        if (callback != null) {
            callback.accept(STATUS_SETTINGS_CHANGE_UNAVAILABLE);
        }
    }
}
