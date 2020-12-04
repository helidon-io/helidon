/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.metrics.micrometer;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;


import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


public class MicrometerSupportBuilderTest {

    @Test
    public void testValidBuiltInRegistries() {
        MicrometerSupport support = MicrometerSupport.builder()
                .enrollBuiltInRegistry(MicrometerSupport.Builder.BuiltInRegistry.PROMETHEUS)
                .build();

        assertThat("Did not find expected instance of PrometheusMeterRegistry",
                support.enrolledRegistries()
                .values()
                .stream()
                .filter(PrometheusMeterRegistry.class::isInstance)
                .findFirst()
                .isPresent(), is(true));
    }

    @Test
    public void testValidExplicitlyAddedPrometheusRegistry() {
        MicrometerSupport support = MicrometerSupport.builder()
                .enrollRegistry(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), req -> Optional.empty())
                .build();

        assertThat("Did not find expected instance of PrometheusMeterRegistry",
                support.enrolledRegistries()
                .values()
                .stream()
                .filter(PrometheusMeterRegistry.class::isInstance)
                .findFirst()
                .isPresent());
    }

    @Test
    public void testBuiltInWithExplicitlyAddedPrometheusRegistries() {
        MicrometerSupport support = MicrometerSupport.builder()
                .enrollRegistry(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), req -> Optional.empty())
                .enrollBuiltInRegistry(MicrometerSupport.Builder.BuiltInRegistry.PROMETHEUS)
                .build();

        assertThat("Unexpected count of PrometheusMeterRegistries",
                support.enrolledRegistries()
                    .values()
                    .stream()
                    .filter(PrometheusMeterRegistry.class::isInstance)
                    .count(),
                is(2L));
    }

    @Test
    public void testBuiltInWithNames() {
        Properties props = new Properties();
        props.setProperty("metrics.micrometer." + MicrometerSupport.BUILTIN_REGISTRIES_CONFIG_KEY, "prometheus");
        Config config = Config.create(ConfigSources.create(props));
        MicrometerSupport.Builder builder = MicrometerSupport.builder()
                .config(config.get("metrics.micrometer"));

        assertThat(builder.logRecords(), is(empty()));
        MicrometerSupport support = builder.build();

        assertThat("Did not find expected instance of PrometheusMeterRegistry",
                support.enrolledRegistries()
                        .values()
                        .stream()
                        .filter(PrometheusMeterRegistry.class::isInstance)
                        .findFirst()
                        .isPresent());

        builder = MicrometerSupport.builder();
        props.setProperty("metrics.micrometer." + MicrometerSupport.BUILTIN_REGISTRIES_CONFIG_KEY, "badReg");
        config = Config.create(ConfigSources.create(props));
        builder.config(config.get("metrics.micrometer"));

        assertThat(builder.logRecords(), is(not(empty())));
        assertThat(builder.logRecords().get(0).getLevel(), is(Level.WARNING));
        assertThat(builder.logRecords().get(0).getMessage(), containsString("badReg"));
    }
}
