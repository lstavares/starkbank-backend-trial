package br.com.leandrotavares.starkbanktrial.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(InvoiceIssuingProperties.class)
public class InvoiceIssuingConfig {
}
