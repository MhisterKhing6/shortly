package shortly.mandmcorp.dev.shortly.service.common;

import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import shortly.mandmcorp.dev.shortly.model.IdempotencyRecord;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.utils.CurrentUser;

/**
 * Idempotency-Key support: the first request with a key runs and stores the ids of what it
 * created; a repeat within 24 hours gets the same result rebuilt from those ids, without running
 * again. A repeat while the first is still running gets 409.
 */
@Service
public class IdempotencyService {

    private static final long TTL_MS = 24 * 3_600_000L;

    private final MongoTemplate mongoTemplate;

    public IdempotencyService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public <T> T run(String scope, String key, Supplier<T> action,
                     Function<T, List<String>> toIds, Function<IdempotencyRecord, T> replay) {
        if (key == null || key.isBlank()) return action.get();
        if (key.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is too long");
        User user = CurrentUser.getOrNull();
        String id = scope + ":" + (user != null ? user.getCompanyId() + ":" + user.getUserId() : "public") + ":" + key.trim();

        try {
            mongoTemplate.insert(IdempotencyRecord.builder().id(id).status("PENDING").createdAt(new Date()).build());
        } catch (DuplicateKeyException e) {
            IdempotencyRecord existing = mongoTemplate.findById(id, IdempotencyRecord.class);
            if (existing != null && existing.getCreatedAt() != null
                    && System.currentTimeMillis() - existing.getCreatedAt().getTime() > TTL_MS) {
                mongoTemplate.remove(existing);
                return run(scope, key, action, toIds, replay);
            }
            if (existing == null || !"DONE".equals(existing.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is still being processed; try again in a moment");
            }
            return replay.apply(existing);
        }

        try {
            T result = action.get();
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(id)),
                    new Update().set("status", "DONE").set("resultIds", toIds.apply(result)), IdempotencyRecord.class);
            return result;
        } catch (RuntimeException e) {
            // Failed requests can be retried with the same key.
            mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), IdempotencyRecord.class);
            throw e;
        }
    }
}
