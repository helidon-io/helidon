/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.tracing.jaeger;

import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.tracing.TracerBuilder;

import io.jaegertracing.Configuration;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tracing.jaeger.JaegerTracerBuilder.DEFAULT_HTTP_HOST;
import static io.helidon.tracing.jaeger.JaegerTracerBuilder.DEFAULT_HTTP_PATH;
import static io.helidon.tracing.jaeger.JaegerTracerBuilder.DEFAULT_HTTP_PORT;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link JaegerTracerBuilder}.
 */
class JaegerTracerBuilderTest {
    private static Config config;

    @BeforeAll
    static void initClass() {
        config = Config.create().get("tracing");
    }

    @Test
    void testNoConfig() {
        assertThrows(IllegalArgumentException.class, () -> TracerBuilder.create(Config.empty()).build());
    }

    @Test
    void testBadConfig() {
        assertThrows(IllegalArgumentException.class, () -> TracerBuilder.create(config.get("jaeger-very-bad")).build());
    }

    @Test
    void testConfigDefaults() {
        TracerBuilder<?> builder = TracerBuilder.create(config.get("jaeger-defaults"));

        assertThat(builder, instanceOf(JaegerTracerBuilder.class));

        JaegerTracerBuilder jBuilder = (JaegerTracerBuilder) builder;

        assertThat(jBuilder.serviceName(), is("helidon-service"));
        assertThat("Tags", jBuilder.tags(), is(Map.of()));
        assertThat("Protocol", jBuilder.protocol(), nullValue());
        assertThat("Host", jBuilder.host(), nullValue());
        assertThat("Port", jBuilder.port(), nullValue());
        assertThat("Path", jBuilder.path(), nullValue());
        assertThat("Enabled", jBuilder.isEnabled(), is(true));
        assertThat("Username", jBuilder.username(), nullValue());
        assertThat("Password", jBuilder.password(), nullValue());
        assertThat("Token", jBuilder.token(), nullValue());
        assertThat("Propagations", jBuilder.propagations(), empty());
        assertThat("Reporter log spans", jBuilder.reporterLogSpans(), nullValue());
        assertThat("Reporter max queue size", jBuilder.reporterMaxQueueSize(), nullValue());
        assertThat("Reporter flush intervals", jBuilder.reporterFlushIntervalMillis(), nullValue());
        assertThat("Sampler type", jBuilder.samplerType(), nullValue());
        assertThat("Sampler param", jBuilder.samplerParam(), nullValue());
        assertThat("Sampler manager", jBuilder.samplerManager(), nullValue());

        // I should not build the tracer, as that may try connecting to other endpoints
        // only test the configuration I modify
        Configuration jaegerConfig = jBuilder.jaegerConfig();
        // only endpoint is configured
        Configuration.SenderConfiguration sc = jaegerConfig.getReporter().getSenderConfiguration();
        assertThat(sc.getEndpoint(), is("http://" + DEFAULT_HTTP_HOST + ":" + DEFAULT_HTTP_PORT + DEFAULT_HTTP_PATH));
    }

    @Test
    void testConfigDisabled() {
        TracerBuilder<?> builder = TracerBuilder.create(config.get("jaeger-disabled"));

        assertThat(builder, instanceOf(JaegerTracerBuilder.class));

        JaegerTracerBuilder jBuilder = (JaegerTracerBuilder) builder;

        assertThat(jBuilder.serviceName(), is("helidon-service"));

        Tracer tracer = builder.build();
        assertThat(tracer, instanceOf(NoopTracer.class));
    }

    @Test
    void testConfigUdp() {
        TracerBuilder<?> builder = TracerBuilder.create(config.get("jaeger-udp"));

        assertThat(builder, instanceOf(JaegerTracerBuilder.class));

        JaegerTracerBuilder jBuilder = (JaegerTracerBuilder) builder;
        assertThat(jBuilder.serviceName(), is("udp-service"));

        // I should not build the tracer, as that may try connecting to other endpoints
        // only test the configuration I modify
        Configuration jaegerConfig = jBuilder.jaegerConfig();
        // only endpoint is configured
        Configuration.SenderConfiguration sc = jaegerConfig.getReporter().getSenderConfiguration();
        assertThat(sc.getAgentHost(), is("192.168.1.2"));
        assertThat(sc.getAgentPort(), is(14268));
        assertThat(sc.getEndpoint(), is(nullValue()));
    }

