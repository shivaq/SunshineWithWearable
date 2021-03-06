/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFaceService extends CanvasWatchFaceService {

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final String TAG = SunshineWatchFaceService.class.getSimpleName();

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final String HIGH_TEMPERATURE_KEY = "high_temp";
    private static final String LOW_TEMPERATURE_KEY = "low_temp";
    private static final String WEATHER_IMAGE_KEY = "img_weather";
    private static final String PASS_WEATHER_DATA_PATH = "/pass_weather_data";
    private static final String START_SYNC_PATH = "/start_sync";


    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFaceService.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    /**
     * Engine
     ****************************/
    private class Engine extends CanvasWatchFaceService.Engine implements
            MessageApi.MessageListener,
            CapabilityApi.CapabilityListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            DataApi.DataListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        GoogleApiClient mGoogleApiClient;
        boolean mAmbient;

        // Components to be drawn
        Calendar mCalendar;
        Date mDate;
        String mDateString;
        String mHighTemp = getString(R.string.no_weather_data);
        String mLowTemp = getString(R.string.no_weather_data);
        Bitmap mWeatherIcon = BitmapFactory.decodeResource(getBaseContext().getResources(),
                R.drawable.close_button);


        java.text.DateFormat mTimeFormat;

        float mXOffsetForWeatherIcon;
        float mXOffsetForTime;
        float mXOffsetForHigh;
        float mXOffsetForLow;

        float mYOffsetForWeatherIcon;
        float mYOffsetForTime;
        float mYOffsetForDate;
        float mYOffsetForTemperature;

        float mDateTextSize;
        float mTemperatureTextSize;

        Paint mBackgroundPaint;
        Paint mIconPaint;
        Paint mTimePaint;
        Paint mSecondPaint;
        Paint mDatePaint;
        Paint mLinePaint;
        Paint mHighTempPaint;
        Paint mLowTempPaint;


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFaceService.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();


            Log.d(TAG, "onCreate: isConnected" + mGoogleApiClient.isConnected());

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            mWeatherIcon = BitmapFactory.decodeResource(getBaseContext().getResources(),
                    R.drawable.ic_launcher);

            Resources resources = SunshineWatchFaceService.this.getResources();
            mYOffsetForTime = resources.getDimension(R.dimen.digital_y_offset);
            mYOffsetForDate = resources.getDimension(R.dimen.digital_y_offset_for_date);
            mYOffsetForTemperature = resources.getDimension(R.dimen.digital_y_offset_for_temperature);
            mYOffsetForWeatherIcon = resources.getDimension(R.dimen.digital_y_offset_for_weather_icon);

            mDateTextSize = resources.getDimension(R.dimen.text_date);
            mTemperatureTextSize = resources.getDimension(R.dimen.text_temperature);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.colorPrimary));

            mIconPaint = new Paint();

            mTimePaint = new Paint();
            mTimePaint = createTextPaint(resources.getColor(R.color.white));
            mSecondPaint = new Paint();
            mSecondPaint = createTextPaint(resources.getColor(R.color.colorPrimaryLight));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.colorPrimaryLight));

            mLinePaint = new Paint();
            mLinePaint = createTextPaint(resources.getColor(R.color.colorPrimaryLight));
            mHighTempPaint = new Paint();
            mHighTempPaint = createTextPaint(resources.getColor(R.color.white));

            mLowTempPaint = new Paint();
            mLowTempPaint = createTextPaint(resources.getColor(R.color.colorPrimaryLight));


            mCalendar = Calendar.getInstance();
            mDate = new Date();
            mDateString = (android.text.format.DateFormat.format("EEE, MMM dd yyyy", mCalendar)).toString();
            mTimeFormat = android.text.format.DateFormat.getTimeFormat(getApplicationContext());

            if (mHighTemp.equals(getString(R.string.no_weather_data))) {
                askPhoneToSyncData();
            }
            Log.d(TAG, "onCreate: mHighTemp is " + mHighTemp);
        }


        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            mGoogleApiClient.disconnect();
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            Log.d(TAG, "onVisibilityChanged: ");
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            Log.d(TAG, "registerReceiver: ");
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            Log.d(TAG, "onApplyWindowInsets: ");
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffsetForTime = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            mXOffsetForHigh = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_for_high_round : R.dimen.digital_x_offset_for_high);
            mXOffsetForLow = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_for_low_round : R.dimen.digital_x_offset_for_low);

            mXOffsetForWeatherIcon = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_for_image_round : R.dimen.digital_x_offset_for_image);


            mTimePaint.setTextSize(textSize);
            mDatePaint.setTextSize(mDateTextSize);
            mHighTempPaint.setTextSize(mTemperatureTextSize);
            mLowTempPaint.setTextSize(mTemperatureTextSize);
            mSecondPaint.setTextSize(textSize / 2);

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            Log.d(TAG, "onAmbientModeChanged: ");
            super.onAmbientModeChanged(inAmbientMode);

            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mHighTempPaint.setAntiAlias(!inAmbientMode);
                    mLowTempPaint.setAntiAlias(!inAmbientMode);
                    mSecondPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Log.d(TAG, "onTapCommand: ");
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }


        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            int width = bounds.width();
            int height = bounds.height();
            // centerX is 240.0 for 480 x 480
            float centerX = width / 2f;
            float centerY = height / 2f;

