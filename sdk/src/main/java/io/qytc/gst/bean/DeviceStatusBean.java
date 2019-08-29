package io.qytc.gst.bean;

import java.io.Serializable;

public class DeviceStatusBean implements Serializable {
    private String cmd;
    private DataBean data;

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public DataBean getData() {
        if (null == data){
            data = new DataBean();
        }
        return data;
    }

    public void setData(DataBean data) {
        this.data = data;
    }

    public static class DataBean implements Serializable{
        private String acctno;
        private int    roomNo;
        private int    inRoom;
        private int    speaker;
        private int    camera;
        private int    mic;

        public String getAcctno() {
            return acctno;
        }

        public void setAcctno(String acctno) {
            this.acctno = acctno;
        }

        public int getRoomNo() {
            return roomNo;
        }

        public void setRoomNo(int roomNo) {
            this.roomNo = roomNo;
        }

        public int getInRoom() {
            return inRoom;
        }

        public void setInRoom(int inRoom) {
            this.inRoom = inRoom;
        }

        public int getSpeaker() {
            return speaker;
        }

        public void setSpeaker(int speaker) {
            this.speaker = speaker;
        }

        public int getCamera() {
            return camera;
        }

        public void setCamera(int camera) {
            this.camera = camera;
        }

        public int getMic() {
            return mic;
        }

        public void setMic(int mic) {
            this.mic = mic;
        }
    }
}
