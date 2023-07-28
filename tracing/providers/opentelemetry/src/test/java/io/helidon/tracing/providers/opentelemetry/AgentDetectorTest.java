/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.tracing.providers.opentelemetry;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


/**
 * Check Agent Detector working correctly.
 */
class AgentDetectorTest {

    public static final String OTEL_AGENT_PRESENT = "otel.agent.present";
    public static final String IO_OPENTELEMETRY_JAVAAGENT = "io.opentelemetry.javaagent";

    @Test
    void shouldBeNoOpTelemetry(){
        Config config = Config.create(ConfigSources.create(Map.of(OTEL_AGENT_PRESENT, "true")));
        boolean present = HelidonOpenTelemetry.AgentDetector.isAgentPresent(config);
        assertThat(present, is(true));
    }

    @Test
    void shouldNotBeNoOpTelemetry(){
        Config config = Config.create(ConfigSources.create(Map.of(OTEL_AGENT_PRESENT, "false")));
        boolean present = HelidonOpenTelemetry.AgentDetector.isAgentPresent(config);
        assertThat(present, is(false));
    }

    @Test
    void checkEnvVariable(){
        System.setProperty(IO_OPENTELEMETRY_JAVAAGENT, "true");
        Config config = Config.create();
        boolean present = HelidonOpenTelemetry.AgentDetector.isAgentPresent(config);
        assertThat(present, is(true));
    }
}