//            float xOffsetForTime = centerX/2f;
            float xOffsetForTime;
            float yOffset = centerY + 10;


            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                // Icon
                Bitmap weatherIcon = mWeatherIcon;
                canvas.drawBitmap(weatherIcon, centerX / 3, centerY / 5, mIconPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            // HH:MM
            String timeText = mTimeFormat.format(mDate);
            xOffsetForTime = centerX - mTimePaint.measureText(timeText) / 2;
            canvas.drawText(timeText, xOffsetForTime, yOffset, mTimePaint);

            if(!isInAmbientMode()){
                // ss
                canvas.drawText(formatTwoDigitNumber(mCalendar.get(Calendar.SECOND)),
                        xOffsetForTime + mTimePaint.measureText(timeText),
                        yOffset, mSecondPaint);
            }

            // date
            Paint.FontMetrics fm = mDatePaint.getFontMetrics();
            yOffset += fm.descent - fm.ascent + centerY/5;
            canvas.drawText(mDateString,
                    centerX - mDatePaint.measureText(mDateString)/2, yOffset, mDatePaint);

            // line
            yOffset += 15;
            canvas.drawLine(centerX - 130, yOffset, centerX + 130, yOffset, mLinePaint);

            //high
            fm = mHighTempPaint.getFontMetrics();
            yOffset += fm.descent - fm.ascent;
            String highTemp = mHighTemp;
            canvas.drawText(highTemp, centerX - mHighTempPaint.measureText(highTemp),
                    yOffset, mHighTempPaint);

            //low
            String lowTemp = mLowTemp;
            canvas.drawText(lowTemp, centerX,
                    yOffset, mLowTempPaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onMessageReceived(MessageEvent messageEvent) {

        }

        @Override
        public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
            Log.d(TAG, "onCapabilityChanged: ");
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(TAG, "onConnected: isconnected " + mGoogleApiClient.isConnected());
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }


        @Override
        public void onConnectionSuspended(int i) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Log.d(TAG, "onConnectionSuspended: ");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(TAG, "onConnectionFailed: ");
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d(TAG, "onDataChanged: " + dataEventBuffer);

            for (DataEvent event : dataEventBuffer) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    String path = event.getDataItem().getUri().getPath();

                    if (PASS_WEATHER_DATA_PATH.equals(path)) {
                        DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());

                        Asset weatherIcon = dataMapItem.getDataMap()
                                .getAsset(WEATHER_IMAGE_KEY);
                        new LoadBitmapAsyncTask().execute(weatherIcon);

                        mHighTemp = dataMapItem.getDataMap()
                                .getString(HIGH_TEMPERATURE_KEY);
                        mLowTemp = dataMapItem.getDataMap()
                                .getString(LOW_TEMPERATURE_KEY);
                    }
                }
            }
        }

        private void askPhoneToSyncData() {
            new AskPhoneAsyncTask().execute();
        }

        private class AskPhoneAsyncTask extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... args) {
                Collection<String> nodes = getNodes();
                for (String node : nodes) {
                    sendStartSyncMessage(node);
                }
                return null;
            }
        }


        private Collection<String> getNodes() {
            HashSet<String> results = new HashSet<>();

            NodeApi.GetConnectedNodesResult nodes =
                    Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

            for (Node node : nodes.getNodes()) {
                results.add(node.getId());
            }
            return results;
        }


        private void sendStartSyncMessage(String node) {

            Wearable.MessageApi.sendMessage(
                    mGoogleApiClient, node, START_SYNC_PATH,
                    new byte[0]).setResultCallback(
                    new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                            if (!sendMessageResult.getStatus().isSuccess()) {
                                Log.e(TAG, "Failed to send message with status code: "
                                        + sendMessageResult.getStatus().getStatusCode());
                            }
                        }
                    }
            );
        }


        private class LoadBitmapAsyncTask extends AsyncTask<Asset, Void, Bitmap> {

            @Override
            protected Bitmap doInBackground(Asset... params) {

                if (params.length > 0) {

                    Asset asset = params[0];
                    InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                            mGoogleApiClient, asset).await().getInputStream();

                    if (assetInputStream == null) {
                        Log.w(TAG, "Requested an unknown Asset.");
                        return null;
                    }
                    return BitmapFactory.decodeStream(assetInputStream);

                } else {
                    Log.e(TAG, "Asset must be non-null");
                    return null;
                }
            }


            @Override
            protected void onPostExecute(Bitmap bitmap) {

                if (bitmap != null) {
                    mWeatherIcon = bitmap;
                }
            }
        }

    }


}
