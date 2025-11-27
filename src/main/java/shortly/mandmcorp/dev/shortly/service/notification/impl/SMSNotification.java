package shortly.mandmcorp.dev.shortly.service.notification.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.extern.slf4j.Slf4j;

import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

import shortly.mandmcorp.dev.shortly.config.MNotifyConfig;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationInterface;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationRequestTemplate;
import shortly.mandmcorp.dev.shortly.utils.WebRequestUtil;

@Service
@Qualifier("smsNotification")
@AllArgsConstructor
@Slf4j
public class SMSNotification implements NotificationInterface {

    private final MNotifyConfig mNotifyConfig;
    private final WebRequestUtil webRequestUtil;
    
   
    @Async("taskExecutor")
    public void send(NotificationRequestTemplate notify) {
        log.info("Attempting to send SMS to: {}", notify.getTo());
        try {
            boolean result = sendMessage(notify);
            log.info("SMS send result: {}", result);
        } catch (Exception e) {
            log.error("Error sending SMS: ", e);
        }
    }

    private boolean sendMessage(NotificationRequestTemplate notify) {
        try {
            Map<String, Object> requestBody = Map.of(
                "recipient", List.of(notify.getTo()),
                "sender", mNotifyConfig.getSender(),
                "message", notify.getBody(),
                "is_schedule", false,
                "schedule_date", ""
            );
           String uri = mNotifyConfig.getBaseUrl() + "/quick?key=" + mNotifyConfig.getAccessKey();
           log.info("Sending SMS request to: {}", uri);
           log.info("Request body: {}", requestBody);
           boolean result = webRequestUtil.postRequst(uri, requestBody);
           log.info("WebRequestUtil response: {}", result);
           return result;
        } catch (Exception e) {
            log.error("Exception in sendMessage: ", e);
            return false;
        }
    }

    
     

}
