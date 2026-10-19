package com.tcem.validation.couchdb;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Mango {@code _find} query request DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FindQueryRequest {

    private Map<String, Object> selector;
    private Integer limit;
    private Integer skip;

    public FindQueryRequest() {}

    public FindQueryRequest(Map<String, Object> selector) {
        this.selector = selector;
    }

    public FindQueryRequest(Map<String, Object> selector, Integer limit, Integer skip) {
        this.selector = selector;
        this.limit = limit;
        this.skip = skip;
    }

    public Map<String, Object> getSelector() { return selector; }
    public void setSelector(Map<String, Object> selector) { this.selector = selector; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    public Integer getSkip() { return skip; }
    public void setSkip(Integer skip) { this.skip = skip; }
}

