package com.example.android.sunshine.app.widget;

import android.annotation.TargetApi;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.example.android.sunshine.app.R;
import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;

import java.util.concurrent.ExecutionException;

/**
 * Created by pedro on 26/07/16.
 */

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class DetailWidgetRemoteViewsService extends RemoteViewsService{
    public final String LOG_TAG = DetailWidgetRemoteViewsService.class.getSimpleName();
    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };

    //These indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_WEATHER_DATE = 1;
    private static final int INDEX_WEATHER_CONDITION_ID = 2;
    private static final int INDEX_WEATHER_DESC = 3;
    private static final int INDEX_MAX_TEMP = 4;
    private static final int INDEX_MIN_TEMP = 5;

    @Override
    public RemoteViewsFactory onGetViewFactory(final Intent intent) {
        return new RemoteViewsFactory() {
            private Cursor data = null;

            @Override
            public void onCreate() {
                //nothing to do
            }

            @Override
            public void onDataSetChanged() {
                if(data != null){
                    data.close();
                }

                //We need to clear and restore the calling identity so that calls
                //use our process and permission
                final long identityToken = Binder.clearCallingIdentity();
                String location = Utility.getPreferredLocation(DetailWidgetRemoteViewsService.this);
                Uri weatherForLocationUri = WeatherContract.WeatherEntry
                        .buildWeatherLocationWithStartDate(location, System.currentTimeMillis());
                data = getContentResolver().query(weatherForLocationUri,
                        FORECAST_COLUMNS,
                        null,
                        null,
                        WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
                Binder.restoreCallingIdentity(identityToken);
            }

            @Override
            public void onDestroy() {
                if(data != null){
                    data.close();
                    data = null;
                }
            }

            @Override
            public int getCount() {
                return data == null ? 0 : data.getCount();
            }

            @Override
            public RemoteViews getViewAt(int position) {
                if (position == AdapterView.INVALID_POSITION ||
                        data == null || !data.moveToPosition(position))
                    return null;

                RemoteViews views = new RemoteViews(getPackageName(),
                        R.layout.widget_detail_list_item);
                int weatherId = data.getInt(INDEX_WEATHER_CONDITION_ID);
                int weatherArtResourceId = Utility.getIconResourceForWeatherCondition(weatherId);
                Bitmap weatherArtImage = null;
                if (!Utility.usingLocalGraphics(DetailWidgetRemoteViewsService.this)){
                    String weatherArtResourceUrl = Utility.getArtUrlForWeatherCondition(
                            DetailWidgetRemoteViewsService.this, weatherId
                    );
                    try {
                        weatherArtImage = Glide.with(DetailWidgetRemoteViewsService.this)
                                .load(weatherArtResourceUrl)
                                .asBitmap()
                                .error(weatherArtResourceId)
                                .into(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).get();
                    } catch (InterruptedException | ExecutionException e){
                        Log.e(LOG_TAG, "Error retrieving icon from " + weatherArtResourceUrl, e);
                    }
                }
                String description = data.getString(INDEX_WEATHER_DESC);
                long dateInMillis = data.getLong(INDEX_WEATHER_DATE);
                String formattedDate = Utility.getFriendlyDayString(
                        DetailWidgetRemoteViewsService.this, dateInMillis, false);
                double maxTemp = data.getDouble(INDEX_MAX_TEMP);
                double minTemp = data.getDouble(INDEX_MIN_TEMP);
                String formattedMaxTemperature = Utility.formatTemperature(
                        DetailWidgetRemoteViewsService.this, maxTemp);
                String formattedMinTemperature = Utility.formatTemperature(
                        DetailWidgetRemoteViewsService.this, minTemp);
                if (weatherArtImage != null){
                    views.setImageViewBitmap(R.id.widget_icon, weatherArtImage);
                } else {
                    views.setImageViewResource(R.id.widget_icon, weatherArtResourceId);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1){
                    setRemoteContentDescription(views, description);
                }

                views.setTextViewText(R.id.widget_date, formattedDate);
                views.setTextViewText(R.id.widget_description, description);
                views.setTextViewText(R.id.widget_high_temperature, formattedMaxTemperature);
                views.setTextViewText(R.id.widget_low_temperature, formattedMinTemperature);

                final Intent fillInIntent = new Intent();
                String locationSetting = Utility
                        .getPreferredLocation(DetailWidgetRemoteViewsService.this);
                Uri weatherUri = WeatherContract.WeatherEntry
                        .buildWeatherLocationWithDate(locationSetting, dateInMillis);
                fillInIntent.setData(weatherUri);
                views.setOnClickFillInIntent(R.id.widget_list_item, fillInIntent);
                return views;
            }

            @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
            private void setRemoteContentDescription(RemoteViews views, String description){
                views.setContentDescription(R.id.widget_icon, description);
            }

            @Override
            public RemoteViews getLoadingView() {
                return new RemoteViews(getPackageName(), R.layout.widget_detail_list_item);
            }

            @Override
            public int getViewTypeCount() {
                return 1;
            }

            @Override
            public long getItemId(int position) {
                if (data.moveToPosition(position))
                    return data.getLong(INDEX_WEATHER_ID);
                return position;
            }

            @Override
            public boolean hasStableIds() {
                return true;
            }
        };
    }
}