    @Test
    void testFullHttp() {
        TracerBuilder<?> builder = TracerBuilder.create(config.get("jaeger-full-http"));

        assertThat(builder, instanceOf(JaegerTracerBuilder.class));

        JaegerTracerBuilder jBuilder = (JaegerTracerBuilder) builder;

        assertThat(jBuilder.serviceName(), is("helidon-full-http"));

        assertThat("Enabled", jBuilder.isEnabled(), is(true));
        assertThat("Protocol", jBuilder.protocol(), is("https"));
        assertThat("Host", jBuilder.host(), is("192.168.1.3"));
        assertThat("Port", jBuilder.port(), is(14240));
        assertThat("Path", jBuilder.path(), is("/api/traces/mine"));
        assertThat("Token", jBuilder.token(), is("token"));
        assertThat("Username", jBuilder.username(), is("user"));
        assertThat("Password", jBuilder.password(), is("pass"));
        assertThat("Propagations", jBuilder.propagations(), is(List.of(Configuration.Propagation.JAEGER)));
        assertThat("Reporter log spans", jBuilder.reporterLogSpans(), is(false));
        assertThat("Reporter max queue size", jBuilder.reporterMaxQueueSize(), is(42));
        assertThat("Reporter flush intervals", jBuilder.reporterFlushIntervalMillis(), is(10001L));
        assertThat("Sampler type", jBuilder.samplerType(), is(JaegerTracerBuilder.SamplerType.REMOTE));
        assertThat("Sampler param", jBuilder.samplerParam(), is(0.5));
        assertThat("Sampler manager", jBuilder.samplerManager(), is("localhost:47877"));
        assertThat("Tags", jBuilder.tags(), is(Map.of(
                "tag1", "tag1-value",
                "tag2", "tag2-value",
                "tag3", "true",
                "tag4", "false",
                "tag5", "145",
                "tag6", "741"
        )));

        // I should not build the tracer, as that may try connecting to other endpoints
        // only test the configuration I modify
        Configuration jaegerConfig = jBuilder.jaegerConfig();
        Configuration.ReporterConfiguration reporter = jaegerConfig.getReporter();
        assertThat(reporter.getFlushIntervalMs(), is(10001));
        assertThat(reporter.getLogSpans(), is(false));
        assertThat(reporter.getMaxQueueSize(), is(42));

        Configuration.SenderConfiguration sc = reporter.getSenderConfiguration();
        // we should have endpoint, as we use https
        assertThat(sc.getEndpoint(), is("https://192.168.1.3:14240/api/traces/mine"));
        // if both are specified, token has precedence
        assertThat(sc.getAuthToken(), is("token"));
        assertThat(sc.getAuthUsername(), nullValue());
        assertThat(sc.getAuthPassword(), nullValue());


        Configuration.SamplerConfiguration sampler = jaegerConfig.getSampler();
        assertThat(sampler.getManagerHostPort(), is("localhost:47877"));
        assertThat(sampler.getParam(), is(0.5));
        assertThat(sampler.getType(), is("remote"));

        assertThat(jaegerConfig.getTracerTags(), is(Map.of(
                "tag1", "tag1-value",
                "tag2", "tag2-value",
                "tag3", "true",
                "tag4", "false",
                "tag5", "145",
                "tag6", "741"
        )));
    }

    @Test
    void testJaegerPropagations() {
        TracerBuilder<?> builder = TracerBuilder.create(config.get("jaeger-propagations"));

        assertThat(builder, instanceOf(JaegerTracerBuilder.class));

        JaegerTracerBuilder jBuilder = (JaegerTracerBuilder) builder;

        assertThat(jBuilder.serviceName(), is("helidon-propagations"));
        assertThat("Propagations",
                   jBuilder.propagations(),
                   is(List.of(Configuration.Propagation.JAEGER, Configuration.Propagation.B3)));
    }
}