package com.example.android.sunshine;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import timber.log.Timber;

public class ConnectWearUtils implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String HIGH_TEMPERATURE_KEY = "high_temp";
    private static final String LOW_TEMPERATURE_KEY = "low_temp";
    private static final String WEATHER_IMAGE_KEY = "img_weather";
    private static final String PASS_WEATHER_DATA_PATH = "/pass_weather_data";

    private GoogleApiClient mGoogleApiClient;
    private Context mContext;
    private Bitmap mWeatherIconBitmap;
    private String mHighTemp;
    private String mLowTemp;

    public ConnectWearUtils(Context context, Bitmap weatherIconBitmap, String highTemp, String lowTemp) {
        mContext = context;
        mWeatherIconBitmap = weatherIconBitmap;
        mHighTemp = highTemp;
        mLowTemp = lowTemp;
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Timber.d("ConnectWearUtils:onConnected: isConnected: %s Context is %s", mGoogleApiClient.isConnected(), mContext);
        sendWeatherToWear();
    }


    private void sendWeatherToWear() {

        Timber.d("ConnectWearUtils:sendWeatherToWear2: isConnected %s", mGoogleApiClient.isConnected());
        Asset weatherIconAsset = bitmapToAsset(mWeatherIconBitmap);

        PutDataMapRequest dataMapRequest = PutDataMapRequest.create(PASS_WEATHER_DATA_PATH);

        dataMapRequest.getDataMap().putAsset(WEATHER_IMAGE_KEY, weatherIconAsset);
        dataMapRequest.getDataMap().putString(HIGH_TEMPERATURE_KEY, mHighTemp);
        dataMapRequest.getDataMap().putString(LOW_TEMPERATURE_KEY, mLowTemp);
        dataMapRequest.getDataMap().putLong("time", System.currentTimeMillis());

        Timber.d("ConnectWearUtils:sendWeatherToWear: high, low, asset is %s, %s", mHighTemp, mLowTemp);
        PutDataRequest dataRequest = dataMapRequest.asPutDataRequest();
        dataRequest.setUrgent();

        Wearable.DataApi.putDataItem(mGoogleApiClient, dataRequest)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                        Timber.d("ConnectWearUtils:onResult: Sending image was successful: %s",
                                dataItemResult.getStatus().isSuccess());
                    }
                });
    }

    private Asset bitmapToAsset(Bitmap bitmap) {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            return Asset.createFromBytes(byteStream.toByteArray());
        } finally {
            if (null != byteStream) {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }


    @Override
    public void onConnectionSuspended(int i) {
        Timber.d("ConnectWearUtils:onConnectionSuspended: ");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Timber.d("ConnectWearUtils:onConnectionFailed: ");
    }
}
