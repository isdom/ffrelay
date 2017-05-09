package org.jocean.ffrelay;

import java.util.Map;

public class RelayStatus {
    public RelayStatus(final Map<Object, String> status) {
        this._status = status;
    }
    
    public Map<Object, String> getStatus() {
        return this._status;
    }
    
    private final Map<Object, String> _status;
}
