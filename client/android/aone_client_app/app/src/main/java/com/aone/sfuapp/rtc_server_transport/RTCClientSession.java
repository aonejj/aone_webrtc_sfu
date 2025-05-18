package com.aone.sfuapp.rtc_server_transport;


import static com.aone.sfuapp.rtc_server_transport.RTCSignalingEvents.RTCSignalingCategory.kRTCSignalingCategory_AudioInit;
import static com.aone.sfuapp.rtc_server_transport.RTCSignalingEvents.RTCSignalingCategory.kRTCSignalingCategory_Init;
import static com.aone.sfuapp.rtc_server_transport.RTCSignalingEvents.RTCSignalingCategory.kRTCSignalingCategory_VideoAdd;
import static com.aone.sfuapp.rtc_server_transport.RTCSignalingEvents.RTCSignalingCategory.kRTCSignalingCategory_VideoRemove;
import static com.aone.sfuapp.rtc_server_transport.RTCSignalingEvents.RTCSignalingCommand.kRTCSignalingCommand_Req;
import static com.aone.sfuapp.rtc_server_transport.RTCSignalingEvents.RTCSignalingCommand.kRTCSignalingCommand_Res_Answer;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import androidx.annotation.Nullable;

import com.aone.sfuapp.RandomGeneratorImpl;

import java.util.ArrayList;
import java.util.List;

import okio.ByteString;

