package shortly.mandmcorp.dev.shortly.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "jimi")
@Data
public class JimiConfig {
    private String appKey;
    private String appSecret;
    private String baseUrl;
    private String userId;
    private String userPasswordMd5;
}
