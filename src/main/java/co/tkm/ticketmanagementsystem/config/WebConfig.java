package co.tkm.ticketmanagementsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.uploads.movies-dir:uploads/movies}")
    private String uploadsMoviesDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadsMoviesDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/movies/**")
                .addResourceLocations("file:" + uploadPath.toString() + "/");
    }
}
