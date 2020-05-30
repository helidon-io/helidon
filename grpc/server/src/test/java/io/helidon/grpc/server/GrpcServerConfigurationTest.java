/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.grpc.server;

import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link GrpcServerConfiguration} unit tests.
 */
public class GrpcServerConfigurationTest {

    @Test
    public void shouldHaveDefault() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .build();

        assertThat(configuration.name(), is(GrpcServerConfiguration.DEFAULT_NAME));
    }

    @Test
    public void shouldSetName() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .name(" foo ")
                .build();

        assertThat(configuration.name(), is("foo"));
    }

    @Test
    public void shouldNotSetNullName() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .name(null)
                .build();

        assertThat(configuration.name(), is(GrpcServerConfiguration.DEFAULT_NAME));
    }

    @Test
    public void shouldNotSetBlankName() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .name(" \t ")
                .build();

        assertThat(configuration.name(), is(GrpcServerConfiguration.DEFAULT_NAME));
    }

    @Test
    public void shouldHaveDefaultPort() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .build();

        assertThat(configuration.port(), is(GrpcServerConfiguration.DEFAULT_PORT));
    }

    @Test
    public void shouldSetPort() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .port(19)
                .build();

        assertThat(configuration.port(), is(19));
    }

    @Test
    public void shouldSetNegativePortToZero() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .port(-1)
                .build();

        assertThat(configuration.port(), is(0));
    }

    @Test
    public void shouldHaveDefaultTracer() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .build();

        assertThat(configuration.tracer(), is(sameInstance(GlobalTracer.get())));
    }

    @Test
    public void shouldSetTracer() {
        Tracer tracer = mock(Tracer.class);
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .tracer(tracer)
                .build();

        assertThat(configuration.tracer(), is(sameInstance(tracer)));
    }

    @Test
    public void shouldNotSetNullTracer() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .tracer((Tracer) null)
                .build();

        assertThat(configuration.tracer(), is(sameInstance(GlobalTracer.get())));
    }

    @Test
    public void shouldSetTracerSupplier() {
        Tracer tracer = mock(Tracer.class);
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .tracer(() -> tracer)
                .build();

        assertThat(configuration.tracer(), is(sameInstance(tracer)));
    }

    @Test
    public void shouldNotSetNullTracerSupplier() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .tracer((Supplier<Tracer>) null)
                .build();

        assertThat(configuration.tracer(), is(sameInstance(GlobalTracer.get())));
    }

    @Test
    public void shouldNotSetNullTracerFromSupplier() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .tracer(() -> null)
                .build();

        assertThat(configuration.tracer(), is(sameInstance(GlobalTracer.get())));
    }

    @Test
    public void shouldHaveDefaultTracingConfig() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .build();

        assertThat(configuration.tracingConfig(), is(notNullValue()));
    }

    @Test
    public void shouldSetTracingConfiguration() {
        GrpcTracingConfig tracingConfig = mock(GrpcTracingConfig.class);
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .tracingConfig(tracingConfig)
                .build();

        assertThat(configuration.tracingConfig(), is(sameInstance(tracingConfig)));
    }

    @Test
    public void shouldNotSetNullTracingConfiguration() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .tracingConfig(null)
                .build();

        assertThat(configuration.tracingConfig(), is(notNullValue()));
    }

    @Test
    public void shouldHaveDefaultWorkerCount() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .build();

        assertThat(configuration.workers(), is(GrpcServerConfiguration.DEFAULT_WORKER_COUNT));
    }

    @Test
    public void shouldSetWorkerCount() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .workersCount(100)
                .build();

        assertThat(configuration.workers(), is(100));
    }

    @Test
    public void shouldNotSetNegativeWorkerCount() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .workersCount(-1)
                .build();

        assertThat(configuration.workers(), is(GrpcServerConfiguration.DEFAULT_WORKER_COUNT));
    }

    @Test
    public void shouldNotSetZeroWorkerCount() {
        GrpcServerConfiguration configuration = GrpcServerConfiguration.builder()
                .workersCount(0)
                .build();

        assertThat(configuration.workers(), is(GrpcServerConfiguration.DEFAULT_WORKER_COUNT));
    }

    @Test
    public void shouldBuildFromConfig() {
        Config config = Config.builder().sources(ConfigSources.classpath("config1.conf")).build();
        GrpcServerConfiguration serverConfig = config.get("grpcserver").as(GrpcServerConfiguration::create).get();

        assertThat(serverConfig.name(), is("foo"));
        assertThat(serverConfig.port(), is(19));
        assertThat(serverConfig.useNativeTransport(), is(true));
        assertThat(serverConfig.workers(), is(51));
    }
}
