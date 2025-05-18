package com.aone.sfuapp;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCStatsCollectorCallback;
import org.webrtc.RtpParameters;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SoftwareVideoDecoderFactory;
import org.webrtc.SoftwareVideoEncoderFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.IdentityHashMap;
import java.util.List;

import androidx.annotation.Nullable;

public class RTCCoreSDK {

    public static class RTCCodecFactoryParameters {
        public final String videoCodec;
        public final String audioCodec;
        public final boolean encVideoCodecHwAcceleration;
        public final boolean decVideoCodecHwAcceletation;

        public RTCCodecFactoryParameters(String videoCodec, String audioCodec,
                                         boolean encVideoCodecHwAcceleration,
                                         boolean decVideoCodecHwAcceletation) {
            this.videoCodec = videoCodec;
            this.audioCodec = audioCodec;
            this.encVideoCodecHwAcceleration = encVideoCodecHwAcceleration;
            this.decVideoCodecHwAcceletation = decVideoCodecHwAcceletation;
        }
    }

    public static class RTCDataChannelParameters {
        public final boolean ordered;
        public final int maxRetransmitTimeMs;
        public final int maxRetransmits;
        public final String protocol;
        public final boolean negotiated;
        public final int id;

        public RTCDataChannelParameters(boolean ordered, int maxRetransmitTimeMs, int maxRetransmits,
                                        String protocol, boolean negotiated, int id) {
            this.ordered = ordered;
            this.maxRetransmitTimeMs = maxRetransmitTimeMs;
            this.maxRetransmits = maxRetransmits;
            this.protocol = protocol;
            this.negotiated = negotiated;
            this.id = id;
        }
    }

    public interface RTCAudioDeviceMuduleObserver {
        void onRTCAudioRecordStart();
        void onRTCAudioRecordStop();

        void onRTCAudioTrackStart();
        void onRTCAudioTrackStop();

        void onRTCAudioRecordInitError(String errorMessage);
        void onRTCAudioRecordStartError(String errorMessage);
        void onRTCAudioRecordError(String errorMessage);

        void onRTCAudioTrackInitError(String errorMessage);
        void onRTCAudioTrackStartError(String errorMessage);
        void onRTCAudioTrackError(String errorMessage);
    }

    private static final String TAG = "RTCCoreSDK";

    private static boolean _is_factory_initialzied = false;

    private final Context _ctx;
    private final EglBase _rootEglBase;

    @Nullable
    private PeerConnectionFactory _factory;
    @Nullable
    private PeerConnection _peerConnection;

    @Nullable
    private RTCAudioDeviceMuduleObserver _adm_observer;

    private final IdentityHashMap<VideoTrack, VideoSource> _videoSources = new IdentityHashMap<VideoTrack, VideoSource>();
    private final IdentityHashMap<VideoTrack, SurfaceTextureHelper> _surface_texture_handlers = new IdentityHashMap<VideoTrack, SurfaceTextureHelper>();
    private final IdentityHashMap<AudioTrack, AudioSource> _audioSources = new IdentityHashMap<AudioTrack, AudioSource>();

