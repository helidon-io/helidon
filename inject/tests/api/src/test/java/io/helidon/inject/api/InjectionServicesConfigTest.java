/*
 * Copyright (c) 2022-2023 Oracle and/or its affiliates.
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

package io.helidon.inject.api;

import java.time.Duration;
import java.util.Map;

import io.helidon.common.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class InjectionServicesConfigTest {

    @AfterEach
    void reset() {
        InjectionServicesHolder.reset();
    }

    /**
     * This tests the default injection configuration.
     */
    @Test
    void withOutBootstrap() {
        InjectionServicesConfig cfg = InjectionServicesConfig.create();
        assertThat(cfg.serviceLookupCaching(), equalTo(Boolean.FALSE));
        assertThat(cfg.activationLogs(), equalTo(Boolean.FALSE));
        assertThat(cfg.activationDeadlockDetectionTimeout(), equalTo(Duration.ofSeconds(10)));
        assertThat(cfg.permitsDynamic(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsDynamic(), equalTo(Boolean.TRUE));
        assertThat(cfg.permitsReflection(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsReflection(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsJsr330(), equalTo(Boolean.TRUE));
        assertThat(cfg.supportsJsr330Statics(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsJsr330Privates(), equalTo(Boolean.FALSE));
        assertThat(cfg.usesJsr330(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsCompileTime(), equalTo(Boolean.TRUE));
        assertThat(cfg.usesCompileTimeApplications(), equalTo(Boolean.TRUE));
        assertThat(cfg.usesCompileTimeModules(), equalTo(Boolean.TRUE));
        assertThat(cfg.supportsContextualLookup(), equalTo(Boolean.FALSE));
        assertThat(cfg.providerName(), optionalEmpty());
        assertThat(cfg.providerVersion(), optionalEmpty());
    }

    @Test
    void withBootstrapWithoutConfig() {
        Bootstrap bootstrap = Bootstrap.builder().build();
        InjectionServicesHolder.bootstrap(bootstrap);
        assertThat(InjectionServicesHolder.bootstrap(false), optionalPresent());

        // should be the same as if we had no bootstrap
        withOutBootstrap();
    }

    @Test
    void withBootStrapConfig() {
        Config config = io.helidon.config.Config.builder(
                        ConfigSources.create(
                                Map.of("inject.provider-name", "fake",
                                       "inject.provider-version", "2.4",
                                       "inject.service-lookup-caching", "true",
                                       "inject.activation-logs", "true",
                                       "inject.activation-deadlock-detection-timeout", "PT0.111S",
                                       "inject.permits-dynamic", "true",
                                       "inject.permits-reflection", "true",
                                       "inject.uses-jsr330", "true",
                                       "inject.uses-compile-time-applications", "false",
                                       "inject.uses-compile-time-modules", "false"
                                ), "config-1"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        Bootstrap bootstrap = Bootstrap.builder()
                .config(config)
                .build();
        InjectionServicesHolder.bootstrap(bootstrap);
        assertThat(InjectionServicesHolder.bootstrap(false), optionalPresent());

        InjectionServicesConfig cfg = InjectionServicesConfig.create(config.get("inject"));
        assertThat(cfg.serviceLookupCaching(), equalTo(Boolean.TRUE));
        assertThat(cfg.activationLogs(), equalTo(Boolean.TRUE));
        assertThat(cfg.activationDeadlockDetectionTimeout(), equalTo(Duration.ofMillis(111)));
        assertThat(cfg.permitsDynamic(), equalTo(Boolean.TRUE));
        assertThat(cfg.supportsDynamic(), equalTo(Boolean.TRUE));
        assertThat(cfg.permitsReflection(), equalTo(Boolean.TRUE));
        assertThat(cfg.supportsReflection(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsJsr330(), equalTo(Boolean.TRUE));
        assertThat(cfg.supportsJsr330Statics(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsJsr330Privates(), equalTo(Boolean.FALSE));
        assertThat(cfg.usesJsr330(), equalTo(Boolean.TRUE));
        assertThat(cfg.supportsCompileTime(), equalTo(Boolean.TRUE));
        assertThat(cfg.usesCompileTimeApplications(), equalTo(Boolean.FALSE));
        assertThat(cfg.usesCompileTimeModules(), equalTo(Boolean.FALSE));
        assertThat(cfg.supportsContextualLookup(), equalTo(Boolean.FALSE));
        assertThat(cfg.providerName(), optionalValue(is("fake")));
        assertThat(cfg.providerVersion(), optionalValue(is("2.4")));
    }

}
