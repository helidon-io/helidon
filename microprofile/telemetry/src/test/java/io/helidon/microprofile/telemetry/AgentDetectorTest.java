/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.telemetry;

import io.helidon.config.Config;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.tracing.providers.opentelemetry.HelidonOpenTelemetry;

import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Check Agent Detector working correctly.
 */
@HelidonTest(resetPerTest = true)
@AddExtension(ServerCdiExtension.class)
class AgentDetectorTest {

    public static final String IO_OPENTELEMETRY_JAVAAGENT = "io.opentelemetry.javaagent";

    @Test
    @AddConfig(key = TelemetryCdiExtension.OTEL_AGENT_PRESENT, value = "true")
    void shouldBeNoOpTelemetry(){
        Config config = CDI.current().select(Config.class).get();
        boolean present = HelidonOpenTelemetry.AgentDetector.isAgentPresent(config);
        assertThat(present, is(true));
    }

    @Test
    @AddConfig(key = TelemetryCdiExtension.OTEL_AGENT_PRESENT, value = "false")
    void shouldNotBeNoOpTelemetry(){
        Config config = CDI.current().select(Config.class).get();
        boolean present = HelidonOpenTelemetry.AgentDetector.isAgentPresent(config);
        assertThat(present, is(false));
    }

    @Test
    void checkEnvVariable(){
        System.setProperty(IO_OPENTELEMETRY_JAVAAGENT, "true");
        Config config = CDI.current().select(Config.class).get();
        boolean present = HelidonOpenTelemetry.AgentDetector.isAgentPresent(config);
        assertThat(present, is(true));
    }
}
