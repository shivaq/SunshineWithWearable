package com.example.android.sunshine;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;

import timber.log.Timber;

public class WithWearListenerService implements
        MessageApi.MessageListener,
        GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks
{


    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

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
