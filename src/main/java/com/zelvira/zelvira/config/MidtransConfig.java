package com.zelvira.zelvira.config;

import com.midtrans.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MidtransConfig {

    @Value("${app.midtrans.server-key:${midtrans.server-key:}}")
    private String serverKey;

    @Value("${app.midtrans.client-key:${midtrans.client-key:}}")
    private String clientKey;

    @Value("${app.midtrans.is-production:${midtrans.is-production:true}}")
    private boolean isProduction;

    @Bean
    public Config midtransConfiguration() {
        return Config.builder()
                .setIsProduction(isProduction)
                .setServerKey(serverKey)
                .setClientKey(clientKey)
                .build();
    }
}
