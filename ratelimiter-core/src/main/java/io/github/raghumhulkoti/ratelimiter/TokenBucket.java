package io.github.raghumhulkoti.ratelimiter;

final class TokenBucket {

    private TokenBucket() {
    }

    static Result compute(BucketState state, Rule rule, long permits, long nowNanos) {
        double capacity = rule.capacity();
        double tokens;
        if (state == null) {
            state = new BucketState(capacity, nowNanos);
            tokens = capacity;
        } else {
            long elapsed = Math.max(0, nowNanos - state.lastRefillNanos());
            tokens = Math.min(capacity, state.tokens() + tokensFor(elapsed, rule));
        }

        if (tokens >= permits) {
            double left = tokens - permits;
            Decision decision = new Decision(true, (long) left, rule.capacity(),
                    0, nanosFor(capacity - left, rule));
            return new Result(new BucketState(left, nowNanos), decision);
        }

        long retryAfter = permits > rule.capacity() ? Decision.NEVER : nanosFor(permits - tokens, rule);
        Decision decision = new Decision(false, (long) tokens, rule.capacity(),
                retryAfter, nanosFor(capacity - tokens, rule));
        return new Result(state, decision);
    }

    private static double tokensFor(long elapsedNanos, Rule rule) {
        return (double) elapsedNanos * rule.refillTokens() / rule.refillPeriodNanos();
    }

    private static long nanosFor(double tokens, Rule rule) {
        if (tokens <= 0) {
            return 0;
        }
        return (long) Math.ceil(tokens * rule.refillPeriodNanos() / rule.refillTokens());
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
