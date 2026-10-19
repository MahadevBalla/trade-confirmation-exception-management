package com.tcem.workitem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Apache CouchDB connectivity and the databases owned by workitem-service.
 */
@Configuration
@ConfigurationProperties(prefix = "couchdb")
public class CouchDbProperties {

    private String url = "http://localhost:5984";
    private String username = "admin";
    private String password = "password";
    private String dlqEntriesDatabase = "dlq-entries";
    private String workItemsDatabase = "work-items";
    private String auditEventsDatabase = "audit-events";
    private boolean autoCreateDatabases = true;
    private int connectionTimeoutMs = 3000;
    private int readTimeoutMs = 5000;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDlqEntriesDatabase() { return dlqEntriesDatabase; }
    public void setDlqEntriesDatabase(String dlqEntriesDatabase) { this.dlqEntriesDatabase = dlqEntriesDatabase; }

    public String getWorkItemsDatabase() { return workItemsDatabase; }
    public void setWorkItemsDatabase(String workItemsDatabase) { this.workItemsDatabase = workItemsDatabase; }

    public String getAuditEventsDatabase() { return auditEventsDatabase; }
    public void setAuditEventsDatabase(String auditEventsDatabase) { this.auditEventsDatabase = auditEventsDatabase; }

    public boolean isAutoCreateDatabases() { return autoCreateDatabases; }
    public void setAutoCreateDatabases(boolean autoCreateDatabases) { this.autoCreateDatabases = autoCreateDatabases; }

    public int getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(int connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
