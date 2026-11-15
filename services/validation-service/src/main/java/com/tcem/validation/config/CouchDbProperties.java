package com.tcem.validation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for CouchDB connectivity.
 * Bound from the {@code couchdb.*} namespace in {@code application.yml}.
 */
@Configuration
@ConfigurationProperties(prefix = "couchdb")
public class CouchDbProperties {

    private String url = "http://localhost:5984";
    private String username = "admin";
    private String password = "password";
    private String confirmationsDatabase = "confirmations";
    private String idempotencyDatabase = "validation-idempotency";
    private boolean autoCreateDatabases = true;
    private int connectionTimeoutMs = 3000;
    private int readTimeoutMs = 5000;

    public CouchDbProperties() {}

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmationsDatabase() { return confirmationsDatabase; }
    public void setConfirmationsDatabase(String confirmationsDatabase) { this.confirmationsDatabase = confirmationsDatabase; }

    public String getIdempotencyDatabase() { return idempotencyDatabase; }
    public void setIdempotencyDatabase(String idempotencyDatabase) { this.idempotencyDatabase = idempotencyDatabase; }

    public boolean isAutoCreateDatabases() { return autoCreateDatabases; }
    public void setAutoCreateDatabases(boolean autoCreateDatabases) { this.autoCreateDatabases = autoCreateDatabases; }

    public int getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(int connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}

