package co.tkm.ticketmanagementsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Origins allowed to call this API from a browser; comma-separated in .env. */
    @Value("${app.cors.allowed-origins:http://localhost:8081}")
    private String[] allowedOrigins;

    @Value("${app.uploads.movies-dir:uploads/movies}")
    private String uploadsMoviesDir;

    @Value("${app.uploads.food-items-dir:uploads/food-items}")
    private String uploadsFoodItemsDir;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.port:25}")
    private int mailPort;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadsMoviesDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/movies/**")
                .addResourceLocations("file:" + uploadPath.toString() + "/");

        Path foodUploadPath = Paths.get(uploadsFoodItemsDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/food-items/**")
                .addResourceLocations("file:" + foodUploadPath.toString() + "/");
    }

    /**
     * Shared by Spring Security's CORS filter and MVC. Local Flutter web uses a
     * fresh port on each {@code flutter run -d chrome}, so every localhost and
     * 127.0.0.1 origin is allowed in addition to {@code app.cors.allowed-origins}.
     * Native Dio calls do not send an Origin and are unaffected.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(corsOriginPatterns());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(corsOriginPatterns().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    private List<String> corsOriginPatterns() {
        List<String> patterns = new ArrayList<>();
        if (allowedOrigins != null) {
            for (String origin : allowedOrigins) {
                if (origin != null && !origin.isBlank()) {
                    patterns.add(origin.trim());
                }
            }
        }
        patterns.add("http://localhost:*");
        patterns.add("http://127.0.0.1:*");
        return patterns;
    }

    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailHost);
        sender.setPort(mailPort);
        if (mailUsername != null && !mailUsername.isBlank()) {
            sender.setUsername(mailUsername);
        }
        if (mailPassword != null && !mailPassword.isBlank()) {
            sender.setPassword(mailPassword);
        }
        return sender;
    }
}
