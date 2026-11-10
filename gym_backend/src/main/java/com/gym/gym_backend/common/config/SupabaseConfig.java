package com.gym.gym_backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SupabaseConfig {
    @Bean
    RestClient supabaseRestClient(@Value("${supabase.url}") String url,
            @Value("${supabase.secret-key}") String secretKey) {
        return RestClient.builder().baseUrl(url.replaceAll("/$", "") + "/rest/v1").defaultHeader("apikey", secretKey)
                .defaultHeader("Accept-Profile", "public").defaultHeader("Content-Profile", "public").build();
    }

}
