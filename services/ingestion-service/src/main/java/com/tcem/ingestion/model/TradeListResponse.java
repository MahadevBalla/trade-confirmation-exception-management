package com.tcem.ingestion.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tcem.ingestion.model.document.TradeDocument;

import java.util.Collections;
import java.util.List;

/**
 * Paginated trade list response for GET /trades (FR3, FR11).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeListResponse {

    private List<TradeDocument> trades;
    private int page;
    private int size;
    private int count;
    private String statusFilter;

    public TradeListResponse() {}

    public TradeListResponse(List<TradeDocument> trades, int page, int size, String statusFilter) {
        this.trades = trades != null ? trades : Collections.emptyList();
        this.page = page;
        this.size = size;
        this.count = this.trades.size();
        this.statusFilter = statusFilter;
    }

    public List<TradeDocument> getTrades() { return trades; }
    public void setTrades(List<TradeDocument> trades) {
        this.trades = trades != null ? trades : Collections.emptyList();
        this.count = this.trades.size();
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public String getStatusFilter() { return statusFilter; }
    public void setStatusFilter(String statusFilter) { this.statusFilter = statusFilter; }
}
