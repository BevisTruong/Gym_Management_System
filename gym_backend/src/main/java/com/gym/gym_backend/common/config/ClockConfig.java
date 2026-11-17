package com.gym.gym_backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;
import java.time.Instant;

@Configuration
public class ClockConfig {
    @Bean
    Clock applicationClock(@Value("${app.time-zone:Asia/Ho_Chi_Minh}") String zone,
            @Value("${app.simulated-now:}") String simulatedNow) {
        ZoneId zoneId = ZoneId.of(zone);
        return simulatedNow == null || simulatedNow.isBlank() ? Clock.system(zoneId)
                : Clock.fixed(Instant.parse(simulatedNow), zoneId);
    }

}
