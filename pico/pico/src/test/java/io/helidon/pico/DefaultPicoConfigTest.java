/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico;

import java.util.Map;

import io.helidon.common.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isEmptyOrNullString;

class DefaultPicoConfigTest {

    @AfterEach
    void reset() {
        PicoServicesHolder.reset();
    }

    @Test
    void withOutBootstrap() {
        DefaultPicoServicesConfig cfg = DefaultPicoServicesConfig.builder().build();
        assertThat(cfg.serviceLookupCaching(), equalTo(Boolean.FALSE));
        assertThat(cfg.activationLogs(), equalTo(Boolean.FALSE));
        assertThat(cfg.activationDeadlockDetectionTimeoutMillis(), equalTo(10000L));
        assertThat(cfg.permitsDynamic(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsDynamic(), equalTo(Boolean.TRUE));
        assertThat(cfg.permitsReflection(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsReflection(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsJsr330(), equalTo(Boolean.TRUE));
        assertThat(cfg.supportsJsr330Statics(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsJsr330Privates(), equalTo(Boolean.FALSE));
        assertThat(cfg.usesJsr330(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsCompileTime(), equalTo(Boolean.TRUE));
        assertThat(cfg.usesCompileTime(), equalTo(Boolean.TRUE));
        assertThat(cfg.supportsContextualLookup(), equalTo(Boolean.FALSE));
        assertThat(cfg.providerName(), isEmptyOrNullString());
        assertThat(cfg.providerVersion(), isEmptyOrNullString());
    }

    @Test
    void withBootstrapWithoutConfig() {
        DefaultBootstrap bootstrap = DefaultBootstrap.builder().build();
        PicoServicesHolder.bootstrap(bootstrap);
        assertThat(PicoServicesHolder.bootstrap(false), optionalPresent());

        // should be the same as if we had no bootstrap
        withOutBootstrap();
    }

    @Test
    void withBootStrapConfig() {
        Config config = io.helidon.config.Config.create(
                ConfigSources.create(
                        Map.of(PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_PROVIDER_NAME, "fake",
                               PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_PROVIDER_VERSION, "fake",
                               PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_SERVICE_LOOKUP_CACHING, "true",
                               PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_ACTIVATION_LOGS, "true",
                               PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_ACTIVATION_DEADLOCK_TIMEOUT_IN_MILLIS, "111",
                               PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_PERMITS_DYNAMIC, "true",
                               PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_PERMITS_REFLECTION, "true",
                               PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_USES_JSR330, "true",
                               PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_USES_COMPILE_TIME, "false"
                        ), "config-1"));
        DefaultBootstrap bootstrap = DefaultBootstrap.builder()
                .config(config)
                .build();
        PicoServicesHolder.bootstrap(bootstrap);
        assertThat(PicoServicesHolder.bootstrap(false), optionalPresent());

        DefaultPicoServicesConfig cfg = DefaultPicoServicesConfig.builder().build();
        assertThat(cfg.serviceLookupCaching(), equalTo(Boolean.TRUE));
        assertThat(cfg.activationLogs(), equalTo(Boolean.TRUE));
        assertThat(cfg.activationDeadlockDetectionTimeoutMillis(), equalTo(111L));
        assertThat(cfg.permitsDynamic(), equalTo(Boolean.TRUE));
        assertThat(cfg.supportsDynamic(), equalTo(Boolean.TRUE));
        assertThat(cfg.permitsReflection(), equalTo(Boolean.TRUE));
        assertThat(cfg.supportsReflection(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsJsr330(), equalTo(Boolean.TRUE));
        assertThat(cfg.supportsJsr330Statics(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsJsr330Privates(), equalTo(Boolean.FALSE));
        assertThat(cfg.usesJsr330(), equalTo(Boolean.TRUE));
        assertThat(cfg.supportsCompileTime(), equalTo(Boolean.TRUE));
        assertThat(cfg.usesCompileTime(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsContextualLookup(), equalTo(Boolean.FALSE));
        assertThat(cfg.providerName(), isEmptyOrNullString());
        assertThat(cfg.providerVersion(), isEmptyOrNullString());
    }

}
