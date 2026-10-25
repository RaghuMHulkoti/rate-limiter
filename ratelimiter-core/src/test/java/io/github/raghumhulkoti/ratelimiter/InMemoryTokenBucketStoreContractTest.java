package io.github.raghumhulkoti.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InMemoryTokenBucketStoreContractTest extends RateLimitStoreContractTest {

    @Override
    RateLimitStore createStore() {
        return new InMemoryTokenBucketStore();
    }

    @Test
    void supportsTokenBucketOnly() {
        assertThat(createStore().supportedAlgorithms()).containsExactly(Algorithm.TOKEN_BUCKET);
    }
}
