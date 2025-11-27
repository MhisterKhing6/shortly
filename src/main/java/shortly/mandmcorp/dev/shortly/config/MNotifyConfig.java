package shortly.mandmcorp.dev.shortly.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "mnotify")
@Data
public class MNotifyConfig {
    private String accessKey;
    private String sender;
    private String baseUrl;
}
 