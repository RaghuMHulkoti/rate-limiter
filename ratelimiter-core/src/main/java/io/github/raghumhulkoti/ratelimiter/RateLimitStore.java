package io.github.raghumhulkoti.ratelimiter;

import java.util.Set;
import java.util.concurrent.CompletionStage;

public interface RateLimitStore {

    CompletionStage<Decision> evaluate(String key, Rule rule, long permits, long currentTimeNanos);

    Set<Algorithm> supportedAlgorithms();
}
