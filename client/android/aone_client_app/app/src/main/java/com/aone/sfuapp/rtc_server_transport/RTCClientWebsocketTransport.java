package com.aone.sfuapp.rtc_server_transport;

import android.util.Log;

import androidx.annotation.Nullable;

import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.ByteString;

public class RTCClientWebsocketTransport implements RTCClientSessionTransportInterface {
    private static final String TAG = "RTCWebsocketTransport";

    @Nullable
    private okhttp3.WebSocket webSocket;

    @Nullable
    private RTCClientSessionTransportCallbackInterface listener;

    private Object listenerLock = new Object();

    private RTCSessionTransportState state = RTCSessionTransportState.RTC_SESSION_TRANSPORT_CONNECTLESS_STATE;


    public RTCClientWebsocketTransport() {

    }

    @Override
    public void open(String url) {
        Log.d(TAG, "open");
        state = RTCSessionTransportState.RTC_SESSION_TRANSPORT_CONNECTING_STATE;
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .addInterceptor(chain -> {
                    Request.Builder builder = chain.request().newBuilder();
                    builder.addHeader("Sec-WebSocket-Protocol", "rtc_media_server_protocol");
                    return chain.proceed(builder.build());
                }).connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .hostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                })
                .build();

        Request request = new Request.Builder().url(url).build();
        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "xxxx onOpen");
                state = RTCSessionTransportState.RTC_SESSION_TRANSPORT_CONNECT_STATE;
                synchronized (listenerLock) {
                    if(listener != null) {
                        listener.onRTCSessionTransportOpen();
                    }
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "onMessage");
                synchronized (listenerLock) {
                    if(listener != null) {
                        listener.onRTCSessionTransportMessage(text);
                    }
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Log.d(TAG, "onMessage binary");
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "onClosing");
                state = RTCSessionTransportState.RTC_SESSION_TRANSPORT_CONNECTING_STATE;
                synchronized (listenerLock) {
                    if(listener != null) {
                        listener.onRTCSessionTransportClosing(code, reason);
                    }
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                state = RTCSessionTransportState.RTC_SESSION_TRANSPORT_CONNECTLESS_STATE;
                synchronized (listenerLock) {
                    if(listener != null) {
                        listener.onRTCSessionTransportClosed(code, reason);
                    }
                }
                RTCClientWebsocketTransport.this.webSocket = null;
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.d(TAG, "xxxxx onFailure" + t.toString());
                state = RTCSessionTransportState.RTC_SESSION_TRANSPORT_ERROR_STATE;
                synchronized (listenerLock) {
                    if(listener != null) {
                        listener.onRTCSessionTransportFailure(t.toString());
                    }
                }
                RTCClientWebsocketTransport.this.webSocket = null;
            }
        });
    }

    @Override
    public void close() {
        if(state != RTCSessionTransportState.RTC_SESSION_TRANSPORT_CONNECTLESS_STATE &&
                state != RTCSessionTransportState.RTC_SESSION_TRANSPORT_ERROR_STATE) {
            webSocket.close(1000, null);
        }
    }

    @Override
    public void sendMessage(String msg) {
        if(state == RTCSessionTransportState.RTC_SESSION_TRANSPORT_CONNECT_STATE) {
            if(webSocket != null) {
                webSocket.send(msg);
            }
        }
    }

    @Override
    public void setRTCSessionCallback(RTCClientSessionTransportCallbackInterface callback) {
        listener = callback;
    }
}