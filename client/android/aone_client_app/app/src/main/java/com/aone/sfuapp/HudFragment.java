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

import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import org.webrtc.RTCStats;
import org.webrtc.RTCStatsReport;
import org.webrtc.StatsReport;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;


/**
 * Fragment for HUD statistics display.
 */
public class HudFragment extends Fragment {

  private static final String TAG = "HudFragment";

  private TextView encoderStatView;
  private TextView videoRatioRecvStatView;
  private TextView hudViewBwe;
  private TextView hudViewConnection;
  private TextView hudViewVideoSend;
  private TextView hudViewVideoRecv;
  private ImageButton toggleDebugButton;
  private boolean videoCallEnabled;
  private boolean displayHud;
  private volatile boolean isRunning;
  private long recv_bytes = 0;
  private long recv_bytes_ms = 0;

  private double last_stats_ts = 0;
  private long send_bytes = 0;

  private final float TEXT_SIZE = 5;

  public class VideoOutboundRtpInfo {
    long _timestampUs;
    long _sent_bytes;
    long _bitrate;
    long _w = 0;
    long _h = 0;
    double _total_send_delay = 0.0;
    double _send_delay = 0.0;
    int _packet_lost = 0;
    double _jitter = 0.0;
    double _rtt = 0.0;
  }

  Map<String, VideoOutboundRtpInfo> videoOutbounds =  new HashMap<String, VideoOutboundRtpInfo>();
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View controlView = inflater.inflate(R.layout.fragment_hud, container, false);

    // Create UI controls.
    encoderStatView = controlView.findViewById(R.id.encoder_stat_call);
    videoRatioRecvStatView = controlView.findViewById(R.id.video_recv_stat);
    hudViewBwe = controlView.findViewById(R.id.hud_stat_bwe);
    hudViewConnection = controlView.findViewById(R.id.hud_stat_connection);
    hudViewVideoSend = controlView.findViewById(R.id.hud_stat_video_send);
    hudViewVideoRecv = controlView.findViewById(R.id.hud_stat_video_recv);
    toggleDebugButton = controlView.findViewById(R.id.button_toggle_debug);

    toggleDebugButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (displayHud) {
          Log.d(TAG,"hud_trace toggleDebugButton click");
          int visibility =
              (hudViewBwe.getVisibility() == View.VISIBLE) ? View.INVISIBLE : View.VISIBLE;
          hudViewsSetProperties(visibility);

        }
      }
    });

    return controlView;
  }

  @Override
  public void onStart() {
    super.onStart();


    Bundle args = getArguments();
    if (args != null) {
      videoCallEnabled = args.getBoolean(RTCPreference.EXTRA_VIDEO_CALL, true);
      displayHud = args.getBoolean(RTCPreference.EXTRA_DISPLAY_HUD, false);
    }
    Log.d(TAG,"hud_trace displayHud "+ displayHud);
    int visibility = displayHud ? View.VISIBLE : View.INVISIBLE;
    Log.d(TAG,"hud_trace visibility "+ visibility);
    encoderStatView.setVisibility(visibility);    // update kimi visibility -> INVISIBLE
    toggleDebugButton.setVisibility(visibility);  // update kimi visibility -> INVISIBLE
    hudViewsSetProperties(View.INVISIBLE);        // hardcoding kimi
    Log.d(TAG,"hud_trace onStart---");
    isRunning = true;

  }

  @Override
  public void onStop() {
    isRunning = false;
    super.onStop();
  }

  private void hudViewsSetProperties(int visibility) {
    hudViewBwe.setVisibility(visibility);
    hudViewConnection.setVisibility(visibility);
    hudViewVideoSend.setVisibility(visibility);
    hudViewVideoRecv.setVisibility(visibility);
    hudViewBwe.setTextSize(TypedValue.COMPLEX_UNIT_PT, TEXT_SIZE);      // 5
    hudViewConnection.setTextSize(TypedValue.COMPLEX_UNIT_PT, TEXT_SIZE);
    hudViewVideoSend.setTextSize(TypedValue.COMPLEX_UNIT_PT, TEXT_SIZE);
    hudViewVideoRecv.setTextSize(TypedValue.COMPLEX_UNIT_PT, TEXT_SIZE);
  }

  private Map<String, String> getReportMap(StatsReport report) {
    Map<String, String> reportMap = new HashMap<>();
    for (StatsReport.Value value : report.values) {
      reportMap.put(value.name, value.value);
    }
    return reportMap;
  }

  public void rtcStatReportCallabck(RTCStatsReport report) {
    Log.d(TAG,"stat_trace rtcStatReportCallabck");
    if (!isRunning || !displayHud) {
      Log.d(TAG,"hud_trace rtcStatReportCallabck !isRunning || !displayHud");
      return;
    }
    double availableOutgoingBitrate = 0.0;
    Map<String, RTCStats> stats = report.getStatsMap();
    for (Map.Entry<String, RTCStats> entry : stats.entrySet()) {
      Log.d(TAG, "stat_trace key " + entry.getKey().toString());
      Log.d(TAG, "stat_trace value " + entry.getValue().toString());
      if (entry.getKey().contains("RTCOutboundRTPVideoStream")) {
        RTCStats value = entry.getValue();
        Log.d(TAG, "hud_trace " + value.toString());
        Map<String, Object> members = value.getMembers();
        long timeUs = (long) value.getTimestampUs();
        String ssrc = String.valueOf(members.get("ssrc"));
        BigInteger sentBytes = (BigInteger) members.get("bytesSent");
        double send_delay = 0.0;
        if (members.get("totalPacketSendDelay") != null) {
          send_delay = (double) members.get("totalPacketSendDelay");
        }

        VideoOutboundRtpInfo vobj = videoOutbounds.get(ssrc);
        if (vobj != null) {
          // bitrate 계산
          long d_sent = sentBytes.longValue() - vobj._sent_bytes;
          long d_ms = (timeUs - vobj._timestampUs) / 1000;
          vobj._bitrate = ((d_sent * 1000) / d_ms) * 8;
          vobj._timestampUs = timeUs;
          vobj._sent_bytes = sentBytes.longValue();
          if (vobj._w == 0) {
            if (members.get("frameWidth") != null) {
              vobj._w = (long) members.get("frameWidth");
            }
          }

          if (vobj._h == 0) {
            if (members.get("frameHeight") != null) {
              vobj._h = (long) members.get("frameHeight");
            }
          }
          vobj._send_delay = send_delay - vobj._total_send_delay;
          vobj._total_send_delay = send_delay;
        } else {
          VideoOutboundRtpInfo tmp = new VideoOutboundRtpInfo();
          tmp._sent_bytes = sentBytes.longValue();
          tmp._timestampUs = timeUs;
          tmp._bitrate = 0;
          if (members.get("frameWidth") != null) {
            tmp._w = (long) members.get("frameWidth");
          }
          if (members.get("frameHeight") != null) {
            tmp._h = (long) members.get("frameHeight");
          }
          tmp._total_send_delay = send_delay;
          videoOutbounds.put(ssrc, tmp);
        }
      } else if (entry.getKey().contains("RTCRemoteInboundRtpVideoStream")) {
        RTCStats value = entry.getValue();
        Map<String, Object> members = value.getMembers();
        String ssrc = String.valueOf(members.get("ssrc"));
        VideoOutboundRtpInfo vobj = videoOutbounds.get(ssrc);
        if(vobj != null) {
          if(members.get("packetsLost") != null) {
            vobj._packet_lost = (int)members.get("packetsLost");
          }
          if(members.get("jitter") != null) {
            vobj._jitter = (double) members.get("jitter");
          }
          if(members.get("roundTripTime") != null) {
            vobj._rtt = (double) members.get("roundTripTime");
          }
        }
      } else if(entry.getKey().contains("RTCIceCandidatePair")) {
        RTCStats value = entry.getValue();
        Map<String, Object> members = value.getMembers();
        if(members.get("availableOutgoingBitrate") != null) {
          availableOutgoingBitrate = (double)members.get("availableOutgoingBitrate");
        }
      }
    }


    StringBuilder rtp_outbound_info = new StringBuilder();

    for (Map.Entry<String, VideoOutboundRtpInfo> entry : videoOutbounds.entrySet()) {
      rtp_outbound_info.append("ssrc: ").append(entry.getKey()).append("\n");
      rtp_outbound_info.append("w: ").append(entry.getValue()._w).append(" h: ").append(entry.getValue()._h).append("\n");
      rtp_outbound_info.append("bitrate: ").append(entry.getValue()._bitrate).append("\n");
      rtp_outbound_info.append("send_delay: ").append(String.format("%.3f",entry.getValue()._send_delay)).append("\n");
      rtp_outbound_info.append("packetLost: ").append(entry.getValue()._packet_lost).append("\n");
      rtp_outbound_info.append("jiiter: ").append(String.format("%.3f",entry.getValue()._jitter)).append(" rtt: ").append(String.format("%.3f",entry.getValue()._rtt)).append("\n\n");
    }
    rtp_outbound_info.append("abailable outgoing bitrate: ").append(availableOutgoingBitrate);
    Log.d(TAG,"hud_trace displayHud "+ rtp_outbound_info.toString());
    encoderStatView.setText(rtp_outbound_info.toString());
  }

  public void updateEncoderStatistics(final StatsReport[] reports) {
    Log.d("HudFragment", "----> updateEncoderStatistics isRunning "+isRunning + " displayHud "+displayHud);
    if (!isRunning || !displayHud) {
      return;
    }
  }

  public void setEncoderStatViewToggle() {
    if(encoderStatView.getVisibility() == View.INVISIBLE) {
      encoderStatView.setVisibility(View.VISIBLE);
    } else {
      encoderStatView.setVisibility(View.INVISIBLE);
    }
  }
}
