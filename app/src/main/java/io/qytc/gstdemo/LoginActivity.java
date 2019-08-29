package io.qytc.gstdemo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.tencent.trtc.TRTCCloud;
import com.tencent.trtc.TRTCCloudDef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.qytc.gst.sdk.AuthActivity;
import io.qytc.gst.sdk.RoomActivity;
import io.qytc.gst.util.API;
import io.qytc.gst.util.GetUserSig;
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
public class LoginActivity extends Activity implements View.OnClickListener {
    private final static int        REQ_PERMISSION_CODE = 0x1000;
    private              GetUserSig mUserInfoLoader;
    private              String     mUserId             = "";
    private final        Integer    mSdkAppId           = 1400222844;
    private AlertDialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        final EditText etRoomId = findViewById(R.id.et_room_name);
        final EditText etUserId = findViewById(R.id.et_user_name);

        loadUserInfo(etRoomId, etUserId);

        RadioButton rbLive = findViewById(R.id.rb_live);
        rbLive.setOnClickListener(this);

        RadioButton rbVideoCall = findViewById(R.id.rb_videocall);
        rbVideoCall.setOnClickListener(this);

        Button tvEnterRoom = findViewById(R.id.tv_enter);
        tvEnterRoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startJoinRoom();
            }
        });

        // 如果配置有config文件，则从config文件中选择userId
        mUserInfoLoader = new GetUserSig(this);

        // 申请动态权限
        checkPermission();

        ProgressDialog.Builder builder = new ProgressDialog.Builder(this);
        builder.setCancelable(false);
        builder.setMessage("请稍等...");
        alertDialog = builder.create();

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
    private void onJoinRoom(final int roomId, final String userId) {
        if(alertDialog!=null && !alertDialog.isShowing()){
            alertDialog.show();
        }

        RadioButton rbAnchor = findViewById(R.id.rb_anchor);
        final int role = rbAnchor.isChecked() ? TRTCCloudDef.TRTCRoleAnchor : TRTCCloudDef.TRTCRoleAudience;

        RadioButton rbLive = findViewById(R.id.rb_live);
        final int appScene = rbLive.isChecked() ? TRTCCloudDef.TRTC_APP_SCENE_LIVE : TRTCCloudDef.TRTC_APP_SCENE_VIDEOCALL;

        mUserInfoLoader.getUserSigFromServer(userId, String.valueOf(mSdkAppId), new GetUserSig.IGetUserSigListener() {
            @Override
            public void onComplete(String userSig, String errMsg) {
                closeDialog();

                if (!TextUtils.isEmpty(userSig)) {

                    Intent intent = new Intent(getContext(), AuthActivity.class);
                    intent.putExtra("roomId", roomId);
                    intent.putExtra("userId", userId);
                    intent.putExtra("sdkAppId", mSdkAppId);
                    intent.putExtra("userSig", userSig);
                    intent.putExtra("role", role);
                    intent.putExtra("appScene",appScene);

                    saveUserInfo(String.valueOf(roomId), userId);
                    startActivity(intent);


                } else {

                    showMsg("从服务器获取userSig失败");
                }
            }
        });
    }

    private void joinRoomAuth(final JSONObject loginInfo) {
        JSONObject jo = new JSONObject();
        jo.put("roomNo", loginInfo.getInteger("roomId"));
        jo.put("acctno", loginInfo.getString("userId"));

        HttpUtil.getInstance(API.JOIN_ROOM).post(jo.toString(), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                closeDialog();
                showMsg("加入房间失败，错误内容：" + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                closeDialog();
                String jsonStr = response.body().string();
                try {
                    JSONObject jo = JSON.parseObject(jsonStr);
                    if (jo.get("code").equals("0")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                                Intent intent = new Intent(getContext(), RoomActivity.class);
                                intent.putExtra("roomId", loginInfo.getInteger("roomId"));
                                intent.putExtra("userId", loginInfo.getInteger("userId"));
                                intent.putExtra("sdkAppId", loginInfo.getInteger("sdkAppId"));
                                intent.putExtra("userSig", loginInfo.getString("userSig"));
                                intent.putExtra("role", loginInfo.getInteger("role"));
                                intent.putExtra("appScene", loginInfo.getInteger("appScene"));

                                saveUserInfo(loginInfo.getString("roomId"), loginInfo.getString("userId"));
                                startActivity(intent);

                            }
                        });
                    } else {
                        showMsg(jo.getString("msg"));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    showMsg("json数据解析失败");
                }
            }
        });
    }

    private void closeDialog(){
        if(alertDialog!=null && alertDialog.isShowing()){
            alertDialog.dismiss();
        }
    }

    private void startJoinRoom() {
        final EditText etRoomId = findViewById(R.id.et_room_name);
        final EditText etUserId = findViewById(R.id.et_user_name);
        int roomId = 123;
        try {
            roomId = Integer.valueOf(etRoomId.getText().toString());
        } catch (Exception e) {
            Toast.makeText(getContext(), "请输入有效的房间号", Toast.LENGTH_SHORT).show();
            return;
        }
        final String userId = etUserId.getText().toString();
        if (TextUtils.isEmpty(userId)) {
            Toast.makeText(getContext(), "请输入有效的用户名", Toast.LENGTH_SHORT).show();
            return;
        }

        onJoinRoom(roomId, userId);
    }

    private Context getContext() {
        return this;
    }

    //////////////////////////////////    动态权限申请   ////////////////////////////////////////

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissions = new ArrayList<>();
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO)) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (permissions.size() != 0) {
                ActivityCompat.requestPermissions(LoginActivity.this,
                        (String[]) permissions.toArray(new String[0]),
                        REQ_PERMISSION_CODE);
                return false;
            }
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQ_PERMISSION_CODE:
                for (int ret : grantResults) {
                    if (PackageManager.PERMISSION_GRANTED != ret) {
                        Toast.makeText(getContext(), "用户没有允许需要的权限，使用可能会受到限制！", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            default:
                break;
        }
    }

    private void saveUserInfo(String roomId, String userId) {
        try {
            mUserId = userId;
            SharedPreferences shareInfo = this.getSharedPreferences("per_data", 0);
            SharedPreferences.Editor editor = shareInfo.edit();
            editor.putString("userId", userId);
            editor.putString("roomId", roomId);
            editor.putLong("userTime", System.currentTimeMillis());
            editor.commit();
        } catch (Exception e) {

        }
    }

    private void loadUserInfo(EditText etRoomId, EditText etUserId) {
        try {
            TRTCCloud.getSDKVersion();
            SharedPreferences shareInfo = this.getSharedPreferences("per_data", 0);
            mUserId = shareInfo.getString("userId", "");
            String roomId = shareInfo.getString("roomId", "");
            if (TextUtils.isEmpty(roomId)) {
                etRoomId.setText("999");
            } else {
                etRoomId.setText(roomId);
            }
            if (TextUtils.isEmpty(mUserId)) {
                etUserId.setText(String.valueOf(System.currentTimeMillis() % 1000000));
            } else {
                etUserId.setText(mUserId);
            }
        } catch (Exception e) {

        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.rb_live: {
                findViewById(R.id.role).setVisibility(View.VISIBLE);
                break;
            }
            case R.id.rb_videocall: {
                findViewById(R.id.role).setVisibility(View.GONE);
                break;
            }
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
}
