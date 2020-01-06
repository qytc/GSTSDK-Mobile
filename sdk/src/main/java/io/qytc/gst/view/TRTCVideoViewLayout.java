package io.qytc.gst.view;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.tencent.rtmp.TXLog;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.trtc.TRTCCloudDef;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

import io.qytc.gst.sdk.R;

/**
 * Module:   TRTCVideoViewLayout
 * <p>
 * Function: 用于计算每个视频画面的位置排布和大小尺寸
 */
public class TRTCVideoViewLayout extends RelativeLayout {
    private final static String TAG = TRTCVideoViewLayout.class.getSimpleName();
    public static final int MODE_FLOAT = 1;  // 前后堆叠模式
    public static final int MODE_GRID = 2;  // 九宫格模式
    public static final int MAX_USER = 4;
    private Context mContext;

    public ArrayList<TXCloudVideoView> getVideoViewList() {
        return mVideoViewList;
    }

    private ArrayList<TXCloudVideoView> mVideoViewList;
    private ArrayList<RelativeLayout.LayoutParams> mFloatParamList;
    private ArrayList<LayoutParams> mGrid4ParamList;
    private RelativeLayout mLayout;
    private int mCount = 0;
    private int mMode;

    private String mSelfUserId;
    private WeakReference<ITRTCVideoViewLayoutListener> mListener = new WeakReference<>(null);

    HashMap<Integer, Integer> mapNetworkQuality = null;

    public interface ITRTCVideoViewLayoutListener {
        void onEnableRemoteVideo(String userId, boolean enable);

        void onEnableRemoteAudio(String userId, boolean enable);

        void onChangeVideoFillMode(String userId, boolean adjustMode);
    }

    public TRTCVideoViewLayout(Context context) {
        super(context);
        initView(context);
    }


    public TRTCVideoViewLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public TRTCVideoViewLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    public void setUserId(String userId) {
        mSelfUserId = userId;
    }

    public void setListener(ITRTCVideoViewLayoutListener listener) {
        mListener = new WeakReference<>(listener);
    }

    private void initView(Context context) {
        mContext = context;
        LayoutInflater.from(context).inflate(R.layout.room_show_view, this);
        mLayout = findViewById(R.id.ll_mainview);
        initTXCloudVideoView();
        initGridLayoutParams();
        showView();

        mapNetworkQuality = new HashMap<>();
        mapNetworkQuality.put(TRTCCloudDef.TRTC_QUALITY_Down, R.mipmap.signal1);
        mapNetworkQuality.put(TRTCCloudDef.TRTC_QUALITY_Vbad, R.mipmap.signal2);
        mapNetworkQuality.put(TRTCCloudDef.TRTC_QUALITY_Bad, R.mipmap.signal3);
        mapNetworkQuality.put(TRTCCloudDef.TRTC_QUALITY_Poor, R.mipmap.signal4);
        mapNetworkQuality.put(TRTCCloudDef.TRTC_QUALITY_Good, R.mipmap.signal5);
        mapNetworkQuality.put(TRTCCloudDef.TRTC_QUALITY_Excellent, R.mipmap.signal6);

        mMode = MODE_GRID;
    }

    private void showView() {
        mLayout.removeAllViews();
        for (int i = 0; i < mVideoViewList.size(); i++) {
            TXCloudVideoView cloudVideoView = mVideoViewList.get(i);
            RelativeLayout.LayoutParams layoutParams = mGrid4ParamList.get(i);
            cloudVideoView.setLayoutParams(layoutParams);
            mLayout.addView(cloudVideoView);
        }
    }

    public void initGridLayoutParams() {
        mGrid4ParamList = new ArrayList<>();

        int statusH = getStatusBarHeight(mContext);
        TXLog.i(TAG, "statusH:" + statusH);
        int screenW = getScreenWidth(mContext);
        int screenH = getScreenHeight(mContext);
        int bottomMargin = dip2px(50);
        int margin = dip2px(10);

        initGrid4Param(statusH, screenW, screenH, bottomMargin, margin);
    }

