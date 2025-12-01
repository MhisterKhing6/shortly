package shortly.mandmcorp.dev.shortly.service.notification;

/**
 * Interface for notification services that handle sending messages through various channels.
 * Implementations can include SMS, Email, Push notifications, etc.
 * 
 * @author Shortly Team
 * @version 1.0
 * @since 1.0
 */
public interface NotificationInterface {

    /**
     * Sends a notification message asynchronously through the implemented channel.
     * 
     * @param notify the notification request containing recipient details and message content
     * @throws RuntimeException if the notification fails to send
     */
    public void send(NotificationRequestTemplate notify);

}
