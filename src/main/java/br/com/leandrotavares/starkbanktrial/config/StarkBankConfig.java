package br.com.leandrotavares.starkbanktrial.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        StarkBankProperties.class,
        TransferDestinationProperties.class
})
public class StarkBankConfig {
}
