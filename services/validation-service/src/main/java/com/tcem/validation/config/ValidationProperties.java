package com.tcem.validation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Validation-service specific runtime properties.
 */
@Configuration
@ConfigurationProperties(prefix = "validation")
public class ValidationProperties {

    private String inputTopic = "trade.enriched";
    private String serviceName = "validation-service";

    public ValidationProperties() {}

    public String getInputTopic() { return inputTopic; }
    public void setInputTopic(String inputTopic) { this.inputTopic = inputTopic; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
}

