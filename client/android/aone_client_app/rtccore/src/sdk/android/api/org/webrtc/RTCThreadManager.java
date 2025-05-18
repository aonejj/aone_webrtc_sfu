/*
 *  author : kimi
 */

package org.webrtc;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RTCThreadManager {
	private static final String TAG = "RTCThreadManager";
	private static final boolean DEBUG = true;
	private static final ExecutorService executor = Executors.newSingleThreadExecutor();
	private static boolean isInitialized = false;

	public static void  initThreadManager() {
		if(isInitialized == false) {
			isInitialized = true;
			executor.execute(() -> {
				PeerConnectionFactory.initThreadManager();
			});
		}
	}

	public static void initializePeerConnectionFactory(Context appContext, String fieldTrials) {
		executor.execute(() -> {
			if(DEBUG) Log.d(TAG, "_check_thread_ initializePeerConnectionFactory PeerConnectionClient PeerConnectionFactory.initialize+++ executor thread id " + Thread.currentThread().getId());
			PeerConnectionFactory.initialize(
					PeerConnectionFactory.InitializationOptions.builder(appContext)
							.setFieldTrials(fieldTrials)
							.setEnableInternalTracer(true)
							.createInitializationOptions());
			if(DEBUG) Log.d(TAG, "_check_thread_ initializePeerConnectionFactory PeerConnectionClient PeerConnectionFactory.initialize---");
		});
	}

	public static void startTracingCapture() {
		executor.execute(() -> {
			PeerConnectionFactory.startInternalTracingCapture(
					Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator
							+ "webrtc-trace.txt");
		});
	}

	public static void deinitializePeerConnectionFactory() {
		if(isInitialized == true) {
			executor.execute(() -> {
				if (DEBUG) Log.d(TAG, "_check_thread_ deinitializePeerConnectionFactory +++");
				PeerConnectionFactory.stopInternalTracingCapture();
				PeerConnectionFactory.shutdownInternalTracer();
				if (DEBUG) Log.d(TAG, "_check_thread_ deinitializePeerConnectionFactory ---");
			});
			isInitialized = false;
		}
	}

	public static boolean isInitialized() {
		return isInitialized;
	}
}
