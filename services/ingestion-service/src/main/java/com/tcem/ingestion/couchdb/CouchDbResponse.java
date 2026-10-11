package com.tcem.ingestion.couchdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard CouchDB HTTP operation response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CouchDbResponse {

    @JsonProperty("ok")
    private Boolean ok;

    @JsonProperty("id")
    private String id;

    @JsonProperty("rev")
    private String rev;

    @JsonProperty("error")
    private String error;

    @JsonProperty("reason")
    private String reason;

    public CouchDbResponse() {}

    public CouchDbResponse(Boolean ok, String id, String rev, String error, String reason) {
        this.ok = ok;
        this.id = id;
        this.rev = rev;
        this.error = error;
        this.reason = reason;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Boolean getOk() { return ok; }
    public void setOk(Boolean ok) { this.ok = ok; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRev() { return rev; }
    public void setRev(String rev) { this.rev = rev; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public static class Builder {
        private Boolean ok;
        private String id;
        private String rev;
        private String error;
        private String reason;

        public Builder ok(Boolean ok) { this.ok = ok; return this; }
        public Builder id(String id) { this.id = id; return this; }
        public Builder rev(String rev) { this.rev = rev; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }

        public CouchDbResponse build() {
            return new CouchDbResponse(ok, id, rev, error, reason);
        }
    }
}
