package com.example.android.sunshine;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.data.WeatherContract;
import com.example.android.sunshine.utilities.SunshineDateUtils;
import com.example.android.sunshine.utilities.SunshineWeatherUtils;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import timber.log.Timber;

public class ConnectWearService extends WearableListenerService implements
        GoogleApiClient.ConnectionCallbacks,
        CapabilityApi.CapabilityListener{


    private static final String TAG = ConnectWearService.class.getSimpleName();

    public static final String[] WEATHER_WEAR_PROJECTION = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
    };

    public static final int INDEX_WEATHER_ID = 0;
    public static final int INDEX_MAX_TEMP = 1;
    public static final int INDEX_MIN_TEMP = 2;

    private static final String HIGH_TEMPERATURE_KEY = "high_temp";
    private static final String LOW_TEMPERATURE_KEY = "low_temp";
    private static final String WEATHER_IMAGE_KEY = "img_weather";
    private static final String PASS_WEATHER_DATA_PATH = "/pass_weather_data";

    private GoogleApiClient mGoogleApiClient;


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Timber.d("ConnectWearService:onConnected: isConnected: %s", mGoogleApiClient.isConnected());
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }



    public void sendWeatherToWear(Context context){

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();

        mGoogleApiClient.connect();



        Timber.d("ConnectWearService:sendWeatherToWear:mGoogleApiClient is %s", mGoogleApiClient);
        getWeatherDataForWear(context);
    }

    private void sendWeatherToWear(int high, int low, Asset asset){

        Timber.d("ConnectWearService:sendWeatherToWear: isConnected %s", mGoogleApiClient.isConnected());
        PutDataMapRequest dataMapRequest = PutDataMapRequest.create(PASS_WEATHER_DATA_PATH);

        dataMapRequest.getDataMap().putAsset(WEATHER_IMAGE_KEY, asset);
        dataMapRequest.getDataMap().putInt(HIGH_TEMPERATURE_KEY, high);
        dataMapRequest.getDataMap().putInt(LOW_TEMPERATURE_KEY, low);

        PutDataRequest dataRequest = dataMapRequest.asPutDataRequest();
        dataRequest.setUrgent();

        Wearable.DataApi.putDataItem(mGoogleApiClient, dataRequest)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                        Log.d(TAG, "onResult: Sending image was successful: "
                        + dataItemResult.getStatus().isSuccess());
                    }
                });
    }

    private void getWeatherDataForWear(Context context){

        Uri todaysWeatherUri = WeatherContract.WeatherEntry
                .buildWeatherUriWithDate(SunshineDateUtils.normalizeDate(System.currentTimeMillis()));

        Cursor todayWeatherCursor = context.getContentResolver().query(
                todaysWeatherUri,
                WEATHER_WEAR_PROJECTION,
                null,
                null,
                null);

        if (todayWeatherCursor.moveToFirst()) {

            /* Weather ID as returned by API, used to identify the icon to be used */
            int weatherId = todayWeatherCursor.getInt(INDEX_WEATHER_ID);

            Resources resources = context.getResources();
            int smallArtResourceId = SunshineWeatherUtils
                    .getSmallArtResourceIdForWeatherCondition(weatherId);

            Bitmap smallIcon = BitmapFactory.decodeResource(
                    resources,
                    smallArtResourceId);


            int high = ((Double)todayWeatherCursor.getDouble(INDEX_MAX_TEMP)).intValue();
            int low = ((Double)todayWeatherCursor.getDouble(INDEX_MIN_TEMP)).intValue();

            Asset weatherIcon = bitmapToAsset(smallIcon);

            sendWeatherToWear(high, low, weatherIcon);
        }

        /* Always close your cursor when you're done with it to avoid wasting resources. */
        todayWeatherCursor.close();

    }


    private Asset bitmapToAsset(Bitmap bitmap){
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

}
