package io.qytc.gst.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.tencent.trtc.TRTCCloudDef;

import java.io.IOException;

import io.qytc.gst.util.API;
import io.qytc.gst.util.HttpUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;


public class LoginActivity extends Activity {
    private Integer mSdkAppId;
    private AlertDialog alertDialog;
    private int roomId;
    private String userId;
    private int role;
    private Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_auth);

        //获取前一个页面得到的进房参数
        intent = getIntent();

        mSdkAppId = intent.getIntExtra("sdkAppId", 0);
        if (mSdkAppId == 0) {
            showMsg("[sdkAppId]有误，请重新输入");
            finish();
            return;
        }
        roomId = intent.getIntExtra("roomId", 0);
        if (roomId == 0) {
            showMsg("[sdkAppId]有误，请重新输入");
            finish();
            return;
        }
        userId = intent.getStringExtra("userId");
        if (TextUtils.isEmpty(userId)) {
            showMsg("[userId]有误，请重新输入");
            finish();
            return;
        }
        role = intent.getIntExtra("role", 0);
        if (role != 20 && role != 21) {
            showMsg("[role]有误，请重新输入");
            finish();
            return;
        }

        ProgressDialog.Builder builder = new ProgressDialog.Builder(this);
        builder.setCancelable(false);
        builder.setMessage("请稍等...");
        alertDialog = builder.create();
        onJoinRoom();
    }

    private void onJoinRoom() {
        if (alertDialog != null && !alertDialog.isShowing()) {
            alertDialog.show();
        }

        final int appScene = TRTCCloudDef.TRTC_APP_SCENE_LIVE;

        JSONObject jo = new JSONObject();
        jo.put("appId", mSdkAppId);
        jo.put("userId", userId);
        HttpUtil.getInstance(API.generateUserSig).post(jo.toJSONString(), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                closeDialog();
                showMsg("请求签名发生错误:" + e.getMessage());
                finish();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                closeDialog();
                if (response.code() == 200) {
                    JSONObject object = JSON.parseObject(response.body().string());
                    if (object.get("code").equals("0")) {
                        String userSig = object.getString("data");
                        intent.setClass(getContext(), AuthActivity.class);
                        intent.putExtra("userSig", userSig);
                        startActivity(intent);
                    } else {
                        showMsg(object.getString("msg"));
                    }
                } else {
                    showMsg("从服务器获取userSig失败");
                }
                finish();
            }
        });
    }

    private void closeDialog() {
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
    }

    private void showMsg(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Context getContext() {
        return this;
    }
}
