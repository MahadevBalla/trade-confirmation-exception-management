package com.tcem.ingestion.couchdb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * CouchDB Mango _find query request payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FindQueryRequest {

    @JsonProperty("selector")
    private Map<String, Object> selector;

    @JsonProperty("limit")
    private Integer limit;

    @JsonProperty("skip")
    private Integer skip;

    public FindQueryRequest() {}

    public FindQueryRequest(Map<String, Object> selector, Integer limit, Integer skip) {
        this.selector = selector;
        this.limit = limit;
        this.skip = skip;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> getSelector() { return selector; }
    public void setSelector(Map<String, Object> selector) { this.selector = selector; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    public Integer getSkip() { return skip; }
    public void setSkip(Integer skip) { this.skip = skip; }

    public static class Builder {
        private Map<String, Object> selector;
        private Integer limit;
        private Integer skip;

        public Builder selector(Map<String, Object> selector) { this.selector = selector; return this; }
        public Builder limit(Integer limit) { this.limit = limit; return this; }
        public Builder skip(Integer skip) { this.skip = skip; return this; }

        public FindQueryRequest build() {
            return new FindQueryRequest(selector, limit, skip);
        }
    }
}