public class RTCClientSession implements RTCClientSessionInterface,
                                         RTCClientSessionTransportCallbackInterface {
    private static final String TAG = "RTCClientSession";

    // attributes names
    private static final String kCategoryAttr = "category";
    private static final String kCommandAttr = "cmd";
    private static final String kCookieAttr = "cookie";
    private static final String kSdpSemanticsAttr = "sdp_semantics";
    private static final String kSessionIdAttr = "session_id";
    private static final String kIceServersAttr = "ice_servers";
    private static final String kSdpAttr = "sdp";
    private static final String kAddCountAttr = "add_count";
    private static final String kCandidateAttr = "candidate";

    // category names
    private static final String kCategoryRegist = "regist";
    private static final String kCategoryInit = "init";
    private static final String kCategoryAudioInit = "audio_init";
    private static final String kCategoryVideoAdd = "video_add";
    private static final String kCategoryVideoRemove = "video_remove";
    private static final String kCategoryCandidate = "candidate";

    // command names
    private static final String kCommandReq = "req";
    private static final String kCommandRes = "res";
    private static final String kCommandResOffer = "res_offer";
    private static final String kCommandResAnswer = "res_answer";
    private static final String kCommandResAnswerAck = "res_answer_ack";

    // candidate attributes
    private static final String kCandidateMLineIndex = "mline_index";
    private static final String kCandidateMId = "mid";

    // sdp semantics value
    private static final String kSdpSemanticsUnifiedPlan = "UnifiedPlan";
    private static final String kSdpSemanticsPlanB = "PlanB";

    @Nullable
    private RTCClientSessionTransportInterface transport;

    @Nullable
    private RTCSignalingEvents signaling_event;

    long session_id;

    protected RTCSessionState state;

    public RTCClientSession(RTCSignalingEvents event) {
        state = RTCSessionState.RTC_SESSION_INVALID_SESSION;
        signaling_event = event;
        session_id = 0;
    }

    /////////////////////////////////////////////////////////////////////
    @Override
    public int openTransport(String url) {
        if(transport == null) {
            return -1;
        }
        transport.open(url);
        return 0;
    }

    @Override
    public void closeTransport() {
        if(transport != null) {
            transport.close();
            transport = null;
        }
    }

    @Override
    public void setTransport(RTCClientSessionTransportInterface transport) {
        this.transport = transport;
        this.transport.setRTCSessionCallback(this);
    }

    @Override
    public void setState(RTCSessionState state) {
        this.state = state;
        if(this.state == RTCSessionState.RTC_SESSION_INVALID_SESSION) {
            // TODO...
        }
    }

    @Override
    public RTCSessionState getState() {
        return state;
    }

    @Override
    public void regist(boolean is_unified_plan) {
        sendregist(is_unified_plan);
    }

    @Override
    public void sendMessage(RTCSignalingEvents.RTCSignalingCategory category, RTCSignalingEvents.RTCSignalingCommand command,
                            long cookie,String sdp) {
        JSONObject jroot = new JSONObject();
        json_put(jroot, kCategoryAttr, RTCSignalingCategoryToString(category));
        json_put(jroot, kCommandAttr, RTCSignalingCommandToString(command));
        json_put(jroot, kCookieAttr, new Long(cookie));
        json_put(jroot, kSessionIdAttr, new Long(session_id));
        if(sdp != null) {
            json_put(jroot, kSdpAttr, sdp);
        }

        transport.sendMessage(jroot.toString());
    }

    @Override
    public void sendLocalIceCandidate(IceCandidate candidate) {
        JSONObject jroot = new JSONObject();
        json_put(jroot, kCategoryAttr, kCandidateAttr);
        json_put(jroot, kSessionIdAttr, new Long(session_id));
        json_put(jroot, kCandidateMLineIndex, new Integer(candidate.sdpMLineIndex));
        json_put(jroot, kCandidateMId, candidate.sdpMid);
        json_put(jroot, kCategoryAttr, candidate.sdp);

        transport.sendMessage(jroot.toString());
    }

    @Override
    public void dispose() {

    }

    @Override
    public void closeSession() {
        closeTransport();
    }

    ////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onRTCSessionTransportOpen() {
        Log.d(TAG, "onRTCSessionTransportOpen");
        signaling_event.onSignalConnected();
    }

    @Override
    public boolean onRTCSessionTransportMessage(String message) {
        try {
            JSONObject jroot = new JSONObject(message);
            String category = jroot.getString(kCategoryAttr);
            if(category.equals(kCategoryRegist)) {
                on_register_response(jroot);
            } else {
                on_other_categories(jroot);
            }
        } catch (JSONException e) {

        }

        return true;
    }

    @Override
    public void onRTCSessionTransportMessage(ByteString bytes) {

    }

    @Override
    public void onRTCSessionTransportClosing(int code, String reason) {
        Log.d(TAG, "onTransportClosing... code "+ code + " "+reason);
    }

    @Override
    public void onRTCSessionTransportClosed(int code, String reason) {
        Log.d(TAG, "onTransportClosed... code "+ code + " "+reason);
        setState(RTCSessionState.RTC_SESSION_INVALID_SESSION);
    }

    @Override
    public void onRTCSessionTransportFailure(String reason) {
        Log.d(TAG, "onTransportFailure... "+reason);
        setState(RTCSessionState.RTC_SESSION_INVALID_SESSION);
    }

    private void sendregist(boolean is_unified_plan) {
        JSONObject jroot = new JSONObject();
        json_put(jroot, kCategoryAttr, kCategoryRegist);
        json_put(jroot, kCommandAttr, kCommandReq);
        json_put(jroot, kCookieAttr, new Long(RandomGeneratorImpl.CreateRandomId()));
        if(is_unified_plan) {
            json_put(jroot, kSdpSemanticsAttr, kSdpSemanticsUnifiedPlan);
        } else {
            json_put(jroot, kSdpSemanticsAttr, kSdpSemanticsPlanB);
        }

        transport.sendMessage(jroot.toString());
    }

    private static void json_put(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void on_register_response(JSONObject jroot) {
        try {
            Log.d(TAG, "on_register_response " + jroot.toString());
            int cookie = jroot.getInt(kCookieAttr);
            this.session_id = jroot.getLong(kSessionIdAttr);

            JSONArray servers = jroot.getJSONArray(kIceServersAttr);
            List<PeerConnection.IceServer> ret = new ArrayList<>();
            Log.d(TAG,">>>>>>>>>>>>>>>>>>>> " + servers.toString());
            for (int i = 0; i < servers.length(); ++i) {
                JSONObject server = servers.getJSONObject(i);

                JSONArray urls = server.getJSONArray("urls");
                String username = server.has("username") ? server.getString("username") : "";
                String credential = server.has("credential") ? server.getString("credential") : "";
                Log.d(TAG,">>>>>>>>>>>>>>>>>>> "+urls.toString() + " len "+urls.length());
                for(int j = 0; j < urls.length(); j++) {
                    String url = urls.getString(j);
                    Log.d(TAG, ">>>>>>>>>>>>>>>>>>>>>>>> stun url " + url);
                    PeerConnection.IceServer turnServer = PeerConnection.IceServer.builder(url)
                            .setPassword(username)
                            .setUsername(credential)
                            .createIceServer();

                    ret.add(turnServer);
                }
            }

            signaling_event.onRegisted(ret);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void on_other_categories(JSONObject jroot) {
        try {
            Long sid = jroot.getLong(kSessionIdAttr);
            Log.d(TAG, "on_other_categories " + jroot.toString());
            Log.d(TAG, "on_other_categories session_id " + session_id + " sid "+sid);
            if(session_id != sid) {
                throw new RuntimeException("session_id != sid");
            }

            String category = jroot.getString(kCategoryAttr);
            if(category.equals(kCategoryVideoAdd)) {
                on_categories(jroot, kRTCSignalingCategory_VideoAdd);
            } else if (category.equals(kCategoryVideoRemove)) {
                on_categories(jroot, kRTCSignalingCategory_VideoRemove);
            } else if (category.equals(kCategoryCandidate)) {
                on_category_candidate(jroot);
            } else if(category.equals(kCategoryInit)) {
                on_categories(jroot, kRTCSignalingCategory_Init);
            } else if(category.equals(kCategoryAudioInit)) {
                on_categories(jroot, kRTCSignalingCategory_AudioInit);
            } else {
                throw new RuntimeException("invalid category");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void on_categories(JSONObject jroot, RTCSignalingEvents.RTCSignalingCategory category) throws JSONException {
        String command = jroot.getString(kCommandAttr);
        RTCSignalingEvents.RTCSignalingCommand cmd;

        if(command.equals(kCommandReq)) {
            cmd = kRTCSignalingCommand_Req;
        } else {
            cmd = kRTCSignalingCommand_Res_Answer;
        }
        long cookie = jroot.getLong(kCookieAttr);

        if(cmd == kRTCSignalingCommand_Req) {
            int addCount = 0;
            if(category == kRTCSignalingCategory_VideoAdd) {
                addCount = jroot.getInt(kAddCountAttr);
            }
            signaling_event.onOfferRequest(category, cmd, cookie, addCount);
        } else {
            String sdp = jroot.getString(kSdpAttr);
            SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER,
                    sdp);
            signaling_event.onRemoteDescription(category,cmd, cookie, sessionDescription);
        }
    }

    private void on_category_candidate(JSONObject jroot) throws JSONException {
        int mline_index = jroot.getInt(kCandidateMLineIndex);
        String mid = jroot.getString(kCandidateMId);
        String candidate = jroot.getString(kCandidateAttr);

        IceCandidate iceCandidate = new IceCandidate(mid, mline_index, candidate);
        signaling_event.onRemoteIceCandidate(iceCandidate);
    }

    private String RTCSignalingCategoryToString(RTCSignalingEvents.RTCSignalingCategory category) {

        switch(category) {
            case kRTCSignalingCategory_Regist:
                return kCategoryRegist;
            case kRTCSignalingCategory_Init:
                return kCategoryInit;
            case kRTCSignalingCategory_AudioInit:
                return kCategoryAudioInit;
            case kRTCSignalingCategory_VideoAdd:
                return kCategoryVideoAdd;
            case kRTCSignalingCategory_VideoRemove:
                return kCategoryVideoRemove;
            case kRTCSingalingCategory_Candidate:
                return kCategoryCandidate;
            default:
                break;
        }
        return null;
    }

    private String RTCSignalingCommandToString(RTCSignalingEvents.RTCSignalingCommand command) {
        switch (command) {
            case kRTCSignalingCommand_Req:
                return kCommandReq;
            case kRTCSignalingCommand_Res:
                return kCommandRes;
            case kRTCSignalingCommand_Res_Offer:
                return kCommandResOffer;
            case kRTCSignalingCommand_Res_Answer:
                return kCommandResAnswer;
            case kRTCSignalingCommand_Res_Answer_Ack:
                return kCommandResAnswerAck;
            default:
                break;
        }

        return null;
    }
}

