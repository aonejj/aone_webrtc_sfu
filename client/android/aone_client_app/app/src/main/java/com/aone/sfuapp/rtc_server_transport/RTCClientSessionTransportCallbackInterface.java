package com.aone.sfuapp.rtc_server_transport;

import okio.ByteString;

public interface RTCClientSessionTransportCallbackInterface {
    public void onRTCSessionTransportOpen();
    public boolean onRTCSessionTransportMessage(String message);
    public void onRTCSessionTransportMessage(ByteString bytes);
    public void onRTCSessionTransportClosing(int code, String reason);
    public void onRTCSessionTransportClosed(int code, String reason);
    public void onRTCSessionTransportFailure(String reason);
}
