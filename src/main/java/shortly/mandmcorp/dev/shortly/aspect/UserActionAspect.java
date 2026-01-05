package shortly.mandmcorp.dev.shortly.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.annotation.TrackUserAction;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.model.UserAction;
import shortly.mandmcorp.dev.shortly.repository.UserActionRepository;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class UserActionAspect {

    private final UserActionRepository userActionRepository;

    @AfterReturning("@annotation(shortly.mandmcorp.dev.shortly.annotation.TrackUserAction)")
    public void trackUserAction(JoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            TrackUserAction annotation = signature.getMethod().getAnnotation(TrackUserAction.class);

            if (annotation != null) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                String userId = null;

                if (auth != null && auth.getPrincipal() instanceof User) {
                    User user = (User) auth.getPrincipal();
                    userId = user.getUserId();
                }

                UserAction userAction = UserAction.builder()
                        .userId(userId)
                        .action(annotation.action())
                        .description(annotation.description())
                        .build();

                userActionRepository.save(userAction);

                log.debug("User action tracked: userId={}, action={}, description={}",
                         userId, annotation.action(), annotation.description());
            }
        } catch (Exception e) {
            log.error("Error tracking user action", e);
        }
    }
}
