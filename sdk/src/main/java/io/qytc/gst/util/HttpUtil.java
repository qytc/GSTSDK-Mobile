package io.qytc.gst.util;

import java.util.concurrent.TimeUnit;

import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class HttpUtil {
    private static String       mPath;
    private static OkHttpClient okHttpClient;
    private static MediaType    MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static HttpUtil     instance;

    public static HttpUtil getInstance(String path) {
        mPath = path;
        if (okHttpClient == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS);
            okHttpClient = builder.build();
        }
        if (instance == null) {
            instance = new HttpUtil();
        }
        return instance;
    }

    public void post(String jsonBody,Callback callback) {
        RequestBody body = RequestBody.create(MEDIA_TYPE, jsonBody);
        Request request = new Request.Builder().url(API.HOST + mPath).post(body).build();
        okHttpClient.newCall(request).enqueue(callback);
    }

}
