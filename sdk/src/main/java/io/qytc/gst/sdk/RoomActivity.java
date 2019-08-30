package io.qytc.gst.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.tencent.liteav.TXLiteAVCode;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.trtc.TRTCCloud;
import com.tencent.trtc.TRTCCloudDef;
import com.tencent.trtc.TRTCCloudListener;
import com.tencent.trtc.TRTCStatistics;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import io.qytc.gst.bean.DeviceStatusBean;
import io.qytc.gst.dialog.MoreDialog;
import io.qytc.gst.dialog.SettingDialog;
import io.qytc.gst.util.API;
import io.qytc.gst.util.HttpUtil;
import io.qytc.gst.view.TRTCVideoViewLayout;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;


/**
 * Module:   RoomActivity
 * <p>
 * Function: 使用TRTC SDK完成 1v1 和 1vn 的视频通话功能
 * <p>
 * 1. 支持九宫格平铺和前后叠加两种不同的视频画面布局方式，该部分由 TRTCVideoViewLayout 来计算每个视频画面的位置排布和大小尺寸
 * <p>
 * 2. 支持对视频通话的分辨率、帧率和流畅模式进行调整，该部分由 SettingDialog 来实现
 * <p>
 * 3. 创建或者加入某一个通话房间，需要先指定 roomId 和 userId，这部分由 TRTCNewActivity 来实现
 */
