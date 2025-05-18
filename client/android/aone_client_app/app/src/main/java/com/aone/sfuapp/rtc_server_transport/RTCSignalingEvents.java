package com.aone.sfuapp.rtc_server_transport;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.List;

public interface RTCSignalingEvents {
    public static enum RTCSignalingCategory {
        kRTCSignalingCategory_Regist,
        kRTCSignalingCategory_Init,
        kRTCSignalingCategory_AudioInit,
        kRTCSignalingCategory_VideoAdd,
        kRTCSignalingCategory_VideoRemove,
        kRTCSingalingCategory_Candidate,
    }

    public static enum RTCSignalingCommand {
        kRTCSignalingCommand_Req,
        kRTCSignalingCommand_Res,
        kRTCSignalingCommand_Res_Offer,
        kRTCSignalingCommand_Res_Answer,
        kRTCSignalingCommand_Res_Answer_Ack,
    }

    public void onSignalConnected();
    public void onSignalDisconnected();
    public void onRegisted(List<PeerConnection.IceServer> iceServerList);
    public void onRemoteDescription(RTCSignalingCategory category, RTCSignalingCommand command,
                                    long cookie,final SessionDescription sdp);
    public void onRemoteIceCandidate(final IceCandidate candidate);
    public void onOfferRequest(RTCSignalingCategory category, RTCSignalingCommand command,long cookie, int addCount);
}