package com.apps.lore_f.guardiano;

import java.util.List;

/**
 * Created by 105053228 on 11/gen/2017.
 */

public class PictureTakenMessage {

    private String id;
    private String dateStamp;
    private String generalInfo;
    private String pictureURL;
    private String sourceDevice;

    public String getSourceDevice() {
        return sourceDevice;
    }

    public void setSourceDevice(String sourceDevice) {
        this.sourceDevice = sourceDevice;
    }

    public String getPictureURL() {
        return pictureURL;
    }

    public void setPictureURL(String pictureURL) {
        this.pictureURL = pictureURL;
    }

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

    public String getGeneralInfo() {
        return generalInfo;
    }

    public void setGeneralInfo(String generalInfo) {
        this.generalInfo = generalInfo;
    }

    public PictureTakenMessage(){

    }

    public PictureTakenMessage(String dateStamp, String generalInfo, String pictureURL, String sourceDevice){

        this.dateStamp = dateStamp;
        this.generalInfo= generalInfo;
        this.pictureURL = pictureURL;
        this.sourceDevice = sourceDevice;

    }

}
