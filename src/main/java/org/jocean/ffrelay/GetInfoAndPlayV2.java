package org.jocean.ffrelay;

import java.net.URI;
import java.net.URISyntaxException;

import javax.net.ssl.SSLException;
import javax.ws.rs.QueryParam;

import org.jocean.http.Feature;
import org.jocean.http.client.HttpClient;
import org.jocean.http.client.impl.DefaultHttpClient;
import org.jocean.http.rosa.SignalClient;
import org.jocean.http.rosa.impl.DefaultSignalClient;

import com.alibaba.fastjson.annotation.JSONField;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

public class GetInfoAndPlayV2 {

    public static class Req {
        
        public Req(final String sn) {
            this._sn = sn;
        }
        
        @QueryParam("from")
        private String _from = "mpc_ipcam_web";
        
        @QueryParam("sn")
        private String _sn;
        
        @QueryParam("taskid")
        private long _taskid = System.currentTimeMillis();
        
        @QueryParam("_")
        private long _id = System.currentTimeMillis();
    }
    
    public static class Resp {
        @Override
        public String toString() {
            return "Resp [errorCode=" + _errorCode + ", playInfo=" + _playInfo + "]";
        }

        @JSONField(name="errorCode")
        public int getErrorCode() {
            return _errorCode;
        }

        @JSONField(name="errorCode")
        public void setErrorCode(int errorCode) {
            this._errorCode = errorCode;
        }

        @JSONField(name="playInfo")
        public PlayInfo getPlayInfo() {
            return _playInfo;
        }

        @JSONField(name="playInfo")
        public void setPlayInfo(PlayInfo playInfo) {
            this._playInfo = playInfo;
        }
        
        public static class PlayInfo {
            @Override
            public String toString() {
                return "PlayInfo [rtmp=" + _rtmp + ", hls=" + _hls + "]";
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

            private String _rtmp;
            private String _hls;
        }
        private int _errorCode;
        private PlayInfo _playInfo;
    }
    
    public static void main(String[] args) throws SSLException, URISyntaxException {
        final SslContext sslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build();

        final HttpClient client = new DefaultHttpClient(
                new Feature.ENABLE_SSL(sslCtx), 
                Feature.ENABLE_LOGGING_OVER_SSL);
        
        final SignalClient signal = new DefaultSignalClient(client);
        
        final Resp resp = 
        signal.interaction()
        .feature(new SignalClient.UsingUri(new URI("https://live2.jia.360.cn")))
        .feature(new SignalClient.UsingPath("/public/getInfoAndPlayV2"))
        .feature(new SignalClient.DecodeResponseBodyAs(Resp.class))
        .request(new Req("36150701675"))
        .<Resp>build()
        .toBlocking().single();
        
        System.out.println(resp);
    }

}
