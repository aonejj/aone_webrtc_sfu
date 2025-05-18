package com.aone.sfuapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import com.aone.sfuapp.rtc_server_transport.RTCClientSession;
import com.aone.sfuapp.rtc_server_transport.RTCClientWebsocketTransport;
import com.aone.sfuapp.rtc_server_transport.RTCSignalingEvents;

import org.murillo.sdp.ParserException;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCStatsCollectorCallback;
import org.webrtc.RTCStatsReport;
import org.webrtc.RendererCommon;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.voiceengine.WebRtcAudioUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.Nullable;

import static com.aone.sfuapp.rtc_server_transport.RTCSignalingEvents.RTCSignalingCategory.kRTCSignalingCategory_AudioInit;
import static com.aone.sfuapp.rtc_server_transport.RTCSignalingEvents.RTCSignalingCategory.kRTCSignalingCategory_VideoAdd;
import static com.aone.sfuapp.rtc_server_transport.RTCSignalingEvents.RTCSignalingCategory.kRTCSignalingCategory_VideoRemove;
import static com.aone.sfuapp.rtc_server_transport.RTCSignalingEvents.RTCSignalingCommand.kRTCSignalingCommand_Res_Answer_Ack;
import static com.aone.sfuapp.rtc_server_transport.RTCSignalingEvents.RTCSignalingCommand.kRTCSignalingCommand_Res_Offer;
import static org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO;


