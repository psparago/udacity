package org.sparago.udacity.sunshine.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.sparago.udacity.sunshine.app.data.WeatherContract;
import org.sparago.udacity.sunshine.app.data.WeatherContract.LocationEntry;
import org.sparago.udacity.sunshine.app.data.WeatherContract.WeatherEntry;
import org.sparago.udacity.sunshine.app.models.weather.Day;
import org.sparago.udacity.sunshine.app.models.weather.WeatherLocation;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class WeatherFetcher {
	private static final String LOG_TAG = WeatherFetcher.class.getSimpleName();

	public static void fetchWeather(Context context, String location) {

		final String baseUrl = "http://api.openweathermap.org/data/2.5/forecast/daily";
		final String queryParam = "q";
		final String modeParam = "mode";
		final String modeValue = "json";
		final String unitsParam = "units";
		final String unitsValue = "metric";
		final String daysParam = "cnt";
		final int daysValue = 14;

		if (location == null || location.length() == 0) {
			return;
		}

		WeatherLocation weatherLocation = null;

		// These two need to be declared outside the try/catch
		// so that they can be closed in the finally block.
		HttpURLConnection urlConnection = null;
		BufferedReader reader = null;

		try {
			// Will contain the raw JSON response as a string.
			String forecastJsonStr = null;

			URL url = new URL(Uri
					.parse(baseUrl)
					.buildUpon()
					.appendQueryParameter(queryParam, location)
					.appendQueryParameter(modeParam, modeValue)
					.appendQueryParameter(unitsParam, unitsValue)
					.appendQueryParameter(daysParam,
							Integer.valueOf(daysValue).toString()).build()
					.toString());

			// Create the request to OpenWeatherMap, and open the connection
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setRequestMethod("GET");
			urlConnection.connect();

			// Read the input stream into a String
			InputStream inputStream = urlConnection.getInputStream();
			StringBuffer buffer = new StringBuffer();
			if (inputStream == null) {
				// Nothing to do.
				forecastJsonStr = null;
			}
			reader = new BufferedReader(new InputStreamReader(inputStream));

			String line;
			while ((line = reader.readLine()) != null) {
				// Since it's JSON, adding a newline isn't necessary (it
				// won't affect parsing)
				// But it does make debugging a *lot* easier if you print
				// out the completed
				// buffer for debugging.
				buffer.append(line + "\n");
			}

			if (buffer.length() > 0) {
				forecastJsonStr = buffer.toString();
				weatherLocation = new WeatherLocation(location);
				weatherLocation.parseJson(forecastJsonStr);
			}

			long locationId = addLocation(context, weatherLocation);
			addDays(context, weatherLocation, locationId);
			removeOldWeatherData(context);

		} catch (Exception e) {
			Log.e(LOG_TAG, "Error getting weather JSON", e);
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
			if (reader != null) {
				try {
					reader.close();
				} catch (final IOException e) {
					Log.e(LOG_TAG, "Error closing stream", e);
				}
			}
		}

	}

	private static long addLocation(Context context, WeatherLocation location) {
		Log.v(LOG_TAG, "adding location: " + location.getLocation());
		long locationRowId = 0;
		Cursor cursor = null;
		try {
			cursor = context.getContentResolver().query(
					LocationEntry.CONTENT_URI,
					new String[] { LocationEntry._ID },
					LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
					new String[] { location.getLocation() }, null // sort order
					);
			if (cursor.moveToFirst()) {
				locationRowId = cursor.getInt(0);
				Log.v(LOG_TAG, "found location: " + location.getLocation()
						+ " in database, id " + locationRowId);
			} else {
				ContentValues locationValues = new ContentValues();
				locationValues.put(LocationEntry.COLUMN_LOCATION_SETTING,
						location.getLocation());
				locationValues.put(LocationEntry.COLUMN_CITY_NAME, location
						.getCity().getName());
				locationValues.put(LocationEntry.COLUMN_COORD_LAT, location
						.getCity().getCoord().getLatitude());
				locationValues.put(LocationEntry.COLUMN_COORD_LONG, location
						.getCity().getCoord().getLongitude());

				Uri locationInsertUri = context.getContentResolver().insert(
						LocationEntry.CONTENT_URI, locationValues);
				locationRowId = ContentUris.parseId(locationInsertUri);
				Log.v(LOG_TAG, "inserted location: " + location.getLocation()
						+ " in database, id " + locationRowId);
			}
			return locationRowId;
		} finally {
			if (cursor != null)
				cursor.close();
		}
	}

	private static void addDays(Context context, WeatherLocation location,
			long locationId) {
		Log.v(LOG_TAG,
				"adding weather days for location " + location.getLocation());

		int rowsDeleted = context.getContentResolver().delete(
				WeatherContract.WeatherEntry.CONTENT_URI,
				WeatherContract.WeatherEntry.COLUMN_LOC_KEY + " = ?",
				new String[] { Long.toString(locationId) });
		Log.v(LOG_TAG, "deleted " + rowsDeleted + " weather days for location "
				+ location.getLocation());

		List<ContentValues> cvs = new ArrayList<ContentValues>();
		List<Day> days = location.getDays();
		for (Day day : days) {
			ContentValues weatherValues = new ContentValues();
			weatherValues.put(WeatherEntry.COLUMN_LOC_KEY, locationId);
			weatherValues.put(WeatherEntry.COLUMN_DATETEXT,
					WeatherContract.getDbDateString(day.getDate()));
			weatherValues.put(WeatherEntry.COLUMN_HUMIDITY, day.getHumidity());
			weatherValues.put(WeatherEntry.COLUMN_PRESSURE, day.getPressure());
			weatherValues.put(WeatherEntry.COLUMN_WIND_SPEED, day.getSpeed());
			weatherValues.put(WeatherEntry.COLUMN_DEGREES, day.getDegrees());
			weatherValues.put(WeatherEntry.COLUMN_MAX_TEMP, day.getTemps()
					.getHigh());
			weatherValues.put(WeatherEntry.COLUMN_MIN_TEMP, day.getTemps()
					.getLow());
			weatherValues.put(WeatherEntry.COLUMN_SHORT_DESC, day.getWeather()
					.getMain());
			weatherValues.put(WeatherEntry.COLUMN_WEATHER_ID, day.getWeather()
					.getId());
			cvs.add(weatherValues);
		}
		context.getContentResolver().bulkInsert(
				WeatherContract.WeatherEntry.CONTENT_URI,
				cvs.toArray(new ContentValues[cvs.size()]));
	}

	private static void removeOldWeatherData(Context context) {
		String today = WeatherContract.getDbDateString(new Date());
		Log.v(LOG_TAG, "Removing weather entries older than: " + today);

		int rows = context.getContentResolver().delete(
				WeatherEntry.CONTENT_URI,
				WeatherEntry.COLUMN_DATETEXT + " < ?", new String[] { today });
		
		Log.v(LOG_TAG, "Removed: " + rows + " weather entries");
	}

}