    public TXCloudVideoView getFreeCloudVideoView() {
        for (TXCloudVideoView videoView : mVideoViewList) {
            String tempUserID = videoView.getUserId();
            if (TextUtils.isEmpty(tempUserID)) {
                return videoView;
            }
        }
        return null;
    }

    private void initGrid4Param(int statusH, int screenW, int screenH, int bottomMargin, int margin) {
        int grid4W = (screenW - margin * 2) / 2;
        int grid4H = (screenH - statusH - margin * 2 - bottomMargin) / 2;

        LayoutParams layoutParams0 = new LayoutParams(screenW * 4 / 5, screenH);
        layoutParams0.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        layoutParams0.addRule(RelativeLayout.CENTER_VERTICAL);

        LayoutParams layoutParams1 = new LayoutParams(screenW / 5, screenH / 3);
        layoutParams1.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        layoutParams1.addRule(RelativeLayout.ALIGN_PARENT_TOP);

        LayoutParams layoutParams2 = new LayoutParams(screenW / 5, screenH / 3);
        layoutParams2.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        layoutParams2.addRule(RelativeLayout.CENTER_VERTICAL);

        LayoutParams layoutParams3 = new LayoutParams(screenW / 5, screenH / 3);
        layoutParams3.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        layoutParams3.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

        mGrid4ParamList.add(layoutParams0);
        mGrid4ParamList.add(layoutParams1);
        mGrid4ParamList.add(layoutParams2);
        mGrid4ParamList.add(layoutParams3);
    }

    public void initFloatLayoutParams() {
        mFloatParamList = new ArrayList<RelativeLayout.LayoutParams>();
        RelativeLayout.LayoutParams layoutParams0 = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mFloatParamList.add(layoutParams0);

        int midMargin = dip2px(10);
        int lrMargin = dip2px(15);
        int bottomMargin = dip2px(50);
        int subWidth = dip2px(120);
        int subHeight = dip2px(180);

        for (int i = 0; i < 3; i++) {
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(subWidth, subHeight);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            layoutParams.rightMargin = lrMargin;
            layoutParams.bottomMargin = bottomMargin + midMargin * (i + 1) + subHeight * i;

            mFloatParamList.add(layoutParams);
        }

        for (int i = 0; i < 3; i++) {
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(subWidth, subHeight);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            layoutParams.leftMargin = lrMargin;
            layoutParams.bottomMargin = bottomMargin + midMargin * (i + 1) + subHeight * i;

            mFloatParamList.add(layoutParams);
        }
    }

    public void initTXCloudVideoView() {
        mVideoViewList = new ArrayList<>();
        for (int i = 0; i < MAX_USER; i++) {
            TXCloudVideoView cloudVideoView = new TXCloudVideoView(mContext);
            cloudVideoView.setVisibility(GONE);
            cloudVideoView.setId(1000 + i);
            cloudVideoView.setClickable(true);
            cloudVideoView.setTag(R.string.str_tag_pos, i);
            cloudVideoView.setBackgroundColor(Color.BLACK);
            addToolbarLayout(cloudVideoView);
            mVideoViewList.add(i, cloudVideoView);
        }
    }

    public TXCloudVideoView getCloudVideoViewByIndex(int index) {
        return mVideoViewList.get(index);
    }

    public TXCloudVideoView getCloudVideoViewByUseId(String userId) {
        for (TXCloudVideoView videoView : mVideoViewList) {
            String tempUserID = videoView.getUserId();
            if (tempUserID != null && tempUserID.contains(userId)) {
                return videoView;
            }
        }
        return null;
    }

