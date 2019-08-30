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


/**
 * Module:   LoginActivity
 * <p>
 * Function: 该界面可以让用户输入一个【房间号】和一个【用户名】
 * <p>
 * Notice:
 * <p>
 * （1）房间号为数字类型，用户名为字符串类型
 * <p>
 * （2）在真实的使用场景中，房间号大多不是用户手动输入的，而是系统分配的，
 * 比如视频会议中的会议号是会控系统提前预定好的，客服系统中的房间号也是根据客服员工的工号决定的。
 */
public class LoginActivity extends Activity {
    private Integer     mSdkAppId;
    private AlertDialog alertDialog;
    private int         roomId;
    private String      userId;
    private int         role;
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

    /**
     * Function: 读取用户输入，并创建（或加入）音视频房间
     * <p>
     * 此段示例代码最主要的作用是组装 TRTC SDK 进房所需的 TRTCParams
     * <p>
     * TRTCParams.sdkAppId => 可以在腾讯云实时音视频控制台（https://console.cloud.tencent.com/rav）获取
     * TRTCParams.userId   => 此处即用户输入的用户名，它是一个字符串
     * TRTCParams.roomId   => 此处即用户输入的音视频房间号，比如 125
     * TRTCParams.userSig  => 此处示例代码展示了两种获取 usersig 的方式，一种是从【控制台】获取，一种是从【服务器】获取
     * <p>
     * （1）控制台获取：可以获得几组已经生成好的 userid 和 usersig，他们会被放在一个 json 格式的配置文件中，仅适合调试使用
     * （2）服务器获取：直接在服务器端用我们提供的源代码，根据 userid 实时计算 usersig，这种方式安全可靠，适合线上使用
     * <p>
     * 参考文档：https://cloud.tencent.com/document/product/647/17275
     */
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
                        intent.setClass(getContext(),AuthActivity.class);
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
