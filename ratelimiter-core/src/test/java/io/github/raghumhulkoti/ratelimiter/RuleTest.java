package io.github.raghumhulkoti.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RuleTest {

    @Test
    void storesValuesInNanos() {
        Rule rule = Rule.tokenBucket("test", 100, 10, Duration.ofSeconds(1));

        assertThat(rule.algorithm()).isEqualTo(Algorithm.TOKEN_BUCKET);
        assertThat(rule.capacity()).isEqualTo(100);
        assertThat(rule.tokensPerRefill()).isEqualTo(10);
        assertThat(rule.refillIntervalNanos()).isEqualTo(1_000_000_000L);
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> Rule.tokenBucket("test", 0, 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
        assertThatThrownBy(() -> Rule.tokenBucket("test", -1, 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveRefillTokens() {
        assertThatThrownBy(() -> Rule.tokenBucket("test", 10, 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokensPerRefill");
    }

    @Test
    void rejectsNonPositiveRefillPeriod() {
        assertThatThrownBy(() -> Rule.tokenBucket("test", 10, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refillInterval");
        assertThatThrownBy(() -> Rule.tokenBucket("test", 10, 1, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullRefillPeriod() {
        assertThatThrownBy(() -> Rule.tokenBucket("test", 10, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalByValue() {
        assertThat(Rule.tokenBucket("test", 4, 1, Duration.ofSeconds(1)))
                .isEqualTo(Rule.tokenBucket("test", 4, 1, Duration.ofMillis(1000)))
                .hasSameHashCodeAs(Rule.tokenBucket("test", 4, 1, Duration.ofMillis(1000)))
                .isNotEqualTo(Rule.tokenBucket("test", 5, 1, Duration.ofSeconds(1)));
    }

    @Test
    void rejectsInvalidName() {
        assertThatThrownBy(() -> Rule.tokenBucket(null, 10, 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> Rule.tokenBucket("  ", 10, 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> Rule.tokenBucket("a:b", 10, 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("':'");
    }

    @Test
    void differentNamesAreNotEqual() {
        assertThat(Rule.tokenBucket("login", 4, 1, Duration.ofSeconds(1)))
                .isNotEqualTo(Rule.tokenBucket("search", 4, 1, Duration.ofSeconds(1)));
    }
}
