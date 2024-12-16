/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.common.concurrency.limits;

import java.time.Duration;
import java.util.Optional;

import io.helidon.config.Config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class ConfiguredLimitTest {
    private static Config config;

    @BeforeAll
    public static void init() {
        config = Config.create();
    }

    @Test
    public void testFixed() {
        LimitUsingConfig limitConfig = LimitUsingConfig.create(config.get("first"));
        Optional<Limit> configuredLimit = limitConfig.concurrencyLimit();
        assertThat(configuredLimit, not(Optional.empty()));
        Limit limit = configuredLimit.get();

        assertThat(limit.name(), is("server-listener"));
        assertThat(limit.type(), is("fixed"));

        FixedLimitConfig prototype = ((FixedLimit) limit).prototype();
        assertThat("Permits", prototype.permits(), is(1));
        assertThat("Queue length", prototype.queueLength(), is(20));
        assertThat("Should be fair", prototype.fair(), is(true));
        assertThat("Queue timeout", prototype.queueTimeout(), is(Duration.ofSeconds(42)));
    }

    @Test
    public void testAimd() {
        LimitUsingConfig limitConfig = LimitUsingConfig.create(config.get("second"));
        Optional<Limit> configuredLimit = limitConfig.concurrencyLimit();
        assertThat(configuredLimit, not(Optional.empty()));
        Limit limit = configuredLimit.get();

        assertThat(limit.name(), is("aimd"));
        assertThat(limit.type(), is("aimd"));

        AimdLimitConfig prototype = ((AimdLimit) limit).prototype();
        assertThat("Timeout", prototype.timeout(), is(Duration.ofSeconds(42)));
        assertThat("Min limit", prototype.minLimit(), is(11));
        assertThat("Max limit", prototype.maxLimit(), is(22));
        assertThat("Initial limit", prototype.initialLimit(), is(14));
        assertThat("Backoff ratio", prototype.backoffRatio(), is(0.74));
    }
}
