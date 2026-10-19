package com.tcem.enrichment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("couchdb")
public class CouchDbProperties {
    private String url = "http://localhost:5984";
    private String username = "admin";
    private String password = "password";
    private String idempotencyDatabase = "enrichment-idempotency";
    private boolean autoCreateDatabases = true;
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getIdempotencyDatabase() { return idempotencyDatabase; }
    public void setIdempotencyDatabase(String idempotencyDatabase) { this.idempotencyDatabase = idempotencyDatabase; }
    public boolean isAutoCreateDatabases() { return autoCreateDatabases; }
    public void setAutoCreateDatabases(boolean autoCreateDatabases) { this.autoCreateDatabases = autoCreateDatabases; }
}
