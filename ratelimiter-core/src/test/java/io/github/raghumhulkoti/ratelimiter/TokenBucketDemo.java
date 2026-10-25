package io.github.raghumhulkoti.ratelimiter;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class TokenBucketDemo {

    public static void main(String[] args) throws InterruptedException {
        RateLimiter limiter = new DefaultRateLimiter(
                Rule.tokenBucket("demo", 3, 1, Duration.ofSeconds(1)),
                new InMemoryTokenBucketStore(),
                new SystemTimeSource());

        System.out.println("Rule: capacity 3, refill 1 token every second");

        System.out.println();
        System.out.println("Burst of 5 requests from user42:");
        for (int i = 1; i <= 5; i++) {
            print("user42", i, limiter.tryAcquire("user42", 1));
        }

        System.out.println();
        System.out.println("Another user has an independent bucket:");
        print("user99", 1, limiter.tryAcquire("user99", 1));

        System.out.println();
        System.out.println("Wait 1 second, user42 earns 1 token:");
        Thread.sleep(1000);
        for (int i = 6; i <= 7; i++) {
            print("user42", i, limiter.tryAcquire("user42", 1));
        }

        System.out.println();
        System.out.println("Wait 5 seconds, user42's bucket refills to 3 (not 5):");
        Thread.sleep(5000);
        for (int i = 8; i <= 11; i++) {
            print("user42", i, limiter.tryAcquire("user42", 1));
        }

        System.out.println();
        System.out.println("Asking for more than capacity can never succeed:");
        print("user42", 12, limiter.tryAcquire("user42", 4));
    }

    private static void print(String key, int request, Decision decision) {
        String retry = decision.allowed() ? ""
                : decision.retryAfterNanos() == Decision.NEVER ? ", retry never"
                : ", retry in " + TimeUnit.NANOSECONDS.toMillis(decision.retryAfterNanos()) + " ms";
        System.out.printf("  %s request %2d: %-8s remaining %d%s%n",
                key, request, decision.allowed() ? "allowed" : "REJECTED", decision.remaining(), retry);
    }
}
