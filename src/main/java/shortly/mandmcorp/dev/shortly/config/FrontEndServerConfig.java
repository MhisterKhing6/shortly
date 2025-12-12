package shortly.mandmcorp.dev.shortly.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "backend-server")
public class FrontEndServerConfig {
    private String baseUrl;
}
