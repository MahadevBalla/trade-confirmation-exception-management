package com.tcem.ingestion.couchdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * CouchDB Mango _find query response payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FindQueryResponse<T> {

    @JsonProperty("docs")
    private List<T> docs = new ArrayList<>();

    @JsonProperty("bookmark")
    private String bookmark;

    @JsonProperty("warning")
    private String warning;

    public FindQueryResponse() {}

    public FindQueryResponse(List<T> docs, String bookmark, String warning) {
        this.docs = docs != null ? docs : new ArrayList<>();
        this.bookmark = bookmark;
        this.warning = warning;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public List<T> getDocs() { return docs; }
    public void setDocs(List<T> docs) { this.docs = docs; }

    public String getBookmark() { return bookmark; }
    public void setBookmark(String bookmark) { this.bookmark = bookmark; }

    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }

    public static class Builder<T> {
        private List<T> docs = new ArrayList<>();
        private String bookmark;
        private String warning;

        public Builder<T> docs(List<T> docs) { this.docs = docs; return this; }
        public Builder<T> bookmark(String bookmark) { this.bookmark = bookmark; return this; }
        public Builder<T> warning(String warning) { this.warning = warning; return this; }

        public FindQueryResponse<T> build() {
            return new FindQueryResponse<>(docs, bookmark, warning);
        }
    }
}
