package shortly.mandmcorp.dev.shortly.utils;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class WebRequestUtil {
    
    private final WebClient webClient;

    public boolean postRequst(String url, Map<String,Object> body) {
        try {
            webClient.post()
                    .uri( url )
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();      
            return true;
        } catch (Exception e) {
            return false;
        }   
    }
    
}
