package com.aone.sfuapp.rtc_server_transport;

public interface RTCClientSessionTransportInterface {
    public enum RTCSessionTransportState {
        RTC_SESSION_TRANSPORT_CONNECTLESS_STATE,
        RTC_SESSION_TRANSPORT_CONNECTING_STATE,
        RTC_SESSION_TRANSPORT_CONNECT_STATE,
        RTC_SESSION_TRANSPORT_CONNECT_CLOSING_STATE,
        RTC_SESSION_TRANSPORT_ERROR_STATE
    }

    public void open(String url);
    public void close();
    public void sendMessage(String msg);
    public void setRTCSessionCallback(RTCClientSessionTransportCallbackInterface callback);
}