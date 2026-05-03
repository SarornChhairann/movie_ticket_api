package co.tkm.ticketmanagementsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

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
