/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.aone.sfuapp;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import org.webrtc.RendererCommon.ScalingType;

/**
 * Fragment for call control.
 */
public class CallFragment extends Fragment {
  private static final String TAG = "CallFragment";
  private TextView contactView;
  private OnCallEvents callEvents;
  private ScalingType scalingType;
  private boolean videoCallEnabled = true;

  /**
   * Call control interface for container activity.
   */
  public interface OnCallEvents {
    void onCallHangUp();
    void onCameraSwitch();
    void onVideoScalingSwitch(ScalingType scalingType);
    boolean onToggleMic();
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View controlView = inflater.inflate(R.layout.fragment_call, container, false);

    // Create UI controls.
    contactView = controlView.findViewById(R.id.contact_name_call);
    ImageButton disconnectButton = controlView.findViewById(R.id.button_call_disconnect);

    // Add buttons click events.
    disconnectButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        callEvents.onCallHangUp();
      }
    });

    scalingType = ScalingType.SCALE_ASPECT_FILL;

    return controlView;
  }

  @Override
  public void onStart() {
    super.onStart();

    boolean captureSliderEnabled = false;
    Bundle args = getArguments();
    if (args != null) {
      String contactName = args.getString(RTCPreference.EXTRA_ROOMID);
      contactView.setText(contactName);
      videoCallEnabled = args.getBoolean(RTCPreference.EXTRA_VIDEO_CALL, true);
      captureSliderEnabled = videoCallEnabled
          && args.getBoolean(RTCPreference.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, false);
    }
  }

  // TODO(sakal): Replace with onAttach(Context) once we only support API level 23+.
  @SuppressWarnings("deprecation")
  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    callEvents = (OnCallEvents) activity;
  }
}
