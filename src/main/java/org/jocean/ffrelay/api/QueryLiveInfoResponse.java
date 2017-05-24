package org.jocean.ffrelay.api;

import javax.ws.rs.HeaderParam;

import com.alibaba.fastjson.annotation.JSONField;

/**
 * @author isdom
 *
 */
public class QueryLiveInfoResponse {

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("QueryLiveInfoResponse [errorCode=").append(_errorCode)
            .append(", imageUrl=").append(_imageUrl)
            .append(", rtmp=").append(_rtmp)
            .append(", hls=").append(_hls)
            .append("]");
        return builder.toString();
    }

    @JSONField(name="errorCode")
    public int getErrorCode() {
        return _errorCode;
    }

    @JSONField(name="errorCode")
    public void setErrorCode(int errorCode) {
        this._errorCode = errorCode;
    }
    
    @JSONField(name="imageUrl")
    public String getImageUrl() {
        return _imageUrl;
    }

    @JSONField(name="imageUrl")
    public void setImageUrl(String url) {
        this._imageUrl = url;
    }
    
    @JSONField(name="rtmp")
    public String getRtmp() {
        return _rtmp;
    }

    @JSONField(name="rtmp")
    public void setRtmp(String rtmp) {
        this._rtmp = rtmp;
    }

    @JSONField(name="hls")
    public String getHls() {
        return _hls;
    }

    @JSONField(name="hls")
    public void setHls(String hls) {
        this._hls = hls;
    }

    private int _errorCode = 0;
    private String _imageUrl;
    private String _rtmp;
    private String _hls;

    public void setAccessControlAllowOrigin(final String accessControlAllowOrigin) {
        this._accessControlAllowOrigin = accessControlAllowOrigin;
    }
    
    @HeaderParam("Access-Control-Allow-Origin")
    private String _accessControlAllowOrigin;
}
