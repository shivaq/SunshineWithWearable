package com.example.android.sunshine;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.example.android.sunshine.sync.SunshineSyncTask;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import timber.log.Timber;

public class WithWearListenerService extends WearableListenerService implements
        GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks{

    private static final String START_SYNC_PATH = "/start_sync";
    GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate() {
        super.onCreate();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        if(messageEvent.getPath().equals(START_SYNC_PATH)){
            SunshineSyncTask.syncWeather(this);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Timber.d("WithWearListenerService:onConnected: ");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Timber.d("WithWearListenerService:onConnectionSuspended: ");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Timber.d("WithWearListenerService:onConnectionFailed: ");
    }
}