    public int getCloudVideoViewIndex(String userId) {
        for (int i = 0; i < mVideoViewList.size(); i++) {
            if (userId.equalsIgnoreCase("-1")) {
                return -1;
            } else if (null != mVideoViewList.get(i).getUserId() && mVideoViewList.get(i).getUserId().contains(userId) && !userId.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public void updateLayoutGrid() {
        for (int i = 0; i < mVideoViewList.size(); i++) {
            TXCloudVideoView cloudVideoView = mVideoViewList.get(i);
            if (i < mGrid4ParamList.size()) {
                RelativeLayout.LayoutParams layoutParams = mGrid4ParamList.get(i);
                cloudVideoView.setLayoutParams(layoutParams);
            }
            cloudVideoView.setTag(R.string.str_tag_pos, i);
            cloudVideoView.setClickable(true);
            cloudVideoView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Object object = v.getTag(R.string.str_tag_pos);
                    if (object != null) {
                        int pos = (int) object;
                        TXCloudVideoView renderView = (TXCloudVideoView) v;
                        TXLog.i(TAG, "click on pos: " + pos + "/userId: " + renderView.getUserId());
                        if (null != renderView.getUserId()) {
                            swapViewByIndex(0, pos);
                        }
                    }
                }
            });
            if (i != 0) {
                mLayout.bringChildToFront(cloudVideoView);
            }
        }
    }


    public void swapViewByIndex(int src, int dst) {
        TXLog.i(TAG, "swapViewByIndex src:" + src + ",dst:" + dst);
        TXCloudVideoView srcView = mVideoViewList.get(src);
        TXCloudVideoView dstView = mVideoViewList.get(dst);
        mVideoViewList.set(src, dstView);
        mVideoViewList.set(dst, srcView);

        updateLayoutGrid();
    }

    public void appendEventMessage(String userId, String message) {
        for (int i = 0; i < mVideoViewList.size(); i++) {
            if (userId.equalsIgnoreCase(mVideoViewList.get(i).getUserId())) {
                mVideoViewList.get(i).appendEventInfo(message);
                break;
            }
        }
    }

    public int dip2px(float dpValue) {
        final float scale = getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public void showDebugView(int type) {
        for (int i = 0; i < mVideoViewList.size(); i++) {
            TXCloudVideoView renderView = mVideoViewList.get(i);
            if (renderView != null) {
                String vUserId = renderView.getUserId();
                if (!TextUtils.isEmpty(vUserId)) {
                    renderView.showVideoDebugLog(type);
                }

            }
        }
    }

    /**
     * 更新进入房间人数，4个人以下用四宫格，4个人以上用9宫格
     */
    public TXCloudVideoView onMemberEnter(String userId) {
        Log.e(TAG, "onMemberEnter: userId = " + userId);

        if (TextUtils.isEmpty(userId)) return null;
        TXCloudVideoView videoView = null;
        int posIdx = 0;
        int posLocal = mVideoViewList.size();
        for (int i = 0; i < mVideoViewList.size(); i++) {
            TXCloudVideoView renderView = mVideoViewList.get(i);
            if (renderView != null) {
                String vUserId = renderView.getUserId();
                if (userId.equalsIgnoreCase(vUserId)) {
                    return renderView;
                }
                if (videoView == null && TextUtils.isEmpty(vUserId)) {
                    renderView.setUserId(userId);
                    videoView = renderView;
                    posIdx = i;
                    mCount++;
                } else if (!TextUtils.isEmpty(vUserId) && vUserId.equalsIgnoreCase(mSelfUserId)) {
                    posLocal = i;
                }
            }
        }
        TXLog.i("lyj", "onMemberEnter->posIdx: " + posIdx + ", posLast: " + posLocal);

        if (0 == posLocal) {
            swapViewByIndex(posIdx, posLocal);
        }

        updateLayoutGrid();

        return videoView;
    }

    public void onMemberLeave(String userId) {
        Log.e(TAG, "onMemberLeave: userId = " + userId);

        int posIdx = 0, posLocal = mVideoViewList.size();
        for (int i = 0; i < mVideoViewList.size(); i++) {
            TXCloudVideoView renderView = mVideoViewList.get(i);
            if (renderView != null && null != renderView.getUserId()) {
                if (renderView.getUserId().equals(userId)) {
                    renderView.setUserId(null);
                    renderView.setVisibility(View.GONE);
                    freshToolbarLayoutOnMemberLeave(renderView);
                    posIdx = i;
                    mCount--;
                } else if (renderView.getUserId().equalsIgnoreCase(mSelfUserId)) {
                    posLocal = i;
                }
            }
        }

        if (0 == posIdx) {
            swapViewByIndex(posIdx, posLocal);
        }

        updateLayoutGrid();
    }

    public void onRoomEnter() {
        mCount++;
        updateLayoutGrid();
    }

    public int getScreenWidth(Context context) {
        if (context == null) return 0;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.widthPixels;
    }

    public int getScreenHeight(Context context) {
        if (context == null) return 0;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.heightPixels;
    }

    public int getStatusBarHeight(Context context) {
        int statusBarHeight1 = -1;
        //获取status_bar_height资源的ID
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            //根据资源ID获取响应的尺寸值
            statusBarHeight1 = context.getResources().getDimensionPixelSize(resourceId);
        }
        return statusBarHeight1;
    }

