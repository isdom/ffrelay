package org.jocean.ffrelay.api;

import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

/**
 * @author isdom
 *
 */
@Path("/live/queryLiveInfo")
public class QueryLiveInfoRequest {
    
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("QueryLiveInfoRequest [sn=").append(this._sn)
                .append("]");
        return builder.toString();
    }

    public String getSN() {
        return this._sn;
    }

    public void setSN(final String sn) {
        this._sn = sn;
    }
    
    @QueryParam("sn")
    private String _sn;
}
