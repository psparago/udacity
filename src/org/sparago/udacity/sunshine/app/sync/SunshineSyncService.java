package org.sparago.udacity.sunshine.app.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class SunshineSyncService extends Service {
	private static final String LOG_TAG = SunshineSyncService.class.getSimpleName();
	private static final Object sSyncAdapterLock = new Object();
	private static SunshineSyncAdapter sSunshineSyncAdapter = null;

	@Override
	public void onCreate() {
		Log.d(LOG_TAG, "onCreate - SunshineSyncService");
		if (sSunshineSyncAdapter == null) {
			synchronized(sSyncAdapterLock) {
				if (sSunshineSyncAdapter == null) {
					sSunshineSyncAdapter = new SunshineSyncAdapter(getApplicationContext(), true);
				}
			}
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return sSunshineSyncAdapter.getSyncAdapterBinder();
	}

}