public class RTCClientActivity extends Activity
                               implements RTCSignalingEvents,
                               PeerConnection.Observer,
                               CallFragment.OnCallEvents,
                               RTCStatsCollectorCallback {
    private static final String TAG = "RTCClientActivity";
    private boolean is_init_max_bitrate = false;

    private boolean isStatsEnable = false;
    private Timer statsTimer = null;

    private String signaling_server_ip = null;
    private String signaling_server_port = null;

    public static class RTCProxyVideoSink implements VideoSink {
        private VideoSink target;
        public boolean _is_remote = false;    // add kimi for test

        @Override
        synchronized public void onFrame(VideoFrame frame) {
            if (target == null) {
                Logging.d(TAG, "Dropping frame in proxy because target is null.");
                return;
            }

            if(_is_remote == true) {
            }

            target.onFrame(frame);
        }

        synchronized public void setTarget(VideoSink target) {
            this.target = target;
        }
    }

    @Nullable
    private RTCClientSession session;
    @Nullable
    private SurfaceViewRenderer local_renderer;
    // Controls
    private CallFragment callFragment;
	private HudFragment hudFragment;
    private final RTCProxyVideoSink localProxyVideoSink = new RTCProxyVideoSink();

    @Nullable
    private List<SurfaceViewRenderer> remote_renderes_pool;

    private boolean isError;
    private Toast logToast;

    private final ExecutorService signal_executor = Executors.newSingleThreadExecutor();

    private final ExecutorService rtc_executor = Executors.newSingleThreadExecutor();
    private boolean _is_init_local_sdp = false;
    @Nullable
    private List<IceCandidate> queuedRemoteCandidates;
    private boolean _is_set_remote_description = false;

    private final int kHeigth = 720;
    private final int kWidth = 1280;
    private final int kFps = 30;
    private final int kMaxAudioReceivers = 3;
	private boolean hudControlFragmentVisible = false;
	private boolean isConnected = false;

    @Nullable
    private AppRTCAudioManager audioManager;

    private EglBase eglBase;
    @Nullable
    private RTCCoreSDK  _rtc_sdk = null;
    @Nullable
    private AudioDeviceModule _adm;
    @Nullable
    private VideoCapturer _camera_capturer = null;
    @Nullable
    private VideoTrack _camera_track = null;
    @Nullable
    private String _camera_track_id;
    @Nullable
    private String _camera_stream_id;
    @Nullable
    private AudioTrack _audio_track = null;
    @Nullable
    private String _audio_track_id;
    @Nullable
    private String _audio_stream_id;
    @Nullable
    private RtpTransceiver _audio_transceiver;
    @Nullable
    private RtpTransceiver _camera_transceiver;
    @Nullable
    private DataChannel _data_channel;
    @Nullable
    private SessionDescription _local_sdp;
    @Nullable
    private Long _camera_sim_ssrc_1 = null;
    @Nullable
    private Long _camera_sim_ssrc_rtx_1 = null;

    private Long _camera_sim_ssrc_2;
    private Long _camera_sim_ssrc_rtx_2;
    private Long _camera_sim_ssrc_3;
    private Long _camera_sim_ssrc_rtx_3;
    @Nullable
    private String _cname = null;

    private boolean _is_unified_plan = true;
    private boolean _is_registed_response = false;
    ////////////////////////////////////////////////////////////////////////////////////////

    private class SignalingMessage {
        public boolean in_process;
        public RTCSignalingCategory category;
        public RTCSignalingCommand command;
        public long cookie;
        public int addCont;
        public SessionDescription sdp;

        public SignalingMessage(RTCSignalingCategory category, RTCSignalingCommand command, long cookie, int addCont) {
            in_process = false;
            this.category = category;
            this.command = command;
            this.cookie = cookie;
            this.addCont = addCont;
            sdp = null;
        }
    }

    private Deque<SignalingMessage> pending_signaling_messages = new ArrayDeque<>();

    private class RemoteVideoTrackInfo {
        public RtpTransceiver       _transceiver;
        public SurfaceViewRenderer  _renderer;
        public RTCProxyVideoSink    _proxy_video_sink;
        public VideoTrack           _video_track;

        public RemoteVideoTrackInfo() {
            this._transceiver = null;
            this._video_track = null;
            this._proxy_video_sink = null;
            this._renderer = null;
        }
    }

    private final List<String> pending_remote_video_streams = new ArrayList<>();;

    private Object remote_video_track_infos_lock = new Object();

    private ConcurrentHashMap<String, RemoteVideoTrackInfo> remote_video_track_infos =  new ConcurrentHashMap<>();
    ////////////////////////////////////////////////////////////////////////////////////////
    // signaling message handler
    private class SignalingMessageHandlerThread extends HandlerThread {
        private static final String TAG = "SignalingMessageHandlerThread";

        private Handler _handler;
        private WeakReference<RTCClientActivity> _weakActiviy;

        // messages
        public static final int kWhatRegistReq = 0;
        public static final int kWhatRegistedRes = 1;
        public static final int kWhatSignalingMessagePush = 2;
        public static final int kWhatSignalingMessageProcessing = 3;
        public static final int kWhatCreateOffer = 4;
        public static final int kWhatSetLocalDescription = 5;
        public static final int kWhatSetLocalDescriptionSuccess = 6;
        public static final int kWhatRemoteDescription = 7;
        public static final int kWhatSetRemoteDescriptionSuccess = 8;
        public static final int kWhatRemoteCandidate = 9;
        public static final int kWhatLocalCandidate = 10;

        public SignalingMessageHandlerThread(RTCClientActivity activity) {
            super(TAG);
            _weakActiviy = new WeakReference<>(activity);
        }

        @Override
        protected void onLooperPrepared() {
            _handler = new Handler(getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch(msg.what) {
                        case kWhatRegistReq:
                        {
                            RTCClientActivity activity = _weakActiviy.get();
                            if(activity != null) {
                                activity.regist();
                            }
                            break;
                        }
                        case kWhatRegistedRes:
                        {
                            RTCClientActivity activity = _weakActiviy.get();
                            if(activity != null) {
                                activity.on_regist_server((List<PeerConnection.IceServer>)msg.obj);
                            }
                            break;
                        }
                        case kWhatSignalingMessagePush:
                        {
                            RTCClientActivity activity = _weakActiviy.get();
                            if(activity != null) {
                                activity.signaling_message_push_h((SignalingMessage)msg.obj);
                            }
                            break;
                        }
                        case kWhatSignalingMessageProcessing:
                        {
                            RTCClientActivity activity = _weakActiviy.get();
                            if(activity != null) {
                                activity.signaling_message_processing_h();
                            }
                            break;
                        }
                        case kWhatCreateOffer:
                        {
                            RTCClientActivity activity = _weakActiviy.get();
                            if(activity != null) {
                                activity.create_offer_h();
                            }
                            break;
                        }
                        case kWhatSetLocalDescription:
                        {
                            RTCClientActivity activity = _weakActiviy.get();
                            if(activity != null) {
                                activity.set_local_description_h((SessionDescription) msg.obj);
                            }
                            break;
                        }
                        case kWhatSetLocalDescriptionSuccess:
                        {
                            RTCClientActivity activity = _weakActiviy.get();
                            if(activity != null) {
                                activity.set_local_description_success_h();
                            }

                            break;
                        }
                        case kWhatRemoteDescription:
                        {
                            SignalingMessage tmp_sig_msg = (SignalingMessage)msg.obj;

                            RTCClientActivity activity = _weakActiviy.get();
                            if(activity != null) {
                                activity.on_remote_session_description_h(tmp_sig_msg);
                            }

                            break;
                        }
                        case kWhatSetRemoteDescriptionSuccess:
                        {
                            RTCClientActivity activity = _weakActiviy.get();
                            if(activity != null) {
                                activity.on_set_remote_session_description_h();
                            }
                            break;
                        }
                        case kWhatRemoteCandidate:
                        {
                            IceCandidate candidate = (IceCandidate)msg.obj;
                            RTCClientActivity activity = _weakActiviy.get();
                            if(activity != null) {
                                activity.on_remote_ice_candidate_h(candidate);
                            }
                            break;
                        }
                        case kWhatLocalCandidate:
                        {
                            IceCandidate candidate = (IceCandidate)msg.obj;
                            RTCClientActivity activity = _weakActiviy.get();
                            if(activity != null) {
                                activity.on_local_ice_candidate_h(candidate);
                            }

                            break;
                        }
                    }
                }
            };
        }

        public Handler getThreadHandler() {
            return _handler;
        }
    }

    private SignalingMessageHandlerThread signalingMessageHandlerThread;
    ////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_rtc_client);

        callFragment = new CallFragment();
		hudFragment = new HudFragment();
        callFragment.setArguments(getIntent().getExtras());
		hudFragment.setArguments(getIntent().getExtras());
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(R.id.rtc_client_call_fragment_container, callFragment);
	    ft.add(R.id.rtc_client_hud_fragment_container, hudFragment);
        ft.commit();

        queuedRemoteCandidates = new ArrayList<>();

        initEGLAndRenderer();
        init_rtc();
        audioManager = AppRTCAudioManager.create(getApplicationContext());
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        audioManager.start(new AppRTCAudioManager.AudioManagerEvents() {
            // This method will be called each time the number of available audio
            // devices has changed.
            @Override
            public void onAudioDeviceChanged(
                    AppRTCAudioManager.AudioDevice audioDevice, Set<AppRTCAudioManager.AudioDevice> availableAudioDevices) {
                onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
            }
        });

        signaling_server_ip = getIntent().getStringExtra(RTCPreference.EXTRA_SIGNALING_SERVER_IP);
        signaling_server_port = getIntent().getStringExtra(RTCPreference.EXTRA_SIGNALING_SERVER_PORT);

        signalingMessageHandlerThread = new SignalingMessageHandlerThread(this);
        signalingMessageHandlerThread.start();

        create_session();
        signaling_connect();
    }

    @Override
    protected  void onDestroy() {
        if(session != null) {
            session.closeSession();
        }

        Thread.setDefaultUncaughtExceptionHandler(null);
        disconnect();

        super.onDestroy();
    }

    private void onAudioManagerDevicesChanged(
            final AppRTCAudioManager.AudioDevice device, final Set<AppRTCAudioManager.AudioDevice> availableDevices) {
        Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
                + "selected: " + device);
        // TODO(henrika): add callback handler.
    }

    private void initEGLAndRenderer() {
        local_renderer = findViewById(R.id.rtc_client_local_video_view);
        eglBase = EglBase.create();

        local_renderer.init(eglBase.getEglBaseContext(), null);
        local_renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        local_renderer.setZOrderMediaOverlay(true);

        createRemoteRenderesPool();

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleCallControlFragmentVisibility();
            }
        };

        for(int i = 0; i < remote_renderes_pool.size(); i++) {
            remote_renderes_pool.get(i).setOnClickListener(listener);
        }
        localProxyVideoSink.setTarget(local_renderer);
    }

    private void init_rtc() {
        String fieldTrials = "";
        rtc_executor.execute(()->{
            RTCCoreSDK.RTCPeerConnectionFactory_Initialize(getApplicationContext(),
                    fieldTrials, false);

            RTCCoreSDK.RTCPeerConnection_SetLoggingSeverity(Logging.Severity.LS_VERBOSE);
        });
    }

    private void create_peer_connection (List<PeerConnection.IceServer> iceServerList) {
        rtc_executor.execute(()-> {
            _rtc_sdk = new RTCCoreSDK(getApplicationContext(), eglBase);

            WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
            WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(true);
            WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true);

            _adm = _rtc_sdk.RTCAudioDeviceModule_Create(
                    true, true, null);    // default disable AEC : true  NS : ture

            PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
            options.networkIgnoreMask = options.networkIgnoreMask | PeerConnectionFactory.Options.ADAPTER_TYPE_LOOPBACK;

            RTCCoreSDK.RTCCodecFactoryParameters codecParams = new RTCCoreSDK.RTCCodecFactoryParameters(
                    RTCCodecs.VIDEO_CODEC_VP8,
                    RTCCodecs.AUDIO_CODEC_OPUS,
                    false,
                    false);

            _rtc_sdk.RTCPeerConnectionFactory_Create(_adm, options, codecParams);

            PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServerList);
            rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
            rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
            rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
            rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;

            rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
            rtcConfig.enableDtlsSrtp = true;
            rtcConfig.sdpSemantics = (_is_unified_plan ? PeerConnection.SdpSemantics.UNIFIED_PLAN : PeerConnection.SdpSemantics.PLAN_B) ;
            rtcConfig.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.LOW_COST;

            _rtc_sdk.RTCPeerConnection_Create(rtcConfig, this);
        });
    }

    private void create_init_tracks() {
        rtc_executor.execute(()-> {
            // create audio track and transceiver
            _audio_track = create_audio_track();
            _audio_transceiver = add_audio_transceiver(_audio_track);

            // create video track and transceiver
            _camera_capturer = create_video_captuer();
            _camera_track = create_video_track(_camera_capturer);
            _camera_transceiver = add_video_transceiver(_camera_track);

            _camera_capturer.startCapture(kWidth, kHeigth, kFps);
            _camera_track.addSink(local_renderer);

            // create data channel
            _data_channel = create_datachannel("unit_test_dc");
        });
    }

    private void create_offer_h() {
        rtc_executor.execute(()-> {
            MediaConstraints sdpMediaConstraints = new MediaConstraints();
            sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("voiceActivityDetection", "true"));
            sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("iceRestart", "false"));

            if(_rtc_sdk == null) {
                return;
            }

            _rtc_sdk.RTCPeerConnection_CreateOffer(sdpMediaConstraints, new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                    if(_is_init_local_sdp == false) {
                        SessionDescription init_sdp = init_local_sdp(sdp);
//                        dump_sdp(init_sdp, true);
                        sdp = init_sdp;
                    }
                    Message msg = signalingMessageHandlerThread.getThreadHandler().obtainMessage(SignalingMessageHandlerThread.kWhatSetLocalDescription, sdp);
                    signalingMessageHandlerThread.getThreadHandler().sendMessage(msg);
                }

                @Override
                public void onSetSuccess() { }

                @Override
                public void onCreateFailure(String error) { }

                @Override
                public void onSetFailure(String error) { }
            });
        });
    }

    private SessionDescription init_local_sdp(SessionDescription origin_sdp) {
        org.murillo.sdp.SessionDescription medooze_sdp;
        SessionDescription init_sdp = null;
        try {
            medooze_sdp = org.murillo.sdp.SessionDescription.Parse(origin_sdp.description);
            ArrayList<org.murillo.sdp.MediaDescription> mz_media_descriptions = medooze_sdp.getMedias();

            for (org.murillo.sdp.MediaDescription mz_md : mz_media_descriptions) {
                if(mz_md.getMedia().equals("video")) {
                    get_video_media_section_ssrcs(mz_md);
                    add_simulcast_ssrcs(mz_md);
                    init_sdp = new SessionDescription(origin_sdp.type, medooze_sdp.toString());
                    break;
                }
            }
        }catch (ParserException e) {
            e.printStackTrace();
        }
        _is_init_local_sdp = true;
        return init_sdp;
    }

    private void get_video_media_section_ssrcs(org.murillo.sdp.MediaDescription mz_md) {
        for(org.murillo.sdp.Attribute mz_attr : mz_md.getAttributes()) {
            if(mz_attr.getField().equals("ssrc-group")) {
                org.murillo.sdp.SSRCGroupAttribute groupAttribute = (org.murillo.sdp.SSRCGroupAttribute)mz_attr;
                ArrayList<Long> ssrcs = groupAttribute.getSSRCIds();
                _camera_sim_ssrc_1 = ssrcs.get(0);
                _camera_sim_ssrc_rtx_1 = ssrcs.get(1);
                if(_cname != null) {
                    break;
                }
            } else if (mz_attr.getField().equals("ssrc")) {
                org.murillo.sdp.SSRCAttribute ssrcAttr = (org.murillo.sdp.SSRCAttribute)mz_attr;
                if(ssrcAttr.getAttrField().equals("cname")) {
                    _cname = ssrcAttr.getAttrValue();
                    if(_camera_sim_ssrc_1 != null) {
                        break;
                    }
                }
            }
        }
    }

    private void add_simulcast_ssrcs(org.murillo.sdp.MediaDescription mz_md) {
        _camera_sim_ssrc_2 = new Long(RandomGeneratorImpl.CreateRandomId());
        _camera_sim_ssrc_3 = new Long(RandomGeneratorImpl.CreateRandomId());

        _camera_sim_ssrc_rtx_2 = new Long(RandomGeneratorImpl.CreateRandomId());
        _camera_sim_ssrc_rtx_3 = new Long(RandomGeneratorImpl.CreateRandomId());

        ArrayList<Long> ssrcs = new ArrayList<>();
        ssrcs.add(_camera_sim_ssrc_1);
        ssrcs.add(_camera_sim_ssrc_2);
        ssrcs.add(_camera_sim_ssrc_3);

        String tmp_msid = _camera_stream_id + " " + _camera_track_id;


        org.murillo.sdp.SSRCGroupAttribute ssrcsGroupAttr = new org.murillo.sdp.SSRCGroupAttribute("SIM", ssrcs);
        mz_md.addAttribute(ssrcsGroupAttr);

        ArrayList<Long> fid_ssrcs2 = new ArrayList<>();
        fid_ssrcs2.add(_camera_sim_ssrc_2);
        fid_ssrcs2.add(_camera_sim_ssrc_rtx_2);
        org.murillo.sdp.SSRCGroupAttribute ssrcGroupAttr2 = new org.murillo.sdp.SSRCGroupAttribute("FID", fid_ssrcs2);
        mz_md.addAttribute(ssrcGroupAttr2);

        org.murillo.sdp.SSRCAttribute mz_ssrc_attr2 = new org.murillo.sdp.SSRCAttribute(_camera_sim_ssrc_2, "cname", _cname);
        mz_md.addAttribute(mz_ssrc_attr2);
        org.murillo.sdp.SSRCAttribute mz_ssrc_mid_tid_2 = new org.murillo.sdp.SSRCAttribute(_camera_sim_ssrc_2, "msid", tmp_msid);
        mz_md.addAttribute(mz_ssrc_mid_tid_2);

        org.murillo.sdp.SSRCAttribute mz_ssrc_rtx_attr2 = new org.murillo.sdp.SSRCAttribute(_camera_sim_ssrc_rtx_2, "cname", _cname);
        mz_md.addAttribute(mz_ssrc_rtx_attr2);
        org.murillo.sdp.SSRCAttribute mz_ssrc_rtx_mid_tid_2 = new org.murillo.sdp.SSRCAttribute(_camera_sim_ssrc_rtx_2, "msid", tmp_msid);
        mz_md.addAttribute(mz_ssrc_rtx_mid_tid_2);


        ArrayList<Long> fid_ssrcs3 = new ArrayList<>();
        fid_ssrcs3.add(_camera_sim_ssrc_3);
        fid_ssrcs3.add(_camera_sim_ssrc_rtx_3);
        org.murillo.sdp.SSRCGroupAttribute ssrcGroupAttr3 = new org.murillo.sdp.SSRCGroupAttribute("FID", fid_ssrcs3);
        mz_md.addAttribute(ssrcGroupAttr3);

        org.murillo.sdp.SSRCAttribute mz_ssrc_attr3 = new org.murillo.sdp.SSRCAttribute(_camera_sim_ssrc_3, "cname", _cname);
        mz_md.addAttribute(mz_ssrc_attr3);
        org.murillo.sdp.SSRCAttribute mz_ssrc_mid_tid_3 = new org.murillo.sdp.SSRCAttribute(_camera_sim_ssrc_3, "msid", tmp_msid);
        mz_md.addAttribute(mz_ssrc_mid_tid_3);

        org.murillo.sdp.SSRCAttribute mz_ssrc_rtx_attr3 = new org.murillo.sdp.SSRCAttribute(_camera_sim_ssrc_rtx_3, "cname", _cname);
        mz_md.addAttribute(mz_ssrc_rtx_attr3);
        org.murillo.sdp.SSRCAttribute mz_ssrc_rtx_mid_tid_3 = new org.murillo.sdp.SSRCAttribute(_camera_sim_ssrc_rtx_3, "msid", tmp_msid);
        mz_md.addAttribute(mz_ssrc_rtx_mid_tid_3);
    }


    private VideoCapturer create_video_captuer() {
        return _rtc_sdk.RTCPeerConnection_CreateCameraCapturer();
    }

    private VideoTrack create_video_track(VideoCapturer capturer) {
        _camera_track_id = RandomGeneratorImpl.CreateRandomUuid();
        return _rtc_sdk.RTCPeerConnection_CreateVideoTrack(capturer, _camera_track_id);
    }

    private RtpTransceiver add_video_transceiver(VideoTrack track) {
        _camera_stream_id = RandomGeneratorImpl.CreateRandomUuid();
        List<String> streamIds = new ArrayList<>();
        streamIds.add(_camera_stream_id);

        RtpTransceiver.RtpTransceiverInit init =
                new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, streamIds);
        return _rtc_sdk.RTCPeerConnection_AddTransceiver(track, init);
    }

    private AudioTrack create_audio_track() {
        _audio_track_id = RandomGeneratorImpl.CreateRandomUuid();
        Logging.d(TAG, "audio_track_id " + _audio_track_id);
        return _rtc_sdk.RTCPeerConnection_CreateAudioTrack(false, _audio_track_id);
    }

    private RtpTransceiver add_audio_transceiver(AudioTrack track) {
        _audio_stream_id = RandomGeneratorImpl.CreateRandomUuid();
        Logging.d(TAG, "audio_stream_id "+ _audio_stream_id);
        List<String> streamIds = new ArrayList<>();
        streamIds.add(_audio_stream_id);

        RtpTransceiver.RtpTransceiverInit init =
                new RtpTransceiver.RtpTransceiverInit(
                        RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, streamIds);
        return _rtc_sdk.RTCPeerConnection_AddTransceiver(track, init);
    }

    private DataChannel create_datachannel(String label) {
        RTCCoreSDK.RTCDataChannelParameters param = new RTCCoreSDK.RTCDataChannelParameters(
                true, -1,-1, "extra_protocol", false, -1);
        return _rtc_sdk.RTCPeerConnection_CreateDataChannel(label, param);
    }


    private void set_local_description_h(SessionDescription localSdp) {
        SignalingMessage sig_msg = pending_signaling_messages.peekFirst();
        sig_msg.sdp = localSdp;

//        Logging.d(TAG, localSdp.description);

        rtc_executor.execute(()-> {
            _rtc_sdk.RTCPeerConnection_SetLocalDescription(localSdp, new SdpObserver(){

                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                }
                @Override
                public void onSetSuccess() {
                    Message msg = signalingMessageHandlerThread.getThreadHandler().obtainMessage(SignalingMessageHandlerThread.kWhatSetLocalDescriptionSuccess);
                    signalingMessageHandlerThread.getThreadHandler().sendMessage(msg);
                }

                @Override
                public void onCreateFailure(String error) {}
                @Override
                public void onSetFailure(String error) {
                    Logging.d(TAG,"----------->SdpObserver::onSetFailure");
                }
            });
        });
    }

    private boolean create_session() {
        RTCClientWebsocketTransport transport = new RTCClientWebsocketTransport();
        session = new RTCClientSession(this);
        session.setTransport(transport);
        return true;
    }

    private void signaling_connect() {
        signal_executor.execute(()->{
            String wsUrl = "ws://" + signaling_server_ip + ":" + signaling_server_port;
            session.openTransport(wsUrl);
        });
    }

    private void toggleCallControlFragmentVisibility() {
		// Show/hide call control fragment
        if(!isConnected) {
            return;
        }
    	hudControlFragmentVisible = !hudControlFragmentVisible;
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		if (hudControlFragmentVisible) {
            ft.show(hudFragment);
            enableStatsEvents(true, 500);
		} else {
            enableStatsEvents(false, 0);
		    ft.hide(hudFragment);
		}
		ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
		ft.commit();
    }

    private void on_regist_server(List<PeerConnection.IceServer> iceServerList) {
        create_peer_connection(iceServerList);
        create_init_tracks();
        _is_registed_response = true;

        if(!pending_signaling_messages.isEmpty()) {
            Message msg = signalingMessageHandlerThread.getThreadHandler().obtainMessage(SignalingMessageHandlerThread.kWhatSignalingMessageProcessing);
            signalingMessageHandlerThread.getThreadHandler().sendMessage(msg);
        }
    }

    private void on_remote_ice_candidate_h(IceCandidate candidate) {
        rtc_executor.execute(()-> {
            if (_is_set_remote_description) {
                _rtc_sdk.RTCPeerConnection_AddIceCandidate(candidate);
            } else {
                queuedRemoteCandidates.add(candidate);
            }
        });
    }

    private void on_remote_session_description_h(SignalingMessage tmp_sig_msg) {
        SignalingMessage sig_msg = pending_signaling_messages.peekFirst();
        if(sig_msg.category != tmp_sig_msg.category || sig_msg.cookie != tmp_sig_msg.cookie) {
            // thinking invalid message
            return;
        }
        sig_msg.command = tmp_sig_msg.command;
        sig_msg.sdp = tmp_sig_msg.sdp;

//        Logging.d(TAG, sig_msg.sdp.description);

        rtc_executor.execute(()-> {
            _rtc_sdk.RTCPeerConnection_SetRemoteDescription(sig_msg.sdp, new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sdp) {}
                @Override
                public void onSetSuccess() {
                    Message msg = signalingMessageHandlerThread.getThreadHandler().obtainMessage(SignalingMessageHandlerThread.kWhatSetRemoteDescriptionSuccess);
                    signalingMessageHandlerThread.getThreadHandler().sendMessage(msg);
                }

                @Override
                public void onCreateFailure(String error) {}
                @Override
                public void onSetFailure(String error) {
                    Log.d(TAG, "on_remote_session_description_h onSetFailure " + error);
                }
            });
        });
    }

    private void on_set_remote_session_description_h() {
        if(_is_set_remote_description == false) {
            _is_set_remote_description  = true;
            for(IceCandidate candidate :queuedRemoteCandidates) {
                _rtc_sdk.RTCPeerConnection_AddIceCandidate(candidate);
            }
            queuedRemoteCandidates.clear();
        }

        SignalingMessage sig_msg = pending_signaling_messages.peekFirst();
        if(sig_msg.category == kRTCSignalingCategory_VideoAdd) {
            session.sendMessage(sig_msg.category, kRTCSignalingCommand_Res_Answer_Ack, sig_msg.cookie, null);
        }
        pending_signaling_messages.removeFirst();

        if(!pending_signaling_messages.isEmpty()) {
            Message post_msg = signalingMessageHandlerThread.getThreadHandler().obtainMessage(SignalingMessageHandlerThread.kWhatSignalingMessageProcessing);
            signalingMessageHandlerThread.getThreadHandler().sendMessage(post_msg);
        }
    }

    private void on_local_ice_candidate_h(IceCandidate candidate) {
        session.sendLocalIceCandidate(candidate);
    }

    ////////////////////////////////////////////////////////
    private void regist() {
        session.regist(_is_unified_plan);
    }

    private void disconnect() {
        if(session != null) {
            session.closeSession();
            session = null;
        }

        if (local_renderer != null) {
            local_renderer.release();
            local_renderer = null;
        }

        synchronized (remote_video_track_infos_lock) {
            for(ConcurrentHashMap.Entry<String, RemoteVideoTrackInfo> entry : remote_video_track_infos.entrySet()) {
                RemoteVideoTrackInfo remoteVideoTrackInfo = ((RemoteVideoTrackInfo)entry.getValue());
                if(remoteVideoTrackInfo._renderer != null) {
                    remoteVideoTrackInfo._proxy_video_sink.setTarget(null);
                    remote_renderes_pool.add(remoteVideoTrackInfo._renderer);
                }
                remoteVideoTrackInfo._video_track.removeSink(remoteVideoTrackInfo._proxy_video_sink);
            }

            remote_video_track_infos.clear();

            for (SurfaceViewRenderer renderer : remote_renderes_pool) {
                renderer.release();
            }
            remote_renderes_pool.clear();
        }

        //////////////////////////////////////////////////////////
        // thiking....
        if(_camera_capturer != null) {
            _camera_capturer.dispose();
            _camera_capturer = null;
        }

        if(_data_channel != null) {
            _rtc_sdk.RTCPeerConnection_DisposeDataChannel(_data_channel);
            _data_channel = null;
        }

        if(_audio_track != null) {
            _rtc_sdk.RTCPeerConnection_DisposeAudioTrack(_audio_track);
            _audio_track = null;
        }

        if(_camera_track != null) {
            _rtc_sdk.RTCPeerConnection_DisposeVideoTrack(_camera_track);
            _camera_track = null;
        }

        if(_rtc_sdk != null) {
            _rtc_sdk.RTCPeerConnection_Close();
            _rtc_sdk.RTCPeerConnectionFactory_Dispose();
            _rtc_sdk = null;
        }
        /////////////////////////////////////////////////////////////

        if (audioManager != null) {
            audioManager.stop();
            audioManager = null;
        }

        finish();
    }

    private void disconnectWithErrorMessage(final String errorMessage) {
        new AlertDialog.Builder(this)
                .setTitle(getText(R.string.channel_error_title))
                .setMessage(errorMessage)
                .setCancelable(false)
                .setNeutralButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                disconnect();
                            }
                        })
                .create()
                .show();
    }

    private void reportError(final String description) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isError) {
                    isError = true;
                    disconnectWithErrorMessage(description);
                }
            }
        });
    }

    private void logAndToast(String msg) {
        Log.d(TAG, msg);
        if (logToast != null) {
            logToast.cancel();
        }
        logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        logToast.show();
    }

    ///////////////////////////////////////////////////////////////////////////////////
    // Activity interfaces
    @Override
    public void onStop() {
        super.onStop();
        // Don't stop the video when using screencapture to allow user to show other apps to the remote
        // end.
        rtc_executor.execute(()->{
            if(_camera_capturer != null) {
                try {
                    _camera_capturer.stopCapture();
                } catch (InterruptedException e) {

                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        // Video is not paused for screencapture. See onPause.
        rtc_executor.execute(()->{
            if(_camera_capturer != null) {
                _camera_capturer.startCapture(kWidth, kHeigth, kFps);
            }
        });
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // PeerConnection.Observer
    @Override
    public void onSignalingChange(PeerConnection.SignalingState newState) {

    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {

    }

    @Override
    public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
        if(newState == PeerConnection.PeerConnectionState.CONNECTED ) {
            isConnected = true;
        } else if(newState == PeerConnection.PeerConnectionState.DISCONNECTED ||
            newState == PeerConnection.PeerConnectionState.FAILED) {
            isConnected = false;
            enableStatsEvents(false, 0);
        }
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {

    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {

    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        Message msg = signalingMessageHandlerThread.getThreadHandler().obtainMessage(SignalingMessageHandlerThread.kWhatRemoteCandidate, candidate);
        signalingMessageHandlerThread.getThreadHandler().sendMessage(msg);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {

    }

    @Override
    public void onAddStream(MediaStream stream) {
    }

    @Override
    public void onRemoveStream(MediaStream stream) {
        String streamId = stream.getId();
        synchronized (remote_video_track_infos_lock) {
            RemoteVideoTrackInfo remoteVideoTrackInfo = remote_video_track_infos.get(streamId);
            if (remoteVideoTrackInfo != null) {
                remoteVideoTrackInfo._proxy_video_sink.setTarget(null);
                remoteVideoTrackInfo._video_track.removeSink(remoteVideoTrackInfo._proxy_video_sink);
                if (remoteVideoTrackInfo._renderer != null) {
                    remoteVideoTrackInfo._renderer.clearImage();
                    remote_renderes_pool.add(remoteVideoTrackInfo._renderer);
                }
                remote_video_track_infos.remove(streamId);
            } else {
                return;
            }

            if(!pending_remote_video_streams.isEmpty()) {
                String pending_streamId = pending_remote_video_streams.get(0);
                RemoteVideoTrackInfo pending_remoteVideoTrackInfo = remote_video_track_infos.get(pending_streamId);
                if(pending_remoteVideoTrackInfo != null) {
                    pending_remoteVideoTrackInfo._renderer = remote_renderes_pool.get(0);
                    remote_renderes_pool.remove(0);
                    pending_remoteVideoTrackInfo._proxy_video_sink.setTarget(pending_remoteVideoTrackInfo._renderer);
                }

                pending_remote_video_streams.remove(0);
            }
        }
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {

    }

    @Override
    public void onRenegotiationNeeded() {

    }

    @Override
    public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
    }

    @Override
    public void onTrack(RtpTransceiver transceiver) {
        if(transceiver.getMediaType() == MEDIA_TYPE_VIDEO) {

            RemoteVideoTrackInfo remoteVideoTrackInfo = new RemoteVideoTrackInfo();
            remoteVideoTrackInfo._transceiver = transceiver;

            RtpReceiver receiver = transceiver.getReceiver();
            RtpParameters rtpParameters = receiver.getParameters();
            String streamId = String.valueOf(rtpParameters.encodings.get(0).ssrc);

            remoteVideoTrackInfo._proxy_video_sink = new RTCProxyVideoSink();

            MediaStreamTrack streamTrack = receiver.track();
            remoteVideoTrackInfo._video_track = (VideoTrack)streamTrack;
            remoteVideoTrackInfo._video_track.addSink(remoteVideoTrackInfo._proxy_video_sink);

            synchronized (remote_video_track_infos_lock) {
                if (!remote_renderes_pool.isEmpty()) {
                    remoteVideoTrackInfo._renderer = remote_renderes_pool.get(0);
                    remote_renderes_pool.remove(0);
                    remoteVideoTrackInfo._proxy_video_sink.setTarget(remoteVideoTrackInfo._renderer);
                } else {
                    pending_remote_video_streams.add(streamId);
                }

                remote_video_track_infos.put(streamId, remoteVideoTrackInfo);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////
    // RTCSignalingEvents method
    @Override
    public void onSignalConnected() {
        Message msg = signalingMessageHandlerThread.getThreadHandler().obtainMessage(SignalingMessageHandlerThread.kWhatRegistReq);
        signalingMessageHandlerThread.getThreadHandler().sendMessage(msg);
    }

    @Override
    public void onSignalDisconnected() {
        Log.d(TAG,"signal onSignalDisconnected");
    }

    @Override
    public void onRegisted(List<PeerConnection.IceServer> iceServerList) {
        Log.d(TAG,"[signal] onRegisted");
        Message msg = signalingMessageHandlerThread.getThreadHandler().obtainMessage(SignalingMessageHandlerThread.kWhatRegistedRes,
                iceServerList);
        signalingMessageHandlerThread.getThreadHandler().sendMessage(msg);
    }

    @Override
    public void onRemoteDescription(RTCSignalingCategory category, RTCSignalingCommand command, long cookie, SessionDescription sdp) {
        Log.d(TAG,"[signal] onRemoteDescription");
        SignalingMessage tmp_sig_msg = new SignalingMessage(category, command, cookie, 0);
        tmp_sig_msg.sdp = sdp;

        Message msg = signalingMessageHandlerThread.getThreadHandler().obtainMessage(SignalingMessageHandlerThread.kWhatRemoteDescription, tmp_sig_msg);
        signalingMessageHandlerThread.getThreadHandler().sendMessage(msg);
    }

    @Override
    public void onRemoteIceCandidate(IceCandidate candidate) {
        Log.d(TAG,"[signal] onRemoteIceCandidate");
        Message msg = signalingMessageHandlerThread.getThreadHandler().obtainMessage(SignalingMessageHandlerThread.kWhatRemoteCandidate, candidate);
        signalingMessageHandlerThread.getThreadHandler().sendMessage(msg);
    }

    @Override
    public void onOfferRequest(RTCSignalingCategory category, RTCSignalingCommand command, long cookie, int addCount) {
        SignalingMessage sig_msg = new SignalingMessage(category, command, cookie, addCount);
        Message msg = signalingMessageHandlerThread.getThreadHandler().obtainMessage(SignalingMessageHandlerThread.kWhatSignalingMessagePush, sig_msg);
        signalingMessageHandlerThread.getThreadHandler().sendMessage(msg);
    }

    // CallFragment.OnCallEvents
    @Override
    public void onCallHangUp() {
        enableStatsEvents(false, 0);
        disconnect();
    }

    @Override
    public void onCameraSwitch() {
    }

    @Override
    public void onVideoScalingSwitch(RendererCommon.ScalingType scalingType) {
    }

    @Override
    public boolean onToggleMic() {
        return false;
    }
////////////////////////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////////////////////////

    private void createRemoteRenderesPool() {
        remote_renderes_pool = new ArrayList<>();

        SurfaceViewRenderer tmp1 = findViewById(R.id.remote_renderer_1);
        tmp1.init(eglBase.getEglBaseContext(), null);
        tmp1.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        tmp1.setKeepScreenOn(true);
        remote_renderes_pool.add(tmp1);

        SurfaceViewRenderer tmp2 = findViewById(R.id.remote_renderer_2);
        tmp2.init(eglBase.getEglBaseContext(), null);
        tmp2.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        tmp2.setKeepScreenOn(true);
        remote_renderes_pool.add(tmp2);

        SurfaceViewRenderer tmp3 = findViewById(R.id.remote_renderer_3);
        tmp3.init(eglBase.getEglBaseContext(), null);
        tmp3.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        tmp3.setKeepScreenOn(true);
        remote_renderes_pool.add(tmp3);

        SurfaceViewRenderer tmp4 = findViewById(R.id.remote_renderer_4);
        tmp4.init(eglBase.getEglBaseContext(), null);
        tmp4.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        tmp4.setKeepScreenOn(true);
        remote_renderes_pool.add(tmp4);

        SurfaceViewRenderer tmp5 = findViewById(R.id.remote_renderer_5);
        tmp5.init(eglBase.getEglBaseContext(), null);
        tmp5.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        tmp5.setKeepScreenOn(true);
        remote_renderes_pool.add(tmp5);

        SurfaceViewRenderer tmp6 = findViewById(R.id.remote_renderer_6);
        tmp6.init(eglBase.getEglBaseContext(), null);
        tmp6.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        tmp6.setKeepScreenOn(true);
        remote_renderes_pool.add(tmp6);

        SurfaceViewRenderer tmp7 = findViewById(R.id.remote_renderer_7);
        tmp7.init(eglBase.getEglBaseContext(), null);
        tmp7.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        tmp7.setKeepScreenOn(true);
        remote_renderes_pool.add(tmp7);

        SurfaceViewRenderer tmp8 = findViewById(R.id.remote_renderer_8);
        tmp8.init(eglBase.getEglBaseContext(), null);
        tmp8.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        tmp8.setKeepScreenOn(true);
        remote_renderes_pool.add(tmp8);

        SurfaceViewRenderer tmp9 = findViewById(R.id.remote_renderer_9);
        tmp9.init(eglBase.getEglBaseContext(), null);
        tmp9.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        tmp9.setKeepScreenOn(true);
        remote_renderes_pool.add(tmp9);
    }

//////////////////////////////////////////////////////////////////////////////////////////////
// new method
    private void signaling_message_push_h(SignalingMessage sig_msg) {
        boolean is_notify  = false;
        if(pending_signaling_messages.isEmpty()){
            is_notify = true;
        }
//        Log.d(TAG, "signaling_message_push_h pending_signaling_messages size " + pending_signaling_messages.size() + " is_notify "+ is_notify + " category " + sig_msg.category + " command " + sig_msg.command);

        if(_is_registed_response == false) {
            is_notify = false;
        }
        pending_signaling_messages.addLast(sig_msg);

        if(is_notify) {
            Message msg = signalingMessageHandlerThread.getThreadHandler().obtainMessage(SignalingMessageHandlerThread.kWhatSignalingMessageProcessing);
            signalingMessageHandlerThread.getThreadHandler().sendMessage(msg);
        }
    }

    private void signaling_message_processing_h() {
        SignalingMessage sig_msg = pending_signaling_messages.peekFirst();
        sig_msg.in_process = true;

//        Log.d(TAG, "---------------------> signaling_message_processing_h pending_signaling_messages size " + pending_signaling_messages.size() + " category " + sig_msg.category + " command " + sig_msg.command);

        if(sig_msg.category == kRTCSignalingCategory_AudioInit) {
            create_audio_receivers();
        } else if (sig_msg.category == kRTCSignalingCategory_VideoAdd) {
            create_video_receivers(sig_msg.addCont);
        } else if (sig_msg.category == kRTCSignalingCategory_VideoRemove) {
//            Log.d(TAG, "[signal] signaling_message_processing_h kRTCSignalingCategory_VideoRemove");
        }

        Message msg = signalingMessageHandlerThread.getThreadHandler().obtainMessage(SignalingMessageHandlerThread.kWhatCreateOffer);
        signalingMessageHandlerThread.getThreadHandler().sendMessage(msg);
    }

    private void set_local_description_success_h() {
        SignalingMessage sig_msg = pending_signaling_messages.peekFirst();
        sig_msg.command = kRTCSignalingCommand_Res_Offer;
//        Log.d(TAG, "set_local_description_success_h category " + sig_msg.category + " command "+ sig_msg.command);

        if(!is_init_max_bitrate) {
            // set init max bitrate
            _rtc_sdk.RTCPeerConnection_setMaxBitrateSenders(kWidth, kHeigth);
            is_init_max_bitrate = true;
        }

        session.sendMessage(sig_msg.category, sig_msg.command, sig_msg.cookie, sig_msg.sdp.description);
    }


    private void create_audio_receivers() {
        RtpTransceiver.RtpTransceiverInit init = new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY);
        for(int i = 0; i < kMaxAudioReceivers; i++) {
            RtpTransceiver transceiver = _rtc_sdk.RTCPeerConnection_AddTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, init);
            //           dump_transceiver(transceiver);
        }
    }

    private void create_video_receivers(int addCount) {
        RtpTransceiver.RtpTransceiverInit init = new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY);
        for(int i = 0; i < addCount; i++) {
            RtpTransceiver transceiver = _rtc_sdk.RTCPeerConnection_AddTransceiver(MEDIA_TYPE_VIDEO, init);
            //           dump_transceiver(transceiver);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
// dump
    private void dump_transceivers() {
        rtc_executor.execute(()->{
            List<RtpTransceiver> transceivers = _rtc_sdk.RTCPeerConnection_GetTransceivers();
            Log.d(TAG, "dump_transceivers size " + transceivers.size() + " +++");
            for(RtpTransceiver transceiver : transceivers) {
                dump_transceiver(transceiver);
            }
            Log.d(TAG, "dump_transceivers size " + transceivers.size() + " ---");
        });
    }

    private void dump_transceiver(RtpTransceiver transceiver) {
        RtpSender sender = transceiver.getSender();
        RtpReceiver receiver = transceiver.getReceiver();
        Log.d(TAG, "media_type " + transceiver.getMediaType() + " mid "+ transceiver.getMid() + " direction " + transceiver.getDirection() +  " sender id " + sender.id() + " receiver id "+ receiver.id() );
        RtpParameters rParameters = receiver.getParameters();
        if(!rParameters.encodings.isEmpty()) {
            Log.d(TAG, " receiver rtp parameters " + rParameters.encodings.size() + " ssrc " + rParameters.encodings.get(0).ssrc);
        }

        RtpParameters sParameters = sender.getParameters();
        if(!sParameters.encodings.isEmpty()) {
            Log.d(TAG, " sender rtp parameters " + sParameters.encodings.size() + " ssrc " + sParameters.encodings.get(0).ssrc);
        }
    }


    private void dump_sdp(SessionDescription sdp, boolean is_local) {
        Logging.d(TAG, "+++++++++++++++++++ dump " + (is_local ? "local" : "remote") + " description +++++++++++++++++++");
        Logging.d(TAG, sdp.description);
        Logging.d(TAG, "------------------- dump " + (is_local ? "local" : "remote") + " description -------------------");
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    //  RTCStatsCollectorCallback
    @Override
    public void onStatsDelivered(RTCStatsReport report) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (hudControlFragmentVisible) {
                    hudFragment.rtcStatReportCallabck(report);
                }
            }
        });
    }

    private void NewGetStats() {
        rtc_executor.execute(() -> {
            if(_rtc_sdk != null) {
                _rtc_sdk.RTCPeerConnection_GetStats(this);
            }
        });
    }

    private void enableStatsEvents(boolean enable, int periodMs) {
        if(isStatsEnable == enable)
            return;

        isStatsEnable = enable;
        if (isStatsEnable) {
            try {
                statsTimer = new Timer();
                statsTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        NewGetStats();
                    }
                }, 0, periodMs);
            } catch (Exception e) {
                Log.e(TAG, "Can not schedule statistics timer", e);
            }
        } else {
            statsTimer.cancel();
        }
    }
}