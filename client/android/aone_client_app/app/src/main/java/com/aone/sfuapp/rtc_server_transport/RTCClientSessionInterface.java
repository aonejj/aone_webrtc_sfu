package com.aone.sfuapp.rtc_server_transport;

import org.webrtc.IceCandidate;

public interface RTCClientSessionInterface {
    enum RTCSessionState { RTC_SESSION_INVALID_SESSION, RTC_SESSION_CREATED_SESSION, RTC_SESSION_ATTACHED_SESSION, RTC_SESSION_DETACHED_SESSION }

    public int openTransport(String url);
    public void closeTransport();
    public void setTransport(RTCClientSessionTransportInterface transport);

    public void setState(RTCClientSessionInterface.RTCSessionState state);
    public RTCSessionState getState();

    public void regist(boolean is_unified_plan);
    public void sendMessage(RTCSignalingEvents.RTCSignalingCategory category, RTCSignalingEvents.RTCSignalingCommand command,
                            long cookie,String sdp);
    public void sendLocalIceCandidate(final IceCandidate candidate);

    public void dispose();
    public void closeSession();
}