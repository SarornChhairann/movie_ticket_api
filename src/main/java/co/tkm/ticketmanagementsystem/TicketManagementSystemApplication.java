package co.tkm.ticketmanagementsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan // binds KeycloakProperties
public class TicketManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketManagementSystemApplication.class, args);
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(value = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfiguration {
    }
}
