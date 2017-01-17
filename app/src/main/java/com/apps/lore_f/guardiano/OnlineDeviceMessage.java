package com.apps.lore_f.guardiano;

/**
 * Created by 105053228 on 11/gen/2017.
 */

public class OnlineDeviceMessage {

    private String id;
    private String dateStamp;
    private String deviceToken;
    private String deviceDescription;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDateStamp() {
        return dateStamp;
    }

    public void setDateStamp(String dateStamp) {
        this.dateStamp = dateStamp;
    }

    public String getDeviceToken() {
        return deviceToken;
    }

    public void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }

    public String getDeviceDescription() {
        return deviceDescription;
    }

    public void setDeviceDescription(String deviceDescription) {
        this.deviceDescription = deviceDescription;
    }

    public OnlineDeviceMessage(){

    }

    public OnlineDeviceMessage(String dateStamp, String deviceToken ,String deviceDescription){

        this.dateStamp = dateStamp;
        this.deviceToken = deviceToken;
        this.deviceDescription = deviceDescription;

    }

}
