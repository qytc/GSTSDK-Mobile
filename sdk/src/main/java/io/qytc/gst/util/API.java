package io.qytc.gst.util;

public class API {
    private static final String SERVER = "ycdj.whqunyu.com";
    private static final String PORT   = "9999";

    public static final String HOST = "http://" + SERVER + ":" + PORT;
    public static final String WS_HOST="ws://"+SERVER+":"+PORT+"/ws/msg";

    public static final String JOIN_ROOM  = "/api/meeting/joinRoom";
    public static final String EXIT_ROOM  = "/api/meeting/exitRoom";
    public static final String CANCEL_SPEAK="/api/live/cancelSpeak";
    public static final String REQUEST_SPEAK="/api/live/requestSpeak";
    public static final String generateUserSig="/api/v1/generateUserSig";
}