    // peerconnection factory initialize
    public static void RTCPeerConnectionFactory_Initialize(Context appContext, String fieldTrials, boolean is_tracer) {
        if(_is_factory_initialzied == true) {
            return;
        }
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setFieldTrials(fieldTrials)
                .setEnableInternalTracer(is_tracer)
                .createInitializationOptions());
        _is_factory_initialzied = true;
    }

    public RTCCoreSDK(Context appContext, EglBase eglBase) {
        _ctx = appContext;
        _rootEglBase = eglBase;
    }

    public AudioDeviceModule RTCAudioDeviceModule_Create(boolean disableBuiltInAEC,
                                                         boolean disableBuiltInNS,
                                                         RTCAudioDeviceMuduleObserver observer) {
        _adm_observer = observer;
        // Set audio record error callbacks.
        JavaAudioDeviceModule.AudioRecordErrorCallback audioRecordErrorCallback = new JavaAudioDeviceModule.AudioRecordErrorCallback() {
            @Override
            public void onWebRtcAudioRecordInitError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordInitError: " + errorMessage);
                if(_adm_observer != null) {
                    _adm_observer.onRTCAudioRecordInitError(errorMessage);
                }
            }

            @Override
            public void onWebRtcAudioRecordStartError(
                    JavaAudioDeviceModule.AudioRecordStartErrorCode errorCode, String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordStartError: " + errorCode + ". " + errorMessage);
                if(_adm_observer != null) {
                    _adm_observer.onRTCAudioRecordStartError(errorMessage);
                }
            }

            @Override
            public void onWebRtcAudioRecordError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordError: " + errorMessage);
                if(_adm_observer != null) {
                    _adm_observer.onRTCAudioRecordError(errorMessage);
                }
            }
        };

        JavaAudioDeviceModule.AudioTrackErrorCallback audioTrackErrorCallback = new JavaAudioDeviceModule.AudioTrackErrorCallback() {
            @Override
            public void onWebRtcAudioTrackInitError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackInitError: " + errorMessage);
                if(_adm_observer != null) {
                    _adm_observer.onRTCAudioTrackInitError(errorMessage);
                }
            }

            @Override
            public void onWebRtcAudioTrackStartError(
                    JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackStartError: " + errorCode + ". " + errorMessage);
                if(_adm_observer != null) {
                    _adm_observer.onRTCAudioTrackStartError(errorMessage);
                }
            }

            @Override
            public void onWebRtcAudioTrackError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackError: " + errorMessage);
                if(_adm_observer != null) {
                    _adm_observer.onRTCAudioTrackError(errorMessage);
                }
            }
        };

        // Set audio record state callbacks.
        JavaAudioDeviceModule.AudioRecordStateCallback audioRecordStateCallback = new JavaAudioDeviceModule.AudioRecordStateCallback() {
            @Override
            public void onWebRtcAudioRecordStart() {
                Log.i(TAG, "Audio recording starts");
                if(_adm_observer != null) {
                    _adm_observer.onRTCAudioRecordStart();
                }
            }

            @Override
            public void onWebRtcAudioRecordStop() {
                Log.i(TAG, "Audio recording stops");
                if(_adm_observer != null) {
                    _adm_observer.onRTCAudioRecordStop();
                }
            }
        };

        // Set audio track state callbacks.
        JavaAudioDeviceModule.AudioTrackStateCallback audioTrackStateCallback = new JavaAudioDeviceModule.AudioTrackStateCallback() {
            @Override
            public void onWebRtcAudioTrackStart() {
                Log.i(TAG, "Audio playout starts");
                if(_adm_observer != null) {
                    _adm_observer.onRTCAudioTrackStart();
                }
            }

            @Override
            public void onWebRtcAudioTrackStop() {
                Log.i(TAG, "Audio playout stops");
                if(_adm_observer != null) {
                    _adm_observer.onRTCAudioTrackStop();
                }
            }
        };

        return JavaAudioDeviceModule.builder(_ctx)
                .setUseHardwareAcousticEchoCanceler(!disableBuiltInAEC)
                .setUseHardwareNoiseSuppressor(!disableBuiltInNS)
                .setAudioRecordErrorCallback(audioRecordErrorCallback)
                .setAudioTrackErrorCallback(audioTrackErrorCallback)
                .setAudioRecordStateCallback(audioRecordStateCallback)
                .setAudioTrackStateCallback(audioTrackStateCallback)
                .createAudioDeviceModule();
    }

    public boolean RTCPeerConnectionFactory_Create(AudioDeviceModule adm,
                                               PeerConnectionFactory.Options options,
                                               RTCCodecFactoryParameters params) {
        final boolean enableH264HighProfile =
                RTCCodecs.VIDEO_CODEC_H264_HIGH.equals(params.videoCodec);
        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        if(params.encVideoCodecHwAcceleration) {
            Log.i(TAG, "DefaultVideoEncoderFactory");
            encoderFactory = new DefaultVideoEncoderFactory(
                    _rootEglBase.getEglBaseContext(), true /* enableIntelVp8Encoder */, enableH264HighProfile);
        } else {
            Log.d(TAG, "SoftwareVideoEncoderFactory");
            encoderFactory = new SoftwareVideoEncoderFactory();
        }

        if(params.decVideoCodecHwAcceletation) {
            Log.i(TAG, "DefaultVideoDecoderFactory");
            decoderFactory = new DefaultVideoDecoderFactory(_rootEglBase.getEglBaseContext());
        } else {
            Log.i(TAG, "SoftwareVideoDecoderFactory");
            decoderFactory = new SoftwareVideoDecoderFactory();
        }

        _factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
        if(_factory != null) {
            adm.release();
            return true;
        }
        return false;
    }

    public boolean RTCPeerConnection_Create(PeerConnection.RTCConfiguration rtcConfig,
                                            PeerConnection.Observer observer) {
        if(_factory == null) {
            return false;
        }
        _peerConnection = _factory.createPeerConnection(rtcConfig, observer);
        if(_peerConnection == null) {
            return false;
        }
        return true;
    }

    public static void RTCPeerConnection_SetLoggingSeverity(Logging.Severity serverity) {
        Logging.enableLogToDebugOutput(serverity);
    }

    public void RTCPeerConnection_Close() {
        if(_peerConnection != null) {
            _peerConnection.dispose();
            _peerConnection = null;
        }
    }

    public void RTCPeerConnectionFactory_Dispose() {
        if(_factory != null) {
            _factory.dispose();
            _factory = null;
        }

        if(_rootEglBase != null) {
            _rootEglBase.release();
         }
    }

    @Nullable
    public VideoCapturer RTCPeerConnection_CreateCameraCapturer() {
        final VideoCapturer videoCapturer;
        if(useCamera2()) {
            videoCapturer = createCameraCapture(new Camera2Enumerator(_ctx));
        } else {
            videoCapturer = createCameraCapture(new Camera1Enumerator(true));
        }

        return videoCapturer;
    }

    @Nullable
    public VideoTrack RTCPeerConnection_CreateVideoTrack(VideoCapturer capturer,
                                                         String trackId) {
        if(_factory == null) {
            return null;
        }
        SurfaceTextureHelper surfaceTextureHelper =
                SurfaceTextureHelper.create("captureThread", _rootEglBase.getEglBaseContext());
        VideoSource videoSource = _factory.createVideoSource(capturer.isScreencast());
        if(surfaceTextureHelper == null || videoSource == null) {
            return null;
        }

        capturer.initialize(surfaceTextureHelper, _ctx, videoSource.getCapturerObserver());
        VideoTrack videoTrack = _factory.createVideoTrack(trackId, videoSource);
        if(videoTrack == null) {
            return null;
        }
        _videoSources.put(videoTrack, videoSource);
        _surface_texture_handlers.put(videoTrack, surfaceTextureHelper);

        return videoTrack;
    }

    public void RTCPeerConnection_DisposeVideoTrack(VideoTrack videoTrack) {
        VideoSource videoSource = _videoSources.remove(videoTrack);
        SurfaceTextureHelper surfaceTextureHelper = _surface_texture_handlers.remove(videoTrack);

        if(surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
        }

        videoTrack.dispose();

        if(videoSource != null) {
            videoSource.dispose();
        }
    }

    @Nullable
    public AudioTrack RTCPeerConnection_CreateAudioTrack(boolean is_noAudioProcessing, String trackId) {
        if(_factory == null) {
            return null;
        }

        MediaConstraints audioConstraints = new MediaConstraints();
        if(is_noAudioProcessing) {
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(RTCFields.AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(RTCFields.AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(RTCFields.AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(RTCFields.AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false"));
        }
        AudioSource audioSource = _factory.createAudioSource(audioConstraints);
        if(audioSource == null) {
            return null;
        }

        AudioTrack audioTrack = _factory.createAudioTrack(trackId, audioSource);
        if(audioTrack == null) {
            return null;
        }
        _audioSources.put(audioTrack, audioSource);

        return audioTrack;
    }

    public void RTCPeerConnection_DisposeAudioTrack(AudioTrack audioTrack) {
        AudioSource audioSource = _audioSources.remove(audioTrack);
        audioTrack.dispose();
        if(audioSource != null) {
            audioSource.dispose();
        }
    }

    public DataChannel RTCPeerConnection_CreateDataChannel(String label, RTCDataChannelParameters dcParams) {
        DataChannel.Init init = new DataChannel.Init();
        init.ordered = dcParams.ordered;
        init.negotiated = dcParams.negotiated;
        init.maxRetransmits = dcParams.maxRetransmits;
        init.maxRetransmitTimeMs = dcParams.maxRetransmitTimeMs;
        init.id = dcParams.id;
        init.protocol = dcParams.protocol;

        return _peerConnection.createDataChannel(label, init);
    }

    public void RTCPeerConnection_DisposeDataChannel(DataChannel dc) {
        dc.dispose();
    }

    @Nullable
    public RtpTransceiver RTCPeerConnection_AddTransceiver(MediaStreamTrack track,
                                                 @Nullable RtpTransceiver.RtpTransceiverInit init) {
        return _peerConnection.addTransceiver(track, init);
    }

    @Nullable
    public RtpTransceiver RTCPeerConnection_AddTransceiver(MediaStreamTrack.MediaType mediaType,
                                                           @Nullable RtpTransceiver.RtpTransceiverInit init) {
        return _peerConnection.addTransceiver(mediaType, init);
    }

    public void RTCPeerConnection_CreateOffer(MediaConstraints sdpMediaConstraints,
                                              SdpObserver observer) {
        _peerConnection.createOffer(observer, sdpMediaConstraints);
    }

    public void RTCPeerConnection_CreateAnswer(MediaConstraints sdpMediaConstraints,
                                               SdpObserver observer) {
        _peerConnection.createAnswer(observer, sdpMediaConstraints);
    }

    public void RTCPeerConnection_SetLocalDescription(final SessionDescription sdp,
                                                      SdpObserver observer) {
        _peerConnection.setLocalDescription(observer, sdp);
    }

    public void RTCPeerConnection_SetRemoteDescription(final SessionDescription sdp,
                                                       SdpObserver observer) {
        _peerConnection.setRemoteDescription(observer, sdp);
    }

    public void RTCPeerConnection_AddIceCandidate(IceCandidate candidate) {
        _peerConnection.addIceCandidate(candidate);
    }

    public List<RtpTransceiver> RTCPeerConnection_GetTransceivers() {
        return _peerConnection.getTransceivers();
    }

    public void RTCPeerConnection_setMaxBitrateSenders(int src_w, int src_h) {
        int video_ratio_type = 0;   // 0 unknown , 1 hd, 2 fullhd
        if(src_w * src_h == 1280 * 720) {
            video_ratio_type = 1;
        } else if (src_w * src_h == 1920 * 1080) {
            video_ratio_type = 2;
        }

        if(video_ratio_type == 0)
            return;


        for (RtpSender sender : _peerConnection.getSenders()) {
            if (sender.track() != null) {
                String trackType = sender.track().kind();
                if (trackType.equals("video")) {
                    RtpParameters parameters = sender.getParameters();
                    Log.d(TAG, "XXXX  encoding size "+parameters.encodings.size());
                    if(parameters.encodings.size() > 1) {

                        for(int idx = 0; idx < parameters.encodings.size(); idx++) {
                            Log.d(TAG, "XXXX  encoding ssrc "+ parameters.encodings.get(idx).ssrc);

                            if(idx == 2) {
                                parameters.encodings.get(idx).maxBitrateBps = 2500 * 1000;
                                parameters.encodings.get(idx).rid = "h";
                                parameters.encodings.get(idx).scaleResolutionDownBy = 1.0;
                                parameters.encodings.get(idx).maxFramerate = 30;
                            } else if(idx == 1) {
                                parameters.encodings.get(idx).maxBitrateBps = 1000 * 1000;
                                parameters.encodings.get(idx).rid = "m";
                                parameters.encodings.get(idx).scaleResolutionDownBy = 2.0;
                                parameters.encodings.get(idx).maxFramerate = 30;
                            } else {
                                parameters.encodings.get(idx).maxBitrateBps = 300 * 1000;
                                parameters.encodings.get(idx).rid = "l";
                                parameters.encodings.get(idx).scaleResolutionDownBy = 4.0;
                                parameters.encodings.get(idx).maxFramerate = 30;
                            }
                        }
//                         sender.setParameters(parameters);
                    } else if (parameters.encodings.size() == 1) {
                        if(video_ratio_type == 1) {
                            parameters.encodings.get(0).maxBitrateBps = 2000 * 1000;
                        } else  {   // full hd
                            parameters.encodings.get(0).maxBitrateBps = 4000 * 1000;
                        }
                    }
                    sender.setParameters(parameters);
                    break;
                }
            }
        }
    }

    public void RTCPeerConnection_SetActiveSenders(int limit_idx, boolean isOnly) {
        for (RtpSender sender : _peerConnection.getSenders()) {
            if (sender.track() != null) {
                String trackType = sender.track().kind();
                if (trackType.equals("video")) {
                    RtpParameters parameters = sender.getParameters();
                    if(parameters.encodings.size() > 1) {
                        for(int idx = 0; idx < parameters.encodings.size(); idx++) {
                            Log.d(TAG,"XXXX idx "+idx + " ssrc "+ parameters.encodings.get(idx).ssrc + " cur active "+ parameters.encodings.get(idx).active);
                            if(isOnly) {
                                if (idx == limit_idx) {
                                    parameters.encodings.get(idx).active = true;
                                } else {
                                    parameters.encodings.get(idx).active = false;
                                }
                            } else {
                                if (idx <= limit_idx) {
                                    parameters.encodings.get(idx).active = true;
                                } else {
                                    parameters.encodings.get(idx).active = false;
                                }
                            }
                        }
                        sender.setParameters(parameters);
                    }
                    break;
                }
            }
        }
    }

    public void RTCPeerConnection_GetStats(RTCStatsCollectorCallback callback) {
        if(_peerConnection != null) {
            _peerConnection.getStats(callback);
        }
    }

    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(_ctx);
    }

    @Nullable
    private VideoCapturer createCameraCapture(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }
}
