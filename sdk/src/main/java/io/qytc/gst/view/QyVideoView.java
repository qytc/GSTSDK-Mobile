package io.qytc.gst.view;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.tencent.rtmp.ui.TXCloudVideoView;

import io.qytc.gst.sdk.R;

public class QyVideoView extends RelativeLayout {

    private Context mContext;
    private TXCloudVideoView mVideoView;
    private TextView mTvUserName;
    private String mUserName;
    private String mUserId;

    public QyVideoView(Context context) {
        super(context);
        mContext = context;
        initView();
    }

    //初始化
    private void initView() {
        LayoutInflater.from(mContext).inflate(R.layout.qy_video_view, this);

        mVideoView = findViewById(R.id.video_view);
        mTvUserName = findViewById(R.id.user_name);

        mVideoView.setUserId(null);
    }

    //获取视频控件
    public TXCloudVideoView getVideoView() {
        return mVideoView;
    }

    //设置视频播放ID和用户名字
    public void setUserId(String userId, String userName) {
        mVideoView.setUserId(userId);
        mUserId = userId;
        mUserName = userName;
        mTvUserName.setText(userName);
    }

    //获取视频控件ID
    public String getUserId() {
        return mVideoView.getUserId();
    }

    //获取用户名字
    public String getUserName() {
        return mUserName;
    }

}
