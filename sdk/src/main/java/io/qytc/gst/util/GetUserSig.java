package io.qytc.gst.util;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


/**
 * Module:   GetUserSig
 *
 * Function: 用于获取组装 TRTCParam 所必须的 UserSig，腾讯云使用 UserSig 进行安全校验，保护您的 TRTC 流量不被盗用
 */
public class GetUserSig {
    private static final String TAG        = GetUserSig.class.getSimpleName();
    private final static String         SERVER_URL = "http://ums1.whqunyu.com:8888/api/v1/generateUserSig";
    private final static String JSON_ERRORCODE = "code";
    private final static String JSON_ERRORINFO = "msg";
    private final static String JSON_DATA = "data";
    private Context mContext;
    public GetUserSig(Context context){
        this.mContext=context;
    }

    public interface IGetUserSigListener {
        void onComplete(String userSig, String errMsg);
    }

    /**
     * @param userId 用户ID
     * @param appId 应用ID
     * @param listener
     */
    public void getUserSigFromServer(String userId, String appId, final IGetUserSigListener listener) {

        OkHttpClient httpClient=new OkHttpClient();
        try {
            JSONObject jsonReq = new JSONObject();
            jsonReq.put("userId", userId);
            jsonReq.put("appId",appId);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonReq.toString());
            Request req = new Request.Builder()
                    .url(SERVER_URL)
                    .post(body)
                    .build();
            httpClient.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.w(TAG, "loadUserSig->fail: "+e.toString());
                    if (listener != null) {
                        listener.onComplete(null, "http request failed");
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()){
                        Log.w(TAG, "loadUserSig->fail: "+response.message());
                        if (listener != null) {
                            listener.onComplete(null, response.message());
                        }
                    }else{
                        try {
                            JSONTokener jsonTokener = new JSONTokener(response.body().string());
                            JSONObject msgJson = (JSONObject) jsonTokener.nextValue();
                            int code = msgJson.getInt(JSON_ERRORCODE);
                            if (0 != code){
                                if (listener != null) {
                                    listener.onComplete(null, msgJson.getString(JSON_ERRORINFO));
                                }
                            }else{
                                String userSig = msgJson.getString(JSON_DATA);
                                if (listener != null) {
                                    listener.onComplete(userSig, msgJson.getString(JSON_ERRORINFO));
                                }
                            }
                        }catch (Exception e){
                            Log.i(TAG, "loadUserSig->exception: "+e.toString());
                            if (listener != null) {
                                listener.onComplete(null, e.toString());
                            }
                        }
                    }
                }
            });
        } catch (Exception e){
            if (listener != null) {
                listener.onComplete(null, e.toString());
            }
        }
    }

}