    private void addToolbarLayout(final TXCloudVideoView videoView) {
        View view = videoView.findViewById(R.id.layout_toolbar);
        if (view == null) {
            view = LayoutInflater.from(mContext).inflate(R.layout.layout_toolbar, videoView);
            view.setVisibility(GONE);

            final Button btnRemoteVideo = (Button) view.findViewById(R.id.btn_remote_video);
            btnRemoteVideo.setTag(R.mipmap.remote_video_enable);
            btnRemoteVideo.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    String userId = videoView.getUserId();
                    if (userId != null && userId.length() > 0) {
                        userId = userId.substring(0, userId.length() - 1);
                    }
                    if (userId != null && userId.length() > 0) {
                        int currentTag = (int) btnRemoteVideo.getTag();
                        boolean enable = currentTag != R.mipmap.remote_video_enable;
                        ITRTCVideoViewLayoutListener listener = mListener.get();
                        if (listener != null) {
                            listener.onEnableRemoteVideo(userId, enable);
                        }
                        btnRemoteVideo.setTag(enable ? R.mipmap.remote_video_enable : R.mipmap.remote_video_disable);
                        btnRemoteVideo.setBackgroundResource(enable ? R.mipmap.remote_video_enable : R.mipmap.remote_video_disable);
                    }
                }
            });

            final Button btnRemoteAudio = (Button) view.findViewById(R.id.btn_remote_audio);
            btnRemoteAudio.setTag(R.mipmap.remote_audio_enable);
            btnRemoteAudio.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    String userId = videoView.getUserId();
                    if (userId != null && userId.length() > 0) {
                        userId = userId.substring(0, userId.length() - 1);
                    }
                    if (userId != null && userId.length() > 0) {
                        int currentTag = (int) btnRemoteAudio.getTag();
                        boolean enable = currentTag != R.mipmap.remote_audio_enable;
                        ITRTCVideoViewLayoutListener listener = mListener.get();
                        if (listener != null) {
                            listener.onEnableRemoteAudio(userId, enable);
                        }
                        btnRemoteAudio.setTag(enable ? R.mipmap.remote_audio_enable : R.mipmap.remote_audio_disable);
                        btnRemoteAudio.setBackgroundResource(enable ? R.mipmap.remote_audio_enable : R.mipmap.remote_audio_disable);
                    }
                }
            });

            final Button btnFillMode = (Button) view.findViewById(R.id.btn_fill_mode);
            btnFillMode.setTag(R.mipmap.fill_scale);
            btnFillMode.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    String userId = videoView.getUserId();
                    if (userId != null && userId.length() > 0) {
                        userId = userId.substring(0, userId.length() - 1);
                    }
                    if (userId != null && userId.length() > 0) {
                        int currentTag = (int) btnFillMode.getTag();
                        boolean adjustMode = currentTag != R.mipmap.fill_scale;
                        ITRTCVideoViewLayoutListener listener = mListener.get();
                        if (listener != null) {
                            listener.onChangeVideoFillMode(userId, adjustMode);
                        }
                        btnFillMode.setTag(adjustMode ? R.mipmap.fill_scale : R.mipmap.fill_adjust);
                        btnFillMode.setBackgroundResource(adjustMode ? R.mipmap.fill_scale : R.mipmap.fill_adjust);
                    }
                }
            });
        }
    }

    private void clearVideoViewExtraData(TXCloudVideoView videoView) {
        Button btnRemoteVideo = (Button) videoView.findViewById(R.id.btn_remote_video);
        btnRemoteVideo.setTag(R.mipmap.remote_video_enable);
        btnRemoteVideo.setBackgroundResource(R.mipmap.remote_video_enable);

        Button btnRemoteAudio = (Button) videoView.findViewById(R.id.btn_remote_audio);
        btnRemoteAudio.setTag(R.mipmap.remote_audio_enable);
        btnRemoteAudio.setBackgroundResource(R.mipmap.remote_audio_enable);

        Button btnFillMode = (Button) videoView.findViewById(R.id.btn_fill_mode);
        btnFillMode.setTag(R.mipmap.fill_scale);
        btnFillMode.setBackgroundResource(R.mipmap.fill_scale);
    }

    public void freshToolbarLayout() {
        for (TXCloudVideoView videoView : mVideoViewList) {
            String userId = videoView.getUserId();

            View layoutToolbar = videoView.findViewById(R.id.layout_toolbar);
            if (userId != null && userId.isEmpty() == false) {
                if (userId.equalsIgnoreCase(mSelfUserId)) {
                    View view = videoView.findViewById(R.id.layout_no_video);
                    if (view != null) {
                        Object tag = view.getTag();
                        if (tag != null) {
                            if ((int) tag == VISIBLE) {
                                view.setVisibility(VISIBLE);
                                if (layoutToolbar != null) {
                                    layoutToolbar.bringToFront();
                                    layoutToolbar.setVisibility(VISIBLE);
                                }
                            } else {
                                view.setVisibility(GONE);
                            }
                        }
                    }
//                    showToolbarButtons(videoView, false);
                } else {
                    if (videoView.getVisibility() == VISIBLE) {
                        if (layoutToolbar != null) {
                            layoutToolbar.bringToFront();
                            layoutToolbar.setVisibility(VISIBLE);
//                            showToolbarButtons(videoView, mMode == MODE_GRID);
                        }
                    } else {
                        layoutToolbar.setVisibility(GONE);
                        freshToolbarLayoutOnMemberLeave(videoView);
                    }
                }
            } else {
                layoutToolbar.setVisibility(GONE);
                freshToolbarLayoutOnMemberLeave(videoView);
            }
        }
    }

    public void freshToolbarLayoutOnMemberEnter(String userID) {
        for (TXCloudVideoView videoView : mVideoViewList) {
            String tempUserID = videoView.getUserId();
            if (tempUserID != null && tempUserID.equalsIgnoreCase(userID)) {
                View layoutToolbar = videoView.findViewById(R.id.layout_toolbar);
                if (layoutToolbar != null) {
                    layoutToolbar.bringToFront();
                    layoutToolbar.setVisibility(VISIBLE);
//                    showToolbarButtons(videoView, mMode == MODE_GRID);
                }
            }
        }
    }

    private void freshToolbarLayoutOnMemberLeave(TXCloudVideoView videoView) {
        showAudioVolumeProgressBar(videoView, false);
//        showToolbarButtons(videoView, false);
        showNoVideoLayout(videoView, false);
        clearVideoViewExtraData(videoView);
    }

    private void showToolbarButtons(TXCloudVideoView videoView, boolean bShow) {
        View view = videoView.findViewById(R.id.toolbar_buttons);
        if (view != null) {
            view.setVisibility(bShow ? VISIBLE : GONE);
        }
    }

    public void hideAllAudioVolumeProgressBar() {
        for (TXCloudVideoView videoView : mVideoViewList) {
            showAudioVolumeProgressBar(videoView, false);
        }
    }

    public void showAllAudioVolumeProgressBar() {
        for (TXCloudVideoView videoView : mVideoViewList) {
            showAudioVolumeProgressBar(videoView, true);
        }
    }

    private void showAudioVolumeProgressBar(TXCloudVideoView videoView, boolean bShow) {
        View layoutToolbar = videoView.findViewById(R.id.layout_toolbar);
        if (layoutToolbar != null) {
            if (bShow == true) layoutToolbar.bringToFront();
            layoutToolbar.setVisibility(bShow ? VISIBLE : GONE);
        }
        View view = videoView.findViewById(R.id.audio_volume);
        if (view != null) {
            view.setVisibility(bShow ? VISIBLE : GONE);
        }
    }

    private void showNoVideoLayout(TXCloudVideoView videoView, boolean bShow) {
        View view = videoView.findViewById(R.id.layout_no_video);
        if (view != null) {
            view.setVisibility(bShow ? VISIBLE : GONE);
            view.setTag(Integer.valueOf(bShow ? VISIBLE : GONE));
        }
    }

    public void resetAudioVolume() {
        for (TXCloudVideoView videoView : mVideoViewList) {
            ProgressBar progressBar = (ProgressBar) videoView.findViewById(R.id.audio_volume);
            progressBar.setProgress(0);
        }
    }

    public void updateAudioVolume(String userID, int audioVolume) {
        for (TXCloudVideoView videoView : mVideoViewList) {
            if (videoView.getVisibility() == VISIBLE) {
                ProgressBar progressBar = (ProgressBar) videoView.findViewById(R.id.audio_volume);
                String tempUserID = videoView.getUserId();
                if (tempUserID != null && tempUserID.startsWith(userID)) {
                    progressBar.setProgress(audioVolume);
                }
            }
        }
    }

    public void updateNetworkQuality(String userID, int quality) {
        for (TXCloudVideoView videoView : mVideoViewList) {
            if (videoView.getVisibility() == VISIBLE) {
                String tempUserID = videoView.getUserId();
                if (tempUserID != null && tempUserID.startsWith(userID)) {
                    ImageView imageView = (ImageView) videoView.findViewById(videoView.hashCode());
                    if (imageView == null) {
                        imageView = new ImageView(mContext);
                        imageView.setId(videoView.hashCode());
                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(60, 45, Gravity.TOP | Gravity.RIGHT);
                        params.setMargins(0, 8, 8, 0);
                        videoView.addView(imageView, params);
                    }

                    if (quality < TRTCCloudDef.TRTC_QUALITY_Excellent) {
                        quality = TRTCCloudDef.TRTC_QUALITY_Excellent;
                    }
                    if (quality > TRTCCloudDef.TRTC_QUALITY_Down) {
                        quality = TRTCCloudDef.TRTC_QUALITY_Down;
                    }

//                    if (imageView != null) {
//                        imageView.bringToFront();
//                        imageView.setVisibility(VISIBLE);
//                        imageView.setImageResource(mapNetworkQuality.get(Integer.valueOf(quality).intValue()));
//                    }
                }
            }
        }
    }

    public void updateVideoStatus(String userID, boolean bHasVideo) {
        for (TXCloudVideoView videoView : mVideoViewList) {
            if (videoView.getVisibility() == VISIBLE) {
                String tempUserID = videoView.getUserId();
                if (tempUserID != null && tempUserID.startsWith(userID)) {
                    TextView textView = videoView.findViewById(R.id.textview_userid);
                    if (textView != null) {
                        if (mSelfUserId.equalsIgnoreCase(userID)) {
                            userID += "(您自己)";
                        }
                        textView.setText(userID);
                    }
                    if (bHasVideo == false) {
                        View layoutToolbar = videoView.findViewById(R.id.layout_toolbar);
                        if (layoutToolbar != null) {
                            layoutToolbar.bringToFront();
                            layoutToolbar.setVisibility(VISIBLE);
                        }

                        showNoVideoLayout(videoView, true);
                    } else {
                        showNoVideoLayout(videoView, false);
                    }
                }
            }
        }
    }

}
