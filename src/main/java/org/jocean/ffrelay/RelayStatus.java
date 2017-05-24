package org.jocean.ffrelay;

import java.util.Map;

public class RelayStatus {
    public RelayStatus(final Map<Object, String> status,
            final Map<String, String> infos) {
        this._status = status;
        this._infos = infos;
    }
    
    public Map<Object, String> getStatus() {
        return this._status;
    }
    
    public Map<String, String> getSourceInfos() {
        return this._infos;
    }
    
    private final Map<Object, String> _status;
    private final Map<String, String> _infos;
}
