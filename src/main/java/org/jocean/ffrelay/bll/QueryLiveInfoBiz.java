/**
 *
 */
package org.jocean.ffrelay.bll;

import java.util.Map;

import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;

import org.jocean.event.api.AbstractFlow;
import org.jocean.event.api.BizStep;
import org.jocean.event.api.annotation.OnEvent;
import org.jocean.ffrelay.api.QueryLiveInfoRequest;
import org.jocean.ffrelay.api.QueryLiveInfoResponse;
import org.jocean.restful.OutputReactor;
import org.jocean.restful.OutputSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/live/queryLiveInfo")
public class QueryLiveInfoBiz extends AbstractFlow<QueryLiveInfoBiz> implements
        OutputSource {
	
    private static final Logger LOG = 
            LoggerFactory.getLogger(QueryLiveInfoBiz.class);
    
    @GET
    @OnEvent(event = "initWithGet")
    private BizStep onHttpPost() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("queryLiveInfo GET({})/{}/{}, req={}",
                    this, currentEventHandler().getName(), currentEvent(),
                    this._request);
        }
        return onHttpAccept();
    }

    private BizStep onHttpAccept() throws Exception {
        if ( null != this._request.getSN()) {
            responseSections(
                    this._infos.get(sn2imgurlkey(this._request.getSN())), 
                    this._infos.get(sn2rtmpkey(this._request.getSN())), 
                    this._infos.get(sn2hlskey(this._request.getSN()))
                    );
        } else {
            responseSections(null, null, null);
        }
        return null;
    }

    private String sn2imgurlkey(final String sn) {
        return sn + "-imageUrl" ;
    }

    private String sn2rtmpkey(final String sn) {
        return sn + "-rtmp" ;
    }
    
    private String sn2hlskey(final String sn) {
        return sn + "-hls" ;
    }
    
    private void responseSections(
            final String url,
            final String rtmp,
            final String hls) {
        final QueryLiveInfoResponse resp = new QueryLiveInfoResponse();
        final int errorCode = (null != url && null != rtmp && null != hls) ? 0 : 1000;
        resp.setErrorCode(errorCode);
        resp.setImageUrl(url);
        resp.setRtmp(rtmp);
        resp.setHls(hls);
        
        resp.setAccessControlAllowOrigin(_acrOrigin);
        _outputReactor.output(resp);
    }

    public void setInfos(final Map<String, String> infos) {
        this._infos = infos;
    }
    
    @Override
    public void setOutputReactor(final OutputReactor reactor) throws Exception {
        this._outputReactor = reactor;
    }
    
    @HeaderParam("Origin")
    private String _acrOrigin;
    
    @BeanParam
    private QueryLiveInfoRequest _request;
    
    private Map<String, String> _infos;
    
    private OutputReactor _outputReactor;
}
