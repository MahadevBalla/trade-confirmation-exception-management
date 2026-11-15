package com.tcem.validation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Validation-service specific runtime properties.
 */
@Configuration
@ConfigurationProperties(prefix = "validation")
public class ValidationProperties {

    private String serviceName = "validation-service";

    public ValidationProperties() {}

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
}

