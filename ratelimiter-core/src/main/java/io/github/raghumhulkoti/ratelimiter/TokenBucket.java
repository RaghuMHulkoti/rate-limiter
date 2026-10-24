package io.github.raghumhulkoti.ratelimiter;

final class TokenBucket {

    private TokenBucket() {
    }

    static Result compute(BucketState state, Rule rule, long permits, long currentTimeNanos) {
        double capacity = rule.capacity();
        double tokens;
        if (state == null) {
            state = new BucketState(capacity, currentTimeNanos);
            tokens = capacity;
        } else {
            long elapsed = Math.max(0, currentTimeNanos - state.lastUpdatedNanos());
            tokens = Math.min(capacity, state.tokens() + tokensEarnedIn(elapsed, rule));
        }

        if (tokens >= permits) {
            double left = tokens - permits;
            Decision decision = new Decision(true, (long) left, rule.capacity(),
                    0, timeToEarn(capacity - left, rule));
            return new Result(new BucketState(left, currentTimeNanos), decision);
        }

        long retryAfter = permits > rule.capacity() ? Decision.NEVER : timeToEarn(permits - tokens, rule);
        Decision decision = new Decision(false, (long) tokens, rule.capacity(),
                retryAfter, timeToEarn(capacity - tokens, rule));
        return new Result(state, decision);
    }

    private static double tokensEarnedIn(long elapsedNanos, Rule rule) {
        return (double) elapsedNanos * rule.tokensPerRefill() / rule.refillIntervalNanos();
    }

    private static long timeToEarn(double tokens, Rule rule) {
        if (tokens <= 0) {
            return 0;
        }
        return (long) Math.ceil(tokens * rule.refillIntervalNanos() / rule.tokensPerRefill());
    }

    static final class Result {

        private final BucketState newState;
        private final Decision decision;

        Result(BucketState newState, Decision decision) {
            this.newState = newState;
            this.decision = decision;
        }

        BucketState newState() {
            return newState;
        }

        Decision decision() {
            return decision;
        }
    }
}
