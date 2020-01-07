package io.qytc.gst.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;

import io.qytc.gst.util.API;
import io.qytc.gst.util.HttpUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class AuthActivity extends Activity {

    private Context mContext;
    private AlertDialog alertDialog;
    private String selfUserId;
    private int roomId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_auth);

        Intent intent = getIntent();
        selfUserId = intent.getStringExtra("userId");
        roomId = intent.getIntExtra("roomId", 0);

        joinRoomAuth();

    }

    private void joinRoomAuth() {
        ProgressDialog.Builder builder = new ProgressDialog.Builder(this);
        builder.setCancelable(false);
        builder.setMessage("请稍等...");
        alertDialog = builder.create();
        alertDialog.show();

        JSONObject jo = new JSONObject();
        jo.put("roomNo", roomId);
        jo.put("acctno", selfUserId);
        jo.put("device", "Android");

        HttpUtil.getInstance(API.JOIN_ROOM).post(jo.toString(), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                closeDialog();
                showMsg("加入房间失败，错误内容：" + e.getMessage());
                finish();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                closeDialog();
                if (response.code() == 200) {
                    String jsonStr = response.body().string();
                    JSONObject jo = JSON.parseObject(jsonStr);
                    if (jo.get("code").equals("0")) {//验证通过
                        checkSuccessEnterRoom();
                    } else if (jo.get("code").equals("2")) {//请输入密码
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                inputPassword();
                            }
                        });
                        showMsg(jo.getString("msg"));
                    } else if (jo.get("code").equals("3")) {//密码错误
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                inputPassword();
                            }
                        });
                        showMsg(jo.getString("msg"));
                    } else if (jo.get("code").equals("-2")) {
                        showMsg(jo.getString("msg"));
                        finish();
                    } else {//其他
                        showMsg(jo.getString("msg"));
                        finish();
                    }
                } else {
                    showMsg("服务器出现异常了");
                    finish();
                }
            }
        });
    }

    private void inputPassword() {

        findViewById(R.id.password_layout).setVisibility(View.VISIBLE);
        final TextView password_et = findViewById(R.id.password_et);

        Button confirm_btn = findViewById(R.id.confirm_btn);
        Button cancel_btn = findViewById(R.id.cancel_btn);

        confirm_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                authRoomPassword(password_et.getText().toString());
            }
        });
        cancel_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    private void closeDialog() {
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
    }

    private void authRoomPassword(String password) {
        findViewById(R.id.password_layout).setVisibility(View.GONE);
        alertDialog.show();

        JSONObject jo = new JSONObject();
        jo.put("roomNo", roomId);
        jo.put("acctno", selfUserId);
        jo.put("password", password);

        HttpUtil.getInstance(API.JOIN_ROOM).post(jo.toJSONString(), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                closeDialog();
                showMsg("验证码密码请求错误");
                finish();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                closeDialog();
                if (response.code() == 200) {
                    String string = response.body().string();
                    JSONObject jo = JSON.parseObject(string);
                    if (jo.get("code").equals("0")) {
                        findViewById(R.id.password_layout).setVisibility(View.GONE);
                        checkSuccessEnterRoom();
                    } else {
                        showMsg(jo.getString("msg"));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                inputPassword();
                            }
                        });
                    }
                } else {
                    showMsg("服务器出现异常了");
                    finish();
                }
            }
        });
    }

    private void checkSuccessEnterRoom() {
        Intent intent = getIntent();
        intent.setClass(this, RoomActivity.class);
        startActivity(intent);
        finish();
    }

    private void showMsg(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
