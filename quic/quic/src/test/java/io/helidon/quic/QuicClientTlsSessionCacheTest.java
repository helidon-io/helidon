/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.quic;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.tls.Tls;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicClientTlsSessionCacheTest {
    @Test
    void tlsBuilderZeroCapacityRetainsMultipleTickets() {
        Tls tls = Tls.builder()
                .sessionCacheSize(0)
                .sessionTimeout(Duration.ZERO)
                .build();

        assertUnlimitedCache(tls);
    }

    @Test
    void tlsConfigZeroCapacityRetainsMultipleTickets() {
        Config config = Config.just(ConfigSources.create(Map.of("session-cache-size", "0",
                                                               "session-timeout", "PT0S")));
        Tls tls = Tls.builder().config(config).build();

        assertThat(tls.prototype().sessionCacheSize(), is(0));
        assertThat(tls.prototype().sessionTimeout(), is(Duration.ZERO));
        assertUnlimitedCache(tls);
    }

    @Test
    void positiveTlsCapacityStillBoundsCache() {
        Tls tls = Tls.builder().sessionCacheSize(2).build();
        try (QuicClientTlsSessionCache cache = QuicClientTlsSessionCache.create(tls)) {
            cacheFourTickets(cache);

            assertTwoNewestTickets(cache);
        }
    }

    @ParameterizedTest
    @CsvSource({"0, 2", "2, 4", "4, 2"})
    void positiveMaximumCapsUnlimitedAndBoundedTlsPolicies(int configuredCapacity, int maximumSize) {
        Tls tls = Tls.builder().sessionCacheSize(configuredCapacity).build();
        try (QuicClientTlsSessionCache cache = QuicClientTlsSessionCache.create(tls, maximumSize)) {
            cacheFourTickets(cache);

            assertTwoNewestTickets(cache);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void zeroMaximumDisablesCacheEvenAfterClear(int configuredCapacity) {
        Tls tls = Tls.builder().sessionCacheSize(configuredCapacity).build();
        try (QuicClientTlsSessionCache cache = QuicClientTlsSessionCache.create(tls, 0)) {
            cacheFourTickets(cache);
            assertThat(cache.size(), is(0));

            cache.delegate().clear();
            cacheFourTickets(cache);
            assertThat(cache.size(), is(0));
            for (int id = 1; id <= 4; id++) {
                assertThat(cache.delegate().cachedResumptionTicket(id + ".example.com", 443, new String[] {"h3"}),
                           is(Optional.empty()));
            }
        }
    }

    @Test
    void rejectsNegativeMaximum() {
        Tls tls = Tls.builder().sessionCacheSize(0).build();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                        () -> QuicClientTlsSessionCache.create(tls, -1));

        assertThat(failure.getMessage(), containsString("must not be negative"));
    }

    @Test
    void closingUnlimitedCacheClearsTicketsAndPreventsReuse() {
        Tls tls = Tls.builder().sessionCacheSize(0).build();
        QuicClientTlsSessionCache cache = QuicClientTlsSessionCache.create(tls);
        cacheFourTickets(cache);
        assertThat(cache.size(), is(4));

        cache.close();
        assertThat(cache.size(), is(0));
        cache.delegate().clear();
        cacheFourTickets(cache);

        assertThat(cache.size(), is(0));
        assertThat(cache.delegate().cachedResumptionTicket("1.example.com", 443, new String[] {"h3"}),
                   is(Optional.empty()));
    }

    private static void assertUnlimitedCache(Tls tls) {
        try (QuicClientTlsSessionCache cache = QuicClientTlsSessionCache.create(tls)) {
            cacheFourTickets(cache);

            assertThat(cache.size(), is(4));
            for (int id = 1; id <= 4; id++) {
                assertThat(cached(cache, id).ticket(), is(new byte[] {(byte) id}));
            }
        }
    }

    private static void assertTwoNewestTickets(QuicClientTlsSessionCache cache) {
        assertThat(cache.size(), is(2));
        for (int id = 1; id <= 2; id++) {
            assertThat(cache.delegate().cachedResumptionTicket(id + ".example.com", 443, new String[] {"h3"}),
                       is(Optional.empty()));
        }
        assertThat(cached(cache, 3).ticket(), is(new byte[] {3}));
        assertThat(cached(cache, 4).ticket(), is(new byte[] {4}));
    }

    private static void cacheFourTickets(QuicClientTlsSessionCache cache) {
        for (int id = 1; id <= 4; id++) {
            cache.delegate().cache(id + ".example.com", 443, resumptionTicket(id));
        }
    }

    private static QuicTlsResumptionTicket cached(QuicClientTlsSessionCache cache, int id) {
        return cache.delegate().cachedResumptionTicket(id + ".example.com", 443, new String[] {"h3"}).orElseThrow();
    }

    private static QuicTlsResumptionTicket resumptionTicket(int id) {
        return new QuicTlsResumptionTicket(QuicVersion.QUIC_V1,
                                           QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256,
                                           604_800,
                                           7,
                                           new byte[] {1},
                                           new byte[] {(byte) id},
                                           new byte[] {3},
                                           "h3",
                                           new byte[] {4},
                                           System.currentTimeMillis());
    }
}
