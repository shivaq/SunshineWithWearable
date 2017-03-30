package com.example.android.sunshine.sync;

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
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MyService extends WearableListenerService implements
        GoogleApiClient.ConnectionCallbacks,
        CapabilityApi.CapabilityListener{


    private static final String TAG = MyService.class.getSimpleName();

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

    private static boolean isConnected;
    private GoogleApiClient mGoogleApiClient;

    public static boolean isConnected() {
        return isConnected;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isConnected = false;

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived: " + messageEvent);
        super.onMessageReceived(messageEvent);
        if (messageEvent.getPath().equals(PASS_WEATHER_DATA_PATH)) {

            // TODO: pass data to wear

        }
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        isConnected = true;
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }



    public static void sendWeatherToWear(Context context){

        getWeatherDataForWear(context);
    }

    private static void sendWeatherToWear(int high, int low, Asset asset){

        PutDataMapRequest dataMapRequest = PutDataMapRequest.create(PASS_WEATHER_DATA_PATH);

        dataMapRequest.getDataMap().putAsset(WEATHER_IMAGE_KEY, asset);
        dataMapRequest.getDataMap().putInt(HIGH_TEMPERATURE_KEY, high);
        dataMapRequest.getDataMap().putInt(LOW_TEMPERATURE_KEY, low);

        PutDataRequest dataRequest = dataMapRequest.asPutDataRequest();
        dataRequest.setUrgent();

        MyService myService = new MyService();
        Wearable.DataApi.putDataItem(myService.mGoogleApiClient, dataRequest)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                        Log.d(TAG, "onResult: Sending image was successful: "
                        + dataItemResult.getStatus().isSuccess());
                    }
                });
    }

    private static void getWeatherDataForWear(Context context){

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


    private static Asset bitmapToAsset(Bitmap bitmap){
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
