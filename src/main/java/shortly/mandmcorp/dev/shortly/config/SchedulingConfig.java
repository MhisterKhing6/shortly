package shortly.mandmcorp.dev.shortly.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled jobs (the nightly driver-payments integrity check). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
