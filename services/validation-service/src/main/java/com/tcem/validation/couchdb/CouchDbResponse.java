package com.tcem.validation.couchdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal CouchDB document write response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CouchDbResponse {

    private String id;
    private String rev;
    private boolean ok;

    public CouchDbResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRev() { return rev; }
    public void setRev(String rev) { this.rev = rev; }

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
}

