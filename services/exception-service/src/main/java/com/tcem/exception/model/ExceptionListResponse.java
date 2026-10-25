package com.tcem.exception.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tcem.exception.model.document.ExceptionDocument;

import java.util.Collections;
import java.util.List;

/**
 * Paginated REST response DTO for exception list queries — returned by
 * {@code GET /exceptions?tradeId=&status=&category=&page=&size=} (FR11).
 * <p>
 * Mirrors the {@code TradeListResponse} pattern used by the ingestion-service
 * so the React Operations UI can apply the same pagination component.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExceptionListResponse {

    private List<ExceptionDocument> exceptions;
    private int page;
    private int size;
    private int count;
    private String tradeIdFilter;
    private String statusFilter;
    private String categoryFilter;

    public ExceptionListResponse() {}

    public ExceptionListResponse(List<ExceptionDocument> exceptions, int page, int size,
                                  String tradeIdFilter, String statusFilter, String categoryFilter) {
        this.exceptions    = exceptions != null ? exceptions : Collections.emptyList();
        this.page          = page;
        this.size          = size;
        this.count         = this.exceptions.size();
        this.tradeIdFilter = tradeIdFilter;
        this.statusFilter  = statusFilter;
        this.categoryFilter = categoryFilter;
    }

    public List<ExceptionDocument> getExceptions() { return exceptions; }
    public void setExceptions(List<ExceptionDocument> exceptions) {
        this.exceptions = exceptions != null ? exceptions : Collections.emptyList();
        this.count = this.exceptions.size();
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public String getTradeIdFilter() { return tradeIdFilter; }
    public void setTradeIdFilter(String tradeIdFilter) { this.tradeIdFilter = tradeIdFilter; }

    public String getStatusFilter() { return statusFilter; }
    public void setStatusFilter(String statusFilter) { this.statusFilter = statusFilter; }

    public String getCategoryFilter() { return categoryFilter; }
    public void setCategoryFilter(String categoryFilter) { this.categoryFilter = categoryFilter; }
}

