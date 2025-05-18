package com.aone.sfuapp;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

public class IntroActivity extends Activity {
    private static final String TAG = "IntroActivity";
    private static final int PERMISSION_REQUEST = 1;

    private static final String PREF_NAME = "aone_prefs";
    private static final String KEY_IP = "ip_address";
    private static final String KEY_PORT = "port_number";

    private SharedPreferences sharedPref;
    private SharedPreferences ip_prot_sharedPref;
    private String keyprefResolution;
    private String keyprefFps;
    private String keyprefVideoBitrateType;
    private String keyprefVideoBitrateValue;
    private String keyprefAudioBitrateType;
    private String keyprefAudioBitrateValue;

    private EditText ipEditText;
    private EditText portEditText;
    private Button media_server_btn;

    TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            checkInputFields();
        }

        @Override
        public void afterTextChanged(Editable s) { }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_intro);

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        keyprefResolution = getString(R.string.pref_resolution_key);
        keyprefFps = getString(R.string.pref_fps_key);
        keyprefVideoBitrateType = getString(R.string.pref_maxvideobitrate_key);
        keyprefVideoBitrateValue = getString(R.string.pref_maxvideobitratevalue_key);
        keyprefAudioBitrateType = getString(R.string.pref_startaudiobitrate_key);
        keyprefAudioBitrateValue = getString(R.string.pref_startaudiobitratevalue_key);

        ipEditText = findViewById(R.id.edit_ip);
        portEditText = findViewById(R.id.edit_port);

        boolean is_ip_input = true;
        ip_prot_sharedPref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if(ip_prot_sharedPref.contains(KEY_IP) && ip_prot_sharedPref.contains(KEY_PORT)) {
            is_ip_input = false;
            String savedIp = ip_prot_sharedPref.getString(KEY_IP, "");
            String savedPort = ip_prot_sharedPref.getString(KEY_PORT, "");
            ipEditText.setText(savedIp);
            portEditText.setText(savedPort);
        }

        media_server_btn = findViewById(R.id.btn_media_server);
        media_server_btn.setOnClickListener(mediaServerClientListener);
        if(is_ip_input) {
            media_server_btn.setEnabled(false);
            media_server_btn.setAlpha(0.4f);
            ipEditText.addTextChangedListener(textWatcher);
            portEditText.addTextChangedListener(textWatcher);
        }

        requestPermissions();
    }

    private void checkInputFields() {
        String ip = ipEditText.getText().toString().trim();
        String port = portEditText.getText().toString().trim();

        boolean enable = !ip.isEmpty() && !port.isEmpty();
        if(enable) {
            media_server_btn.setEnabled(enable);
            media_server_btn.setAlpha(1.0f);
        }
    }

    private void onPermissionsGranted() {
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Dynamic permissions are not required before Android M.
            onPermissionsGranted();
            return;
        }

        String[] missingPermissions = getMissingPermissions();
        if (missingPermissions.length != 0) {
            requestPermissions(missingPermissions, PERMISSION_REQUEST);
        } else {
            onPermissionsGranted();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private String[] getMissingPermissions() {
        List<String> missing = new ArrayList<>();

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.CAMERA);
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        return missing.toArray(new String[0]);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST) {
            String[] missingPermissions = getMissingPermissions();
            if (missingPermissions.length != 0) {
                for(int i = 0; i < missingPermissions.length; i++) {
                    Log.d(TAG, "----------> missing "+ missingPermissions[i]);
                }

                // User didn't grant all the permissions. Warn that the application might not work
                // correctly.
                new AlertDialog.Builder(this)
                        .setMessage(R.string.missing_permissions_try_again)
                        .setPositiveButton(R.string.yes,
                                (dialog, id) -> {
                                    // User wants to try giving the permissions again.
                                    dialog.cancel();
                                    requestPermissions();
                                })
                        .setNegativeButton(R.string.no,
                                (dialog, id) -> {
                                    // User doesn't want to give the permissions.
                                    dialog.cancel();
                                    onPermissionsGranted();
                                })
                        .show();
            } else {
                // All permissions granted.
                onPermissionsGranted();
            }
        }
    }

    private final OnClickListener mediaServerClientListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            String ip = ipEditText.getText().toString();
            String port = portEditText.getText().toString();

            SharedPreferences.Editor editor = ip_prot_sharedPref.edit();
            editor.putString(KEY_IP, ip);
            editor.putString(KEY_PORT, port);
            editor.apply();

            Intent intent = new Intent(getApplicationContext(), RTCClientActivity.class);
            intent.putExtra(RTCPreference.EXTRA_SIGNALING_SERVER_IP, ip);
            intent.putExtra(RTCPreference.EXTRA_SIGNALING_SERVER_PORT, port);
            startRTCActivity(intent);
        }
    };

    private void startRTCActivity(Intent intent) {
        // Video call enabled flag.
        boolean videoCallEnabled = sharedPrefGetBoolean(R.string.pref_videocall_key,
                RTCPreference.EXTRA_VIDEO_CALL, R.string.pref_videocall_default, false);

        // Use screencapture option.
        boolean useScreencapture = sharedPrefGetBoolean(R.string.pref_screencapture_key,
                RTCPreference.EXTRA_SCREENCAPTURE, R.string.pref_screencapture_default, false);

        // Use Camera2 option.
        boolean useCamera2 = sharedPrefGetBoolean(R.string.pref_camera2_key, RTCPreference.EXTRA_CAMERA2,
                R.string.pref_camera2_default, false);

        // Get default codecs.
        String videoCodec = sharedPrefGetString(R.string.pref_videocodec_key,
                RTCPreference.EXTRA_VIDEOCODEC, R.string.pref_videocodec_default, false);
        String audioCodec = sharedPrefGetString(R.string.pref_audiocodec_key,
                RTCPreference.EXTRA_AUDIOCODEC, R.string.pref_audiocodec_default, false);

        // Check HW codec flag.
        boolean hwCodec = sharedPrefGetBoolean(R.string.pref_hwcodec_key,
                RTCPreference.EXTRA_HWCODEC_ENABLED, R.string.pref_hwcodec_default, false);

        // Check Capture to texture.
        boolean captureToTexture = sharedPrefGetBoolean(R.string.pref_capturetotexture_key,
                RTCPreference.EXTRA_CAPTURETOTEXTURE_ENABLED, R.string.pref_capturetotexture_default,
                false);

        // Check FlexFEC.
        boolean flexfecEnabled = sharedPrefGetBoolean(R.string.pref_flexfec_key,
                RTCPreference.EXTRA_FLEXFEC_ENABLED, R.string.pref_flexfec_default, false);

        // Check Disable Audio Processing flag.
        boolean noAudioProcessing = sharedPrefGetBoolean(R.string.pref_noaudioprocessing_key,
                RTCPreference.EXTRA_NOAUDIOPROCESSING_ENABLED, R.string.pref_noaudioprocessing_default,
                false);

        boolean aecDump = sharedPrefGetBoolean(R.string.pref_aecdump_key,
                RTCPreference.EXTRA_AECDUMP_ENABLED, R.string.pref_aecdump_default, false);

        boolean saveInputAudioToFile =
                sharedPrefGetBoolean(R.string.pref_enable_save_input_audio_to_file_key,
                        RTCPreference.EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED,
                        R.string.pref_enable_save_input_audio_to_file_default, false);

        // Check OpenSL ES enabled flag.
        boolean useOpenSLES = sharedPrefGetBoolean(R.string.pref_opensles_key,
                RTCPreference.EXTRA_OPENSLES_ENABLED, R.string.pref_opensles_default, false);

        // Check Disable built-in AEC flag.
        boolean disableBuiltInAEC = sharedPrefGetBoolean(R.string.pref_disable_built_in_aec_key,
                RTCPreference.EXTRA_DISABLE_BUILT_IN_AEC, R.string.pref_disable_built_in_aec_default,
                false);

        // Check Disable built-in AGC flag.
        boolean disableBuiltInAGC = sharedPrefGetBoolean(R.string.pref_disable_built_in_agc_key,
                RTCPreference.EXTRA_DISABLE_BUILT_IN_AGC, R.string.pref_disable_built_in_agc_default,
                false);

        // Check Disable built-in NS flag.
        boolean disableBuiltInNS = sharedPrefGetBoolean(R.string.pref_disable_built_in_ns_key,
                RTCPreference.EXTRA_DISABLE_BUILT_IN_NS, R.string.pref_disable_built_in_ns_default,
                false);

        // Check Disable gain control
        boolean disableWebRtcAGCAndHPF = sharedPrefGetBoolean(
                R.string.pref_disable_webrtc_agc_and_hpf_key, RTCPreference.EXTRA_DISABLE_WEBRTC_AGC_AND_HPF,
                R.string.pref_disable_webrtc_agc_and_hpf_key, false);

        // Get video resolution from settings.
        int videoWidth = 0;
        int videoHeight = 0;

        if (videoWidth == 0 && videoHeight == 0) {
            String resolution =
                    sharedPref.getString(keyprefResolution, getString(R.string.pref_resolution_default));
            String[] dimensions = resolution.split("[ x]+");
            if (dimensions.length == 2) {
                try {
                    videoWidth = Integer.parseInt(dimensions[0]);
                    videoHeight = Integer.parseInt(dimensions[1]);
                } catch (NumberFormatException e) {
                    videoWidth = 0;
                    videoHeight = 0;
                    Log.e(TAG, "Wrong video resolution setting: " + resolution);
                }
            }
        }

        // Get camera fps from settings.
        int cameraFps = 0;

        if (cameraFps == 0) {
            String fps = sharedPref.getString(keyprefFps, getString(R.string.pref_fps_default));
            String[] fpsValues = fps.split("[ x]+");
            if (fpsValues.length == 2) {
                try {
                    cameraFps = Integer.parseInt(fpsValues[0]);
                } catch (NumberFormatException e) {
                    cameraFps = 0;
                    Log.e(TAG, "Wrong camera fps setting: " + fps);
                }
            }
        }

        // Check capture quality slider flag.
        boolean captureQualitySlider = sharedPrefGetBoolean(R.string.pref_capturequalityslider_key,
                RTCPreference.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED,
                R.string.pref_capturequalityslider_default, false);

        // Get video and audio start bitrate.
        int videoStartBitrate = 0;

        if (videoStartBitrate == 0) {
            String bitrateTypeDefault = getString(R.string.pref_maxvideobitrate_default);
            String bitrateType = sharedPref.getString(keyprefVideoBitrateType, bitrateTypeDefault);
            if (!bitrateType.equals(bitrateTypeDefault)) {
                String bitrateValue = sharedPref.getString(
                        keyprefVideoBitrateValue, getString(R.string.pref_maxvideobitratevalue_default));
                videoStartBitrate = Integer.parseInt(bitrateValue);
            }
        }

        int audioStartBitrate = 0;

        if (audioStartBitrate == 0) {
            String bitrateTypeDefault = getString(R.string.pref_startaudiobitrate_default);
            String bitrateType = sharedPref.getString(keyprefAudioBitrateType, bitrateTypeDefault);
            if (!bitrateType.equals(bitrateTypeDefault)) {
                String bitrateValue = sharedPref.getString(
                        keyprefAudioBitrateValue, getString(R.string.pref_startaudiobitratevalue_default));
                audioStartBitrate = Integer.parseInt(bitrateValue);
            }
        }

        // Check statistics display option.
        boolean displayHud = sharedPrefGetBoolean(R.string.pref_displayhud_key,
                RTCPreference.EXTRA_DISPLAY_HUD, R.string.pref_displayhud_default, false);

        boolean tracing = sharedPrefGetBoolean(R.string.pref_tracing_key, RTCPreference.EXTRA_TRACING,
                R.string.pref_tracing_default, false);

        // Check Enable RtcEventLog.
        boolean rtcEventLogEnabled = sharedPrefGetBoolean(R.string.pref_enable_rtceventlog_key,
                RTCPreference.EXTRA_ENABLE_RTCEVENTLOG, R.string.pref_enable_rtceventlog_default,
                false);

        // Get datachannel options
        boolean dataChannelEnabled = sharedPrefGetBoolean(R.string.pref_enable_datachannel_key,
                RTCPreference.EXTRA_DATA_CHANNEL_ENABLED, R.string.pref_enable_datachannel_default,
                false);
        boolean ordered = sharedPrefGetBoolean(R.string.pref_ordered_key, RTCPreference.EXTRA_ORDERED,
                R.string.pref_ordered_default, false);
        boolean negotiated = sharedPrefGetBoolean(R.string.pref_negotiated_key,
                RTCPreference.EXTRA_NEGOTIATED, R.string.pref_negotiated_default, false);
        int maxRetrMs = sharedPrefGetInteger(R.string.pref_max_retransmit_time_ms_key,
                RTCPreference.EXTRA_MAX_RETRANSMITS_MS, R.string.pref_max_retransmit_time_ms_default,
                false);
        int maxRetr =
                sharedPrefGetInteger(R.string.pref_max_retransmits_key, RTCPreference.EXTRA_MAX_RETRANSMITS,
                        R.string.pref_max_retransmits_default, false);
        int id = sharedPrefGetInteger(R.string.pref_data_id_key, RTCPreference.EXTRA_ID,
                R.string.pref_data_id_default, false);
        String protocol = sharedPrefGetString(R.string.pref_data_protocol_key,
                RTCPreference.EXTRA_PROTOCOL, R.string.pref_data_protocol_default, false);

        intent.putExtra(RTCPreference.EXTRA_LOOPBACK, false);
        intent.putExtra(RTCPreference.EXTRA_VIDEO_CALL, videoCallEnabled);
        intent.putExtra(RTCPreference.EXTRA_SCREENCAPTURE, useScreencapture);
        intent.putExtra(RTCPreference.EXTRA_CAMERA2, useCamera2);
        intent.putExtra(RTCPreference.EXTRA_VIDEO_WIDTH, videoWidth);
        intent.putExtra(RTCPreference.EXTRA_VIDEO_HEIGHT, videoHeight);
        intent.putExtra(RTCPreference.EXTRA_VIDEO_FPS, cameraFps);
        intent.putExtra(RTCPreference.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, captureQualitySlider);
        intent.putExtra(RTCPreference.EXTRA_VIDEO_BITRATE, videoStartBitrate);
        intent.putExtra(RTCPreference.EXTRA_VIDEOCODEC, videoCodec);
        intent.putExtra(RTCPreference.EXTRA_HWCODEC_ENABLED, hwCodec);
        intent.putExtra(RTCPreference.EXTRA_CAPTURETOTEXTURE_ENABLED, captureToTexture);
        intent.putExtra(RTCPreference.EXTRA_FLEXFEC_ENABLED, flexfecEnabled);
        intent.putExtra(RTCPreference.EXTRA_NOAUDIOPROCESSING_ENABLED, noAudioProcessing);
        intent.putExtra(RTCPreference.EXTRA_AECDUMP_ENABLED, aecDump);
        intent.putExtra(RTCPreference.EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED, saveInputAudioToFile);
        intent.putExtra(RTCPreference.EXTRA_OPENSLES_ENABLED, useOpenSLES);
        intent.putExtra(RTCPreference.EXTRA_DISABLE_BUILT_IN_AEC, disableBuiltInAEC);
        intent.putExtra(RTCPreference.EXTRA_DISABLE_BUILT_IN_AGC, disableBuiltInAGC);
        intent.putExtra(RTCPreference.EXTRA_DISABLE_BUILT_IN_NS, disableBuiltInNS);
        intent.putExtra(RTCPreference.EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, disableWebRtcAGCAndHPF);
        intent.putExtra(RTCPreference.EXTRA_AUDIO_BITRATE, audioStartBitrate);
        intent.putExtra(RTCPreference.EXTRA_AUDIOCODEC, audioCodec);
        intent.putExtra(RTCPreference.EXTRA_DISPLAY_HUD, displayHud);
        intent.putExtra(RTCPreference.EXTRA_TRACING, tracing);
        intent.putExtra(RTCPreference.EXTRA_ENABLE_RTCEVENTLOG, rtcEventLogEnabled);
        intent.putExtra(RTCPreference.EXTRA_DATA_CHANNEL_ENABLED, dataChannelEnabled);

        if (dataChannelEnabled) {
            intent.putExtra(RTCPreference.EXTRA_ORDERED, ordered);
            intent.putExtra(RTCPreference.EXTRA_MAX_RETRANSMITS_MS, maxRetrMs);
            intent.putExtra(RTCPreference.EXTRA_MAX_RETRANSMITS, maxRetr);
            intent.putExtra(RTCPreference.EXTRA_PROTOCOL, protocol);
            intent.putExtra(RTCPreference.EXTRA_NEGOTIATED, negotiated);
            intent.putExtra(RTCPreference.EXTRA_ID, id);
        }

        startActivity(intent);
    }

    private boolean sharedPrefGetBoolean(
            int attributeId, String intentName, int defaultId, boolean useFromIntent) {
        boolean defaultValue = Boolean.parseBoolean(getString(defaultId));
        if (useFromIntent) {
            return getIntent().getBooleanExtra(intentName, defaultValue);
        } else {
            String attributeName = getString(attributeId);
            return sharedPref.getBoolean(attributeName, defaultValue);
        }
    }

    private String sharedPrefGetString(
            int attributeId, String intentName, int defaultId, boolean useFromIntent) {
        String defaultValue = getString(defaultId);
        if (useFromIntent) {
            String value = getIntent().getStringExtra(intentName);
            if (value != null) {
                return value;
            }
            return defaultValue;
        } else {
            String attributeName = getString(attributeId);
            return sharedPref.getString(attributeName, defaultValue);
        }
    }

    private int sharedPrefGetInteger(
            int attributeId, String intentName, int defaultId, boolean useFromIntent) {
        String defaultString = getString(defaultId);
        int defaultValue = Integer.parseInt(defaultString);
        if (useFromIntent) {
            return getIntent().getIntExtra(intentName, defaultValue);
        } else {
            String attributeName = getString(attributeId);
            String value = sharedPref.getString(attributeName, defaultString);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Wrong setting for: " + attributeName + ":" + value);
                return defaultValue;
            }
        }
    }
}
