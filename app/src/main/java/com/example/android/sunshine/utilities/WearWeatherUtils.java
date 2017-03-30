package com.example.android.sunshine.utilities;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.example.android.sunshine.data.WeatherContract;


/**
 * Created by Yasuaki on 2017/03/30.
 */
public class WearWeatherUtils {

    public static final String[] WEATHER_WEAR_PROJECTION = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
    };

    public static final int INDEX_WEATHER_ID = 0;
    public static final int INDEX_MAX_TEMP = 1;
    public static final int INDEX_MIN_TEMP = 2;




    public static void passWeatherToWear(Context context){

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
            double high = todayWeatherCursor.getDouble(INDEX_MAX_TEMP);
            double low = todayWeatherCursor.getDouble(INDEX_MIN_TEMP);

            Resources resources = context.getResources();
            int smallArtResourceId = SunshineWeatherUtils
                    .getSmallArtResourceIdForWeatherCondition(weatherId);

            Bitmap smallIcon = BitmapFactory.decodeResource(
                    resources,
                    smallArtResourceId);

        }

        /* Always close your cursor when you're done with it to avoid wasting resources. */
        todayWeatherCursor.close();
    }
}
