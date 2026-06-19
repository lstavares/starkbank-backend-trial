package br.com.leandrotavares.starkbanktrial.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "invoice.scheduler")
public class InvoiceIssuingProperties {

    private boolean enabled = true;
    private int intervalHours = 3;
    private int maxBatches = 8;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getIntervalHours() {
        return intervalHours;
    }

    public void setIntervalHours(int intervalHours) {
        this.intervalHours = intervalHours;
    }

    public int getMaxBatches() {
        return maxBatches;
    }

    public void setMaxBatches(int maxBatches) {
        this.maxBatches = maxBatches;
    }
}
