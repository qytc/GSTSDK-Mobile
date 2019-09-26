package io.qytc.gstdemo;

import android.Manifest;
import android.app.Activity;
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

import java.util.ArrayList;
import java.util.List;

import io.qytc.gst.sdk.LoginActivity;
import io.qytc.gst.util.ThirdLoginConstant;

public class MainActivity extends Activity implements View.OnClickListener {
    private final static int         REQ_PERMISSION_CODE = 0x1000;
    private              String      mUserId             = "";
    private final        Integer     mSdkAppId           = 1400222844;

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

        // 申请动态权限
        checkPermission();
    }


    private void onJoinRoom(final int roomId, final String userId) {

        RadioButton rbAnchor = findViewById(R.id.rb_anchor);
        final int role = rbAnchor.isChecked() ? ThirdLoginConstant.Anchor : ThirdLoginConstant.Audience;

        Intent intent = new Intent(getContext(), LoginActivity.class);
        intent.putExtra(ThirdLoginConstant.ROOMID, roomId);
        intent.putExtra(ThirdLoginConstant.USERID, userId);
        intent.putExtra(ThirdLoginConstant.SDKAPPID, mSdkAppId);
        intent.putExtra(ThirdLoginConstant.ROLE, role);

        saveUserInfo(String.valueOf(roomId), userId);
        startActivity(intent);
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
                ActivityCompat.requestPermissions(MainActivity.this,
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
}