public class RoomActivity extends Activity implements View.OnClickListener,
        SettingDialog.ISettingListener,
        MoreDialog.IMoreListener,
        TRTCVideoViewLayout.ITRTCVideoViewLayoutListener {

    private final static String  TAG          = RoomActivity.class.getSimpleName();
    private              Context mContext;
    private              boolean bEnableVideo = true;
    private              boolean bEnableAudio = true;
    private              boolean bEanbleSpeak = false;

    private TextView  tvRoomId;
    private ImageView ivSpeak, ivCamera, ivVoice;
    private SettingDialog       settingDlg;
    private MoreDialog          moreDlg;
    private TRTCVideoViewLayout mVideoViewLayout;

    private AlertDialog             exitDialog;
    private TRTCCloudDef.TRTCParams trtcParams;     /// TRTC SDK 视频通话房间进入所必须的参数
    private TRTCCloud               trtcCloud;              /// TRTC SDK 实例对象
    private TRTCCloudListenerImpl   trtcListener;    /// TRTC SDK 回调监听

    private int mBeautyLevel    = 0;
    private int mWhiteningLevel = 0;
    private int mRuddyLevel     = 0;
    private int mBeautyStyle    = TRTCCloudDef.TRTC_BEAUTY_STYLE_NATURE;
    private int mSdkAppId       = -1;
    private int mAppScene       = TRTCCloudDef.TRTC_APP_SCENE_LIVE;
    private int roomId;

    private String selfUserId = null;
    private String userSig;
    private String deviceStatus;

    private OkHttpClient mOkHttpClient;
    private WebSocket    webSocket;

    private DeviceStatusBean mDeviceStatusBean = new DeviceStatusBean();

    private Handler               mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 1://websocket心跳
                    webSocket.send(deviceStatus);
                    Log.d(TAG, "handleMessage: " + deviceStatus);
                    if (webSocketListener.webSocketConnect) {
                        mHandler.sendEmptyMessageDelayed(1, 3000);
                    }
                    break;
            }
        }
    };
    private EchoWebSocketListener webSocketListener;
    private int                   role;

    private static class VideoStream {
        String userId;
        int    streamType;

        public boolean equals(Object obj) {
            if (obj == null || userId == null) return false;
            VideoStream stream = (VideoStream) obj;
            return (this.streamType == stream.streamType && this.userId.equals(stream.userId));
        }
    }

    private ArrayList<VideoStream> mVideosInRoom = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        //应用运行时，保持屏幕高亮，不锁屏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.room_activity);

        //获取前一个页面得到的进房参数
        Intent intent = getIntent();
        mSdkAppId = intent.getIntExtra("sdkAppId", 0);
        roomId = intent.getIntExtra("roomId", 0);
        selfUserId = intent.getStringExtra("userId");
        userSig = intent.getStringExtra("userSig");
        mAppScene = intent.getIntExtra("appScene", mAppScene);
        role = intent.getIntExtra("role", TRTCCloudDef.TRTCRoleAudience);

        trtcParams = new TRTCCloudDef.TRTCParams(mSdkAppId, selfUserId, userSig, roomId, "", "");
        trtcParams.role = role;
        init();
    }

    private void init() {
        initDeviceStatusBean();
        //启动webSocket
        startWebSocket();
        //初始化 UI 控件
        initView();
        //创建 TRTC SDK 实例
        initTRTC();
        //开始进入视频通话房间
        enterRoom();
    }

    private void initDeviceStatusBean() {
        mDeviceStatusBean.setCmd("keepAlive");
        DeviceStatusBean.DataBean dataBean = new DeviceStatusBean.DataBean();
        dataBean.setAcctno(selfUserId);
        dataBean.setRoomNo(roomId);
        dataBean.setSpeaker(trtcParams.role == TRTCCloudDef.TRTCRoleAnchor ? 1 : 0);
        dataBean.setCamera(trtcParams.role == TRTCCloudDef.TRTCRoleAnchor ? 1 : 0);
        dataBean.setMic(trtcParams.role == TRTCCloudDef.TRTCRoleAnchor ? 1 : 0);
        dataBean.setInRoom(1);
        mDeviceStatusBean.setData(dataBean);
        deviceStatus = JSON.toJSONString(mDeviceStatusBean);

    }

    private void showMsg(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocketListener != null) {
            webSocketListener.webSocketConnect = false;
        }
        if (webSocket != null) {
            webSocket.close(1000, "Normal Closure");
        }
        if (exitDialog != null && exitDialog.isShowing()) {
            exitDialog.dismiss();
        }
        if (trtcCloud != null) {
            trtcCloud.setListener(null);
            TRTCCloud.destroySharedInstance();
        }

    }

    @Override
    public void onBackPressed() {
        exitDialog.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exitDialog != null && exitDialog.isShowing()) {
            exitDialog.dismiss();
        }
    }

    /**
     * 初始化界面控件，包括主要的视频显示View，以及底部的一排功能按钮
     */
    private void initView() {

        initClickableLayout(R.id.ll_speak);
        initClickableLayout(R.id.ll_camera);
        initClickableLayout(R.id.ll_voice);
        initClickableLayout(R.id.ll_role);
        initClickableLayout(R.id.ll_more);

        mVideoViewLayout = findViewById(R.id.ll_mainview);
        mVideoViewLayout.setUserId(trtcParams.userId);
        mVideoViewLayout.setListener(this);

        ivSpeak = findViewById(R.id.iv_speak);
        ivSpeak.setImageResource(role == TRTCCloudDef.TRTCRoleAnchor ? R.mipmap.speak_enable : R.mipmap.speak_disable);

        ivVoice = findViewById(R.id.iv_mic);
        ivVoice.setImageResource(role == TRTCCloudDef.TRTCRoleAnchor ? R.mipmap.remote_audio_enable : R.mipmap.remote_audio_disable);

        ivCamera = findViewById(R.id.iv_camera);
        ivCamera.setImageResource(role == TRTCCloudDef.TRTCRoleAnchor ? R.mipmap.remote_video_enable : R.mipmap.remote_video_disable);

        tvRoomId = findViewById(R.id.tv_room_id);
        tvRoomId.setText("会议室号：" + trtcParams.roomId);

        settingDlg = new SettingDialog(this, this, mAppScene);

        moreDlg = new MoreDialog(this, this);

        findViewById(R.id.rtc_double_room_back_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitDialog.show();
            }
        });

        initExitDialog();
    }

    private void initTRTC() {
        trtcListener = new TRTCCloudListenerImpl(this);
        trtcCloud = TRTCCloud.sharedInstance(this);
        trtcCloud.setListener(trtcListener);
    }

    private LinearLayout initClickableLayout(int resId) {
        LinearLayout layout = findViewById(resId);
        layout.setOnClickListener(this);
        return layout;
    }

    /**
     * 设置视频通话的视频参数：需要 SettingDialog 提供的分辨率、帧率和流畅模式等参数
     */
    private void setTRTCCloudParam() {

        // 大画面的编码器参数设置
        // 设置视频编码参数，包括分辨率、帧率、码率等等，这些编码参数来自于 SettingDialog 的设置
        // 注意（1）：不要在码率很低的情况下设置很高的分辨率，会出现较大的马赛克
        // 注意（2）：不要设置超过25FPS以上的帧率，因为电影才使用24FPS，我们一般推荐15FPS，这样能将更多的码率分配给画质
        TRTCCloudDef.TRTCVideoEncParam encParam = new TRTCCloudDef.TRTCVideoEncParam();
        encParam.videoResolution = settingDlg.getResolution();
        encParam.videoFps = settingDlg.getVideoFps();
        encParam.videoBitrate = settingDlg.getVideoBitrate();
        encParam.videoResolutionMode = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_LANDSCAPE;
        trtcCloud.setVideoEncoderParam(encParam);

        TRTCCloudDef.TRTCNetworkQosParam qosParam = new TRTCCloudDef.TRTCNetworkQosParam();
        qosParam.controlMode = settingDlg.getQosMode();
        qosParam.preference = settingDlg.getQosPreference();
        trtcCloud.setNetworkQosParam(qosParam);

        //小画面的编码器参数设置
        //TRTC SDK 支持大小两路画面的同时编码和传输，这样网速不理想的用户可以选择观看小画面
        //注意：iPhone & Android 不要开启大小双路画面，非常浪费流量，大小路画面适合 Windows 和 MAC 这样的有线网络环境
        TRTCCloudDef.TRTCVideoEncParam smallParam = new TRTCCloudDef.TRTCVideoEncParam();
        smallParam.videoResolution = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_160_90;
        smallParam.videoFps = settingDlg.getVideoFps();
        smallParam.videoBitrate = 100;
        smallParam.videoResolutionMode = settingDlg.isVideoVertical() ? TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_PORTRAIT : TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_LANDSCAPE;
        trtcCloud.enableEncSmallVideoStream(settingDlg.enableSmall, smallParam);

        trtcCloud.setPriorRemoteVideoStreamType(settingDlg.priorSmall ? TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_SMALL : TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);
    }

    /**
     * 加入视频房间：需要 TRTCNewViewActivity 提供的  TRTCParams 函数
     */
    private void enterRoom() {
        // 预览前配置默认参数
        setTRTCCloudParam();

        // 开启视频采集预览
        if (trtcParams.role == TRTCCloudDef.TRTCRoleAnchor) {
            startLocalVideo(true);
        }

        trtcCloud.setBeautyStyle(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);

        if (trtcParams.role == TRTCCloudDef.TRTCRoleAnchor && moreDlg.isEnableAudioCapture()) {
            trtcCloud.startLocalAudio();
        }

//        setVideoFillMode(moreDlg.isVideoFillMode());
        setVideoFillMode(true);
        setVideoRotation(moreDlg.isVideoVertical());
        enableAudioHandFree(moreDlg.isAudioHandFreeMode());
//        enableGSensor(moreDlg.isEnableGSensorMode());
        enableGSensor(true);
        enableAudioVolumeEvaluation(moreDlg.isAudioVolumeEvaluation());
        enableVideoEncMirror(moreDlg.isRemoteVideoMirror());
        setLocalViewMirrorMode(moreDlg.getLocalVideoMirror());

        mVideosInRoom.clear();
        trtcCloud.enterRoom(trtcParams, mAppScene);
    }

    /**
     * 退出视频房间
     */
    private void exitRoom() {

        JSONObject jo = new JSONObject();
        jo.put("roomNo", roomId);
        jo.put("acctno", selfUserId);
        HttpUtil.getInstance(API.EXIT_ROOM).post(jo.toJSONString(), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

            }
        });


        if (trtcCloud != null) {
            trtcCloud.exitRoom();
        }

        finish();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.ll_camera) {
            if (role == TRTCCloudDef.TRTCRoleAnchor) {
                onEnableVideo();
            } else {
                showMsg("请先申请发言");
            }
        } else if (v.getId() == R.id.ll_voice) {
            if (role == TRTCCloudDef.TRTCRoleAnchor) {
                onEnableAudio();
            } else {
                showMsg("请先申请发言");
            }
        } else if (v.getId() == R.id.ll_role) {
            onShowSettingDlg();
        } else if (v.getId() == R.id.ll_more) {
            onShowMoreDlg();
        } else if (v.getId() == R.id.ll_speak) {
            if (role == TRTCCloudDef.TRTCRoleAnchor) {
                cancelSpeak();
            } else {
                requestSpeak();
            }
        }
    }

    private void requestSpeak() {
        JSONObject jo = new JSONObject();
        jo.put("acctno", selfUserId);
        jo.put("roomNo", roomId);

        HttpUtil.getInstance(API.REQUEST_SPEAK).post(jo.toString(), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                showMsg("申请发言请求失败，" + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.code() == 200) {
                    String jsonStr = response.body().string();
                    JSONObject jo = JSON.parseObject(jsonStr);
                    showMsg(jo.getString("msg"));
                } else {
                    showMsg("服务器出现异常");
                }
            }
        });
    }

    private void cancelSpeak() {
        JSONObject jo = new JSONObject();
        jo.put("acctno", selfUserId);
        jo.put("target", selfUserId);
        HttpUtil.getInstance(API.CANCEL_SPEAK).post(jo.toString(), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                showMsg("取消发言请求失败，" + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                mDeviceStatusBean.getData().setSpeaker(0);
                deviceStatus = JSON.toJSONString(mDeviceStatusBean);
                webSocket.send(deviceStatus);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        role = TRTCCloudDef.TRTCRoleAudience;
                        onChangeRole(role);
                        onEnableSpeak(false);
                    }
                });
            }
        });
    }


    /**
     * 开启/关闭视频上行
     */
    private void onEnableVideo() {
        bEnableVideo = !bEnableVideo;
        startLocalVideo(bEnableVideo);
        mVideoViewLayout.updateVideoStatus(trtcParams.userId, bEnableVideo);
        ivCamera.setImageResource(bEnableVideo ? R.mipmap.remote_video_enable : R.mipmap.remote_video_disable);
    }

    /**
     * 开启/关闭视频上行
     */
    private void onEnableVideo(boolean enableVideo) {
        startLocalVideo(enableVideo);
        mVideoViewLayout.updateVideoStatus(trtcParams.userId, enableVideo);
        ivCamera.setImageResource(enableVideo ? R.mipmap.remote_video_enable : R.mipmap.remote_video_disable);
        this.bEnableAudio = enableVideo;
    }

    private void closeVideoLayout(boolean enableVideo) {
        startLocalVideo(enableVideo);
        ivCamera.setImageResource(enableVideo ? R.mipmap.remote_video_enable : R.mipmap.remote_video_disable);
        this.bEnableAudio = enableVideo;
    }

    /**
     * 开启/关闭音频上行
     */
    private void onEnableAudio() {
        bEnableAudio = !bEnableAudio;
        trtcCloud.muteLocalAudio(!bEnableAudio);
        ivVoice.setImageResource(bEnableAudio ? R.mipmap.mic_enable : R.mipmap.mic_disable);
    }

    /**
     * 开启/关闭音频上行
     */
    private void onEnableAudio(boolean enableAudio) {
        trtcCloud.muteLocalAudio(!enableAudio);
        ivVoice.setImageResource(enableAudio ? R.mipmap.mic_enable : R.mipmap.mic_disable);
        this.bEnableAudio = enableAudio;
    }

    /**
     * 允许/拒绝发言
     */
    private void onEnableSpeak(boolean enableSpeak) {
        ivSpeak.setImageResource(enableSpeak ? R.mipmap.speak_enable : R.mipmap.speak_disable);
        this.bEanbleSpeak = enableSpeak;
        onEnableAudio(enableSpeak);
        closeVideoLayout(enableSpeak);
    }

    /**
     * 打开编码参数设置面板，用于调整画质
     */
    private void onShowSettingDlg() {
        settingDlg.show();
        settingDlg.setCanceledOnTouchOutside(true);
    }

    /*
     * 打开更多参数设置面板
     */
    private void onShowMoreDlg() {
        moreDlg.setRole(trtcParams.role);
        moreDlg.show(mAppScene);
        moreDlg.setCanceledOnTouchOutside(true);
    }

    @Override
    public void onComplete() {
        setTRTCCloudParam();
        setVideoFillMode(true);
        moreDlg.updateVideoFillMode(true);
    }

    /**
     * SDK内部状态回调
     */
    static class TRTCCloudListenerImpl extends TRTCCloudListener implements TRTCCloudListener.TRTCVideoRenderListener {

        private WeakReference<RoomActivity> mContext;

        public TRTCCloudListenerImpl(RoomActivity activity) {
            super();
            mContext = new WeakReference<>(activity);
        }

        /**
         * 加入房间
         */
        @Override
        public void onEnterRoom(long elapsed) {
            final RoomActivity activity = mContext.get();
            if (activity != null) {
                Toast.makeText(activity, "加入房间成功", Toast.LENGTH_SHORT).show();
                activity.mVideoViewLayout.onRoomEnter();
                activity.updateCloudMixtureParams();
                activity.enableAudioVolumeEvaluation(activity.moreDlg.isAudioVolumeEvaluation());
            }
        }

        /**
         * 离开房间
         */
        @Override
        public void onExitRoom(int reason) {

        }

        /**
         * ERROR 大多是不可恢复的错误，需要通过 UI 提示用户
         */
        @Override
        public void onError(int errCode, String errMsg, Bundle extraInfo) {
            Log.d(TAG, "sdk callback onError");
            RoomActivity activity = mContext.get();
            if (activity == null) return;

            if (errCode == TXLiteAVCode.ERR_ROOM_REQUEST_TOKEN_HTTPS_TIMEOUT ||
                    errCode == TXLiteAVCode.ERR_ROOM_REQUEST_IP_TIMEOUT ||
                    errCode == TXLiteAVCode.ERR_ROOM_REQUEST_ENTER_ROOM_TIMEOUT) {
                Toast.makeText(activity, "进房超时，请检查网络或稍后重试:" + errCode + "[" + errMsg + "]", Toast.LENGTH_SHORT).show();
                activity.exitRoom();
                return;
            }

            if (errCode == TXLiteAVCode.ERR_ROOM_REQUEST_TOKEN_INVALID_PARAMETER ||
                    errCode == TXLiteAVCode.ERR_ENTER_ROOM_PARAM_NULL ||
                    errCode == TXLiteAVCode.ERR_SDK_APPID_INVALID ||
                    errCode == TXLiteAVCode.ERR_ROOM_ID_INVALID ||
                    errCode == TXLiteAVCode.ERR_USER_ID_INVALID ||
                    errCode == TXLiteAVCode.ERR_USER_SIG_INVALID) {
                Toast.makeText(activity, "进房参数错误:" + errCode + "[" + errMsg + "]", Toast.LENGTH_SHORT).show();
                activity.exitRoom();
                return;
            }

            if (errCode == TXLiteAVCode.ERR_ACCIP_LIST_EMPTY ||
                    errCode == TXLiteAVCode.ERR_SERVER_INFO_UNPACKING_ERROR ||
                    errCode == TXLiteAVCode.ERR_SERVER_INFO_TOKEN_ERROR ||
                    errCode == TXLiteAVCode.ERR_SERVER_INFO_ALLOCATE_ACCESS_FAILED ||
                    errCode == TXLiteAVCode.ERR_SERVER_INFO_GENERATE_SIGN_FAILED ||
                    errCode == TXLiteAVCode.ERR_SERVER_INFO_TOKEN_TIMEOUT ||
                    errCode == TXLiteAVCode.ERR_SERVER_INFO_INVALID_COMMAND ||
                    errCode == TXLiteAVCode.ERR_SERVER_INFO_GENERATE_KEN_ERROR ||
                    errCode == TXLiteAVCode.ERR_SERVER_INFO_GENERATE_TOKEN_ERROR ||
                    errCode == TXLiteAVCode.ERR_SERVER_INFO_DATABASE ||
                    errCode == TXLiteAVCode.ERR_SERVER_INFO_BAD_ROOMID ||
                    errCode == TXLiteAVCode.ERR_SERVER_INFO_BAD_SCENE_OR_ROLE ||
                    errCode == TXLiteAVCode.ERR_SERVER_INFO_ROOMID_EXCHANGE_FAILED ||
                    errCode == TXLiteAVCode.ERR_SERVER_INFO_STRGROUP_HAS_INVALID_CHARS ||
                    errCode == TXLiteAVCode.ERR_SERVER_ACC_TOKEN_TIMEOUT ||
                    errCode == TXLiteAVCode.ERR_SERVER_ACC_SIGN_ERROR ||
                    errCode == TXLiteAVCode.ERR_SERVER_ACC_SIGN_TIMEOUT ||
                    errCode == TXLiteAVCode.ERR_SERVER_CENTER_INVALID_ROOMID ||
                    errCode == TXLiteAVCode.ERR_SERVER_CENTER_CREATE_ROOM_FAILED ||
                    errCode == TXLiteAVCode.ERR_SERVER_CENTER_SIGN_ERROR ||
                    errCode == TXLiteAVCode.ERR_SERVER_CENTER_SIGN_TIMEOUT ||
                    errCode == TXLiteAVCode.ERR_SERVER_CENTER_ADD_USER_FAILED ||
                    errCode == TXLiteAVCode.ERR_SERVER_CENTER_FIND_USER_FAILED ||
                    errCode == TXLiteAVCode.ERR_SERVER_CENTER_SWITCH_TERMINATION_FREQUENTLY ||
                    errCode == TXLiteAVCode.ERR_SERVER_CENTER_LOCATION_NOT_EXIST ||
                    errCode == TXLiteAVCode.ERR_SERVER_CENTER_ROUTE_TABLE_ERROR ||
                    errCode == TXLiteAVCode.ERR_SERVER_CENTER_INVALID_PARAMETER) {
                Toast.makeText(activity, "进房失败，请稍后重试:" + errCode + "[" + errMsg + "]", Toast.LENGTH_SHORT).show();
                activity.exitRoom();
                return;
            }

            if (errCode == TXLiteAVCode.ERR_SERVER_CENTER_ROOM_FULL ||
                    errCode == TXLiteAVCode.ERR_SERVER_CENTER_REACH_PROXY_MAX) {
                Toast.makeText(activity, "进房失败，房间满了，请稍后重试:" + errCode + "[" + errMsg + "]", Toast.LENGTH_SHORT).show();
                activity.exitRoom();
                return;
            }

            if (errCode == TXLiteAVCode.ERR_SERVER_CENTER_ROOM_ID_TOO_LONG) {
                Toast.makeText(activity, "进房失败，roomID超出有效范围:" + errCode + "[" + errMsg + "]", Toast.LENGTH_SHORT).show();
                activity.exitRoom();
                return;
            }

            if (errCode == TXLiteAVCode.ERR_SERVER_ACC_ROOM_NOT_EXIST ||
                    errCode == TXLiteAVCode.ERR_SERVER_CENTER_ROOM_NOT_EXIST) {
                Toast.makeText(activity, "进房失败，请确认房间号正确:" + errCode + "[" + errMsg + "]", Toast.LENGTH_SHORT).show();
                activity.exitRoom();
                return;
            }

            if (errCode == TXLiteAVCode.ERR_SERVER_INFO_SERVICE_SUSPENDED) {
                Toast.makeText(activity, "进房失败，请确认账号状态是否欠费:" + errCode + "[" + errMsg + "]", Toast.LENGTH_SHORT).show();
                activity.exitRoom();
                return;
            }

            if (errCode == TXLiteAVCode.ERR_SERVER_INFO_PRIVILEGE_FLAG_ERROR ||
                    errCode == TXLiteAVCode.ERR_SERVER_CENTER_NO_PRIVILEDGE_CREATE_ROOM ||
                    errCode == TXLiteAVCode.ERR_SERVER_CENTER_NO_PRIVILEDGE_ENTER_ROOM) {
                Toast.makeText(activity, "进房失败，无权限进入房间:" + errCode + "[" + errMsg + "]", Toast.LENGTH_SHORT).show();
                activity.exitRoom();
                return;
            }

            if (errCode <= TXLiteAVCode.ERR_SERVER_SSO_SIG_EXPIRED &&
                    errCode >= TXLiteAVCode.ERR_SERVER_SSO_INTERNAL_ERROR) {
                // 错误参考 https://cloud.tencent.com/document/product/269/1671#.E5.B8.90.E5.8F.B7.E7.B3.BB.E7.BB.9F
                Toast.makeText(activity, "进房失败，userSig错误:" + errCode + "[" + errMsg + "]", Toast.LENGTH_SHORT).show();
                activity.exitRoom();
                return;
            }

            Toast.makeText(activity, "onError: " + errMsg + "[" + errCode + "]", Toast.LENGTH_SHORT).show();
        }

        /**
         * WARNING 大多是一些可以忽略的事件通知，SDK内部会启动一定的补救机制
         */
        @Override
        public void onWarning(int warningCode, String warningMsg, Bundle extraInfo) {
            Log.d(TAG, "sdk callback onWarning");
        }

        /**
         * 有新的用户加入了当前视频房间
         */
        @Override
        public void onUserEnter(String userId) {
            RoomActivity activity = mContext.get();
            if (activity != null) {
                // 创建一个View用来显示新的一路画面
                TXCloudVideoView renderView = activity.mVideoViewLayout.onMemberEnter(userId + TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);
                if (renderView != null) {
                    // 设置仪表盘数据显示
                    renderView.setVisibility(View.VISIBLE);
                    activity.trtcCloud.setDebugViewMargin(userId, new TRTCCloud.TRTCViewMargin(0.0f, 0.0f, 0.1f, 0.0f));
                }
                activity.enableAudioVolumeEvaluation(activity.moreDlg.isAudioVolumeEvaluation());
            }
        }

        /**
         * 有用户离开了当前视频房间
         */
        @Override
        public void onUserExit(String userId, int reason) {
            RoomActivity activity = mContext.get();
            if (activity != null) {
                //停止观看画面
                activity.trtcCloud.stopRemoteView(userId);
                activity.trtcCloud.stopRemoteSubStreamView(userId);
                //更新视频UI
                activity.mVideoViewLayout.onMemberLeave(userId + TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);
                activity.mVideoViewLayout.onMemberLeave(userId + TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_SUB);

                activity.updateCloudMixtureParams();
            }
        }

        /**
         * 有用户屏蔽了画面
         */
        @Override
        public void onUserVideoAvailable(final String userId, boolean available) {
            RoomActivity activity = mContext.get();
            if (activity != null) {
                VideoStream userStream = new VideoStream();
                userStream.userId = userId;
                userStream.streamType = TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG;
                if (available) {
                    final TXCloudVideoView renderView = activity.mVideoViewLayout.onMemberEnter(userId + TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);
                    if (renderView != null) {
                        // 启动远程画面的解码和显示逻辑，FillMode 可以设置是否显示黑边
                        activity.trtcCloud.setRemoteViewFillMode(userId, TRTCCloudDef.TRTC_VIDEO_RENDER_MODE_FIT);
                        activity.trtcCloud.startRemoteView(userId, renderView);
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                renderView.setUserId(userId + TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);
                            }
                        });
                    }

                    activity.mVideosInRoom.add(userStream);
                } else {
                    activity.trtcCloud.stopRemoteView(userId);
                    //activity.mVideoViewLayout.onMemberLeave(userId+TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);

                    activity.mVideosInRoom.remove(userStream);
                }
                activity.updateCloudMixtureParams();
                activity.mVideoViewLayout.updateVideoStatus(userId + TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG, available);
            }

        }

        public void onUserSubStreamAvailable(final String userId, boolean available) {
            RoomActivity activity = mContext.get();
            if (activity != null) {
                VideoStream userStream = new VideoStream();
                userStream.userId = userId;
                userStream.streamType = TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_SUB;
                if (available) {
                    final TXCloudVideoView renderView = activity.mVideoViewLayout.onMemberEnter(userId + TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_SUB);
                    if (renderView != null) {
                        // 启动远程画面的解码和显示逻辑，FillMode 可以设置是否显示黑边
                        activity.trtcCloud.setRemoteSubStreamViewFillMode(userId, TRTCCloudDef.TRTC_VIDEO_RENDER_MODE_FIT);
                        activity.trtcCloud.startRemoteSubStreamView(userId, renderView);

                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                renderView.setUserId(userId + TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_SUB);
                            }
                        });
                    }
                    activity.mVideosInRoom.add(userStream);
                } else {
                    activity.trtcCloud.stopRemoteSubStreamView(userId);
                    activity.mVideoViewLayout.onMemberLeave(userId + TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_SUB);
                    activity.mVideosInRoom.remove(userStream);
                }
                activity.updateCloudMixtureParams();
            }
        }

        /**
         * 有用户屏蔽了声音
         */
        @Override
        public void onUserAudioAvailable(String userId, boolean available) {
            RoomActivity activity = mContext.get();
            if (activity != null) {
                if (available) {
                    final TXCloudVideoView renderView = activity.mVideoViewLayout.onMemberEnter(userId + TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);
                    if (renderView != null) {
                        renderView.setVisibility(View.VISIBLE);
                    }
                }
            }
        }

        /**
         * 首帧渲染回调
         */
        @Override
        public void onFirstVideoFrame(String userId, int streamType, int width, int height) {
            RoomActivity activity = mContext.get();
            if (activity != null) {
                activity.mVideoViewLayout.freshToolbarLayoutOnMemberEnter(userId + TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);
            }
        }

        public void onStartPublishCDNStream(int err, String errMsg) {

        }

        public void onStopPublishCDNStream(int err, String errMsg) {

        }

        public void onRenderVideoFrame(String userId, int streamType, TRTCCloudDef.TRTCVideoFrame frame) {
//            Log.w(TAG, String.format("onRenderVideoFrame userId: %s, type: %d",userId, streamType));
        }

        public void onUserVoiceVolume(ArrayList<TRTCCloudDef.TRTCVolumeInfo> userVolumes, int totalVolume) {
            mContext.get().mVideoViewLayout.resetAudioVolume();
            for (int i = 0; i < userVolumes.size(); ++i) {
                mContext.get().mVideoViewLayout.updateAudioVolume(userVolumes.get(i).userId, userVolumes.get(i).volume);
            }
        }

        public void onStatistics(TRTCStatistics statics) {

        }

        @Override
        public void onConnectOtherRoom(final String userID, final int err, final String errMsg) {

        }

        @Override
        public void onDisConnectOtherRoom(final int err, final String errMsg) {

        }

        @Override
        public void onNetworkQuality(TRTCCloudDef.TRTCQuality localQuality, ArrayList<TRTCCloudDef.TRTCQuality> remoteQuality) {
            RoomActivity activity = mContext.get();
            if (activity != null) {
                activity.mVideoViewLayout.updateNetworkQuality(localQuality.userId, localQuality.quality);
                for (TRTCCloudDef.TRTCQuality qualityInfo : remoteQuality) {
                    activity.mVideoViewLayout.updateNetworkQuality(qualityInfo.userId, qualityInfo.quality);
                }
            }
        }

        @Override
        public void onRecvCustomCmdMsg(String userId, int cmdID, int seq, byte[] message) {

        }
    }

    @Override
    public void onEnableRemoteVideo(final String userId, boolean enable) {
        if (enable) {
            final TXCloudVideoView renderView = mVideoViewLayout.getCloudVideoViewByUseId(userId + TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);
            if (renderView != null) {
                trtcCloud.setRemoteViewFillMode(userId, TRTCCloudDef.TRTC_VIDEO_RENDER_MODE_FIT);
                trtcCloud.startRemoteView(userId, renderView);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        renderView.setUserId(userId + TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);
                        mVideoViewLayout.freshToolbarLayoutOnMemberEnter(userId);
                    }
                });
            }
        } else {
            trtcCloud.stopRemoteView(userId);
        }
    }

    @Override
    public void onEnableRemoteAudio(String userId, boolean enable) {
        trtcCloud.muteRemoteAudio(userId, !enable);
    }

    @Override
    public void onChangeVideoFillMode(String userId, boolean adjustMode) {
        trtcCloud.setRemoteViewFillMode(userId, adjustMode ? TRTCCloudDef.TRTC_VIDEO_RENDER_MODE_FIT : TRTCCloudDef.TRTC_VIDEO_RENDER_MODE_FILL);
    }

    @Override
    public void onSwitchCamera(boolean bCameraFront) {
        trtcCloud.switchCamera();
    }

    @Override
    public void onFillModeChange(boolean bFillMode) {
        setVideoFillMode(bFillMode);
    }

    @Override
    public void onVideoRotationChange(boolean bVertical) {
        setVideoRotation(bVertical);
    }

    @Override
    public void onEnableAudioCapture(boolean bEnable) {
        enableAudioCapture(bEnable);
    }

    @Override
    public void onEnableAudioHandFree(boolean bEnable) {
        enableAudioHandFree(bEnable);
    }

    @Override
    public void onMirrorLocalVideo(int localViewMirror) {
        setLocalViewMirrorMode(localViewMirror);
    }

    @Override
    public void onMirrorRemoteVideo(boolean bMirror) {
        enableVideoEncMirror(bMirror);
    }

    @Override
    public void onEnableGSensor(boolean bEnable) {
        enableGSensor(bEnable);
    }

    @Override
    public void onEnableAudioVolumeEvaluation(boolean bEnable) {
        enableAudioVolumeEvaluation(bEnable);
    }

    @Override
    public void onEnableCloudMixture(boolean bEnable) {
        updateCloudMixtureParams();
    }

    @Override
    public void onClickButtonGetPlayUrl() {
        if (trtcParams == null) {
            return;
        }

        String strStreamID = "3891_" + stringToMd5("" + trtcParams.roomId + "_" + trtcParams.userId + "_main");
        String strPlayUrl = "http://3891.liveplay.myqcloud.com/live/" + strStreamID + ".flv";

//        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
//        ClipData clipData = ClipData.newPlainText("Label", strPlayUrl);
//        cm.setPrimaryClip(clipData);
//        Toast.makeText(getApplicationContext(), "播放地址已复制到系统剪贴板！", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, strPlayUrl);
        intent.setType("text/plain");
        startActivity(Intent.createChooser(intent, "分享"));
    }

    @Override
    public void onClickButtonLinkMic() {

    }

    @Override
    public void onChangeRole(int role) {
        if (trtcCloud != null) {
            trtcCloud.switchRole(role);
        }
        if (role == TRTCCloudDef.TRTCRoleAnchor) {
            startLocalVideo(true);
            if (moreDlg.isEnableAudioCapture()) {
                trtcCloud.startLocalAudio();
            }
        } else {
            startLocalVideo(false);
            trtcCloud.stopLocalAudio();
            TXCloudVideoView localVideoView = mVideoViewLayout.getCloudVideoViewByUseId(trtcParams.userId);
            if (localVideoView != null) {
                localVideoView.setVisibility(View.GONE);
            }
        }
    }

    private void setVideoFillMode(boolean bFillMode) {
        if (bFillMode) {
            trtcCloud.setLocalViewFillMode(TRTCCloudDef.TRTC_VIDEO_RENDER_MODE_FILL);
        } else {
            trtcCloud.setLocalViewFillMode(TRTCCloudDef.TRTC_VIDEO_RENDER_MODE_FIT);
        }
    }

    private void setVideoRotation(boolean bVertical) {
        if (bVertical) {
            trtcCloud.setLocalViewRotation(TRTCCloudDef.TRTC_VIDEO_ROTATION_0);
        } else {
            trtcCloud.setLocalViewRotation(TRTCCloudDef.TRTC_VIDEO_ROTATION_90);
        }
    }

    private void enableAudioCapture(boolean bEnable) {
        if (bEnable) {
            trtcCloud.startLocalAudio();
        } else {
            trtcCloud.stopLocalAudio();
        }
    }

    private void enableAudioHandFree(boolean bEnable) {
        if (bEnable) {
            trtcCloud.setAudioRoute(TRTCCloudDef.TRTC_AUDIO_ROUTE_SPEAKER);
        } else {
            trtcCloud.setAudioRoute(TRTCCloudDef.TRTC_AUDIO_ROUTE_EARPIECE);
        }
    }

    private void enableVideoEncMirror(boolean bMirror) {
        trtcCloud.setVideoEncoderMirror(bMirror);
    }

    private void setLocalViewMirrorMode(int mirrorMode) {
        trtcCloud.setLocalViewMirror(mirrorMode);
    }

    private void enableGSensor(boolean bEnable) {
        if (bEnable) {
            trtcCloud.setGSensorMode(TRTCCloudDef.TRTC_GSENSOR_MODE_UIFIXLAYOUT);
        } else {
            trtcCloud.setGSensorMode(TRTCCloudDef.TRTC_GSENSOR_MODE_DISABLE);
        }
    }

    private void enableAudioVolumeEvaluation(boolean bEnable) {
        if (bEnable) {
            trtcCloud.enableAudioVolumeEvaluation(300);
            mVideoViewLayout.showAllAudioVolumeProgressBar();
        } else {
            trtcCloud.enableAudioVolumeEvaluation(0);
            mVideoViewLayout.hideAllAudioVolumeProgressBar();
        }
    }

    private void updateCloudMixtureParams() {
        // 背景大画面宽高
        int videoWidth = 720;
        int videoHeight = 1280;

        // 小画面宽高
        int subWidth = 180;
        int subHeight = 320;

        int offsetX = 5;
        int offsetY = 50;

        int bitrate = 200;

        int resolution = settingDlg.getResolution();
        switch (resolution) {

            case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_160_160: {
                videoWidth = 160;
                videoHeight = 160;
                subWidth = 27;
                subHeight = 48;
                offsetY = 20;
                bitrate = 200;
                break;
            }
            case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_320_180: {
                videoWidth = 192;
                videoHeight = 336;
                subWidth = 54;
                subHeight = 96;
                offsetY = 30;
                bitrate = 400;
                break;
            }
            case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_320_240: {
                videoWidth = 240;
                videoHeight = 320;
                subWidth = 54;
                subHeight = 96;
                bitrate = 400;
                break;
            }
            case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_480_480: {
                videoWidth = 480;
                videoHeight = 480;
                subWidth = 72;
                subHeight = 128;
                bitrate = 600;
                break;
            }
            case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_640_360: {
                videoWidth = 368;
                videoHeight = 640;
                subWidth = 90;
                subHeight = 160;
                bitrate = 800;
                break;
            }
            case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_640_480: {
                videoWidth = 480;
                videoHeight = 640;
                subWidth = 90;
                subHeight = 160;
                bitrate = 800;
                break;
            }
            case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_960_540: {
                videoWidth = 544;
                videoHeight = 960;
                subWidth = 171;
                subHeight = 304;
                bitrate = 1000;
                break;
            }
            case TRTCCloudDef.TRTC_VIDEO_RESOLUTION_1280_720: {
                videoWidth = 720;
                videoHeight = 1280;
                subWidth = 180;
                subHeight = 320;
                bitrate = 1500;
                break;
            }
        }

        TRTCCloudDef.TRTCTranscodingConfig config = new TRTCCloudDef.TRTCTranscodingConfig();
        config.appId = -1;  // 请从"实时音视频"控制台的帐号信息中获取
        config.bizId = -1;  // 请进入 "实时音视频"控制台 https://console.cloud.tencent.com/rav，点击对应的应用，然后进入“帐号信息”菜单中，复制“直播信息”模块中的"bizid"
        config.videoWidth = videoWidth;
        config.videoHeight = videoHeight;
        config.videoGOP = 1;
        config.videoFramerate = 15;
        config.videoBitrate = bitrate;
        config.audioSampleRate = 48000;
        config.audioBitrate = 64;
        config.audioChannels = 1;

        // 设置混流后主播的画面位置
        TRTCCloudDef.TRTCMixUser broadCaster = new TRTCCloudDef.TRTCMixUser();
        broadCaster.userId = trtcParams.userId; // 以主播uid为broadcaster为例
        broadCaster.zOrder = 0;
        broadCaster.x = 0;
        broadCaster.y = 0;
        broadCaster.width = videoWidth;
        broadCaster.height = videoHeight;

        config.mixUsers = new ArrayList<>();
        config.mixUsers.add(broadCaster);

        // 设置混流后各个小画面的位置
        if (moreDlg.isEnableCloudMixture()) {
            int index = 0;
            for (VideoStream userStream : mVideosInRoom) {
                TRTCCloudDef.TRTCMixUser audience = new TRTCCloudDef.TRTCMixUser();

                audience.userId = userStream.userId;
                audience.streamType = userStream.streamType;
                audience.zOrder = 1 + index;
                if (index < 3) {
                    // 前三个小画面靠右从下往上铺
                    audience.x = videoWidth - offsetX - subWidth;
                    audience.y = videoHeight - offsetY - index * subHeight - subHeight;
                    audience.width = subWidth;
                    audience.height = subHeight;
                } else if (index < 6) {
                    // 后三个小画面靠左从下往上铺
                    audience.x = offsetX;
                    audience.y = videoHeight - offsetY - (index - 3) * subHeight - subHeight;
                    audience.width = subWidth;
                    audience.height = subHeight;
                } else {
                    // 最多只叠加六个小画面
                }

                config.mixUsers.add(audience);
                ++index;
            }
        }

        trtcCloud.setMixTranscodingConfig(config);
    }

    protected String stringToMd5(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            String result = "";
            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) {
                    temp = "0" + temp;
                }
                result += temp;
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    private void startLocalVideo(boolean enable) {
        TXCloudVideoView localVideoView = mVideoViewLayout.getCloudVideoViewByUseId(trtcParams.userId);
        if (localVideoView == null) {
            localVideoView = mVideoViewLayout.getFreeCloudVideoView();
        }
        localVideoView.setUserId(trtcParams.userId);
        localVideoView.setVisibility(View.VISIBLE);
        if (enable) {
            // 设置 TRTC SDK 的状态
            trtcCloud.enableCustomVideoCapture(false);
            //启动SDK摄像头采集和渲染
            trtcCloud.startLocalPreview(moreDlg.isCameraFront(), localVideoView);
        } else {
            trtcCloud.stopLocalPreview();
        }
    }

    private void swapViewByIndex(String[] userIds) {
        for (int i = 0; i < userIds.length; i++) {
            int src = mVideoViewLayout.getCloudVideoViewIndex(userIds[i]);
            if (src >= 0) {
                mVideoViewLayout.swapViewByIndex(i, src);
            }
        }
    }

    private void initExitDialog() {
        AlertDialog.Builder mAlertDialog = new AlertDialog.Builder(this, R.style.Theme_AppCompat_Light_Dialog_Alert);
        mAlertDialog.setTitle("请确认");
        mAlertDialog.setMessage("确定要退出会议吗？");
        mAlertDialog.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                exitDialog.dismiss();
                exitRoom();
            }
        });
        mAlertDialog.setNegativeButton("取消", null);
        exitDialog = mAlertDialog.create();
    }

    private void startWebSocket() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        mOkHttpClient = builder.build();

        Request request = new Request.Builder().url(API.WS_HOST).build();
        webSocketListener = new EchoWebSocketListener();
        webSocket = mOkHttpClient.newWebSocket(request, webSocketListener);
        mOkHttpClient.dispatcher().executorService().shutdown();
    }

    class EchoWebSocketListener extends WebSocketListener {

        public boolean webSocketConnect;

        public static final String CONFIRM_SPEAK  = "confirm_speak";
        public static final String CONTROL_MIC    = "control_mic";
        public static final String CONTROL_CAMERA = "control_camera";
        public static final String INVITE_SPEAK   = "invite_speak";
        public static final String CANCEL_SPEAK   = "cancel_speak";
        public static final String MULTI_SCREEN   = "multi_screen";
        public static final String FORCE_EXIT     = "force_exit";

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            super.onOpen(webSocket, response);
            Log.d(TAG, "WebSocket 连接成功");
            webSocketConnect = true;
            mHandler.sendEmptyMessage(1);
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            super.onMessage(webSocket, bytes);
            Log.d(TAG, "ByteString onMessage: " + bytes.toString());

        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            super.onMessage(webSocket, text);
            Log.d(TAG, "String onMessage: " + text);

            JSONObject jo = JSON.parseObject(text);
            Integer roomNo = jo.getIntValue("roomNo");
            if (roomNo != roomId) {
                return;
            }

            final int result;
            switch (jo.getString("cmd")) {
                case CONFIRM_SPEAK://主席端 同意/拒绝 分会场发言申请
                    result = jo.getIntValue("result");

                    mDeviceStatusBean.getData().setSpeaker(result);
                    deviceStatus = JSON.toJSONString(mDeviceStatusBean);
                    webSocket.send(deviceStatus);

                    if (result == 1) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                role=TRTCCloudDef.TRTCRoleAnchor;
                                onChangeRole(role);
                                onEnableSpeak(true);
                            }
                        });
                    }
                    showMsg("主席已" + (result == 1 ? "同意" : "拒绝") + "您的发言申请");
                    break;
                case CONTROL_MIC: {//主席端 打开/关闭 分会场麦克风
                    result = jo.getIntValue("result");

                    mDeviceStatusBean.getData().setMic(result);
                    deviceStatus = JSON.toJSONString(mDeviceStatusBean);
                    webSocket.send(deviceStatus);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onEnableAudio(result == 1);
                        }
                    });
                    showMsg("主席已" + (result == 1 ? "打开" : "关闭") + "您的麦克风");
                }
                break;
                case CONTROL_CAMERA://主席端 打开/关闭 分会场摄像头
                    result = jo.getIntValue("result");

                    mDeviceStatusBean.getData().setCamera(result);
                    deviceStatus = JSON.toJSONString(mDeviceStatusBean);
                    webSocket.send(deviceStatus);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onEnableVideo(result == 1);
                        }
                    });
                    showMsg("主席已" + (result == 1 ? "打开" : "关闭") + "您的摄像头");
                    break;
                case INVITE_SPEAK://主席端邀请分会场发言

                    mDeviceStatusBean.getData().setSpeaker(1);
                    deviceStatus = JSON.toJSONString(mDeviceStatusBean);
                    webSocket.send(deviceStatus);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            role = TRTCCloudDef.TRTCRoleAnchor;
                            onChangeRole(role);
                            onEnableSpeak(true);
                            showMsg("主席邀请您发言");
                        }
                    });
                    break;
                case CANCEL_SPEAK://主席端取消分会场发言

                    mDeviceStatusBean.getData().setSpeaker(0);
                    deviceStatus = JSON.toJSONString(mDeviceStatusBean);
                    webSocket.send(deviceStatus);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            role = TRTCCloudDef.TRTCRoleAudience;
                            onChangeRole(role);
                            onEnableSpeak(false);
                            showMsg("主席取消您发言");
                        }
                    });
                    break;

                case MULTI_SCREEN://多画面
                    String users = jo.getString("acctno");
                    final String[] userIds = users.split(",");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            swapViewByIndex(userIds);
                        }
                    });
                    break;
                case FORCE_EXIT:
                    String msg = jo.getString("msg");
                    showMsg(msg);
                    exitRoom();
                    break;
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            super.onClosed(webSocket, code, reason);
            Log.d(TAG, "WebSocket 连接已关闭");
            webSocketConnect = false;
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            super.onClosing(webSocket, code, reason);
            Log.d(TAG, "WebSocket 正在关闭");
            webSocketConnect = false;
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            super.onFailure(webSocket, t, response);
            Log.e(TAG, "WebSocket 连接出错:" + t.getMessage());
            webSocketConnect = false;
            startWebSocket();
        }
    }
}
