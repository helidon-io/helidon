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

package io.helidon.webclient.grpc.tests;

import java.time.Duration;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.grpc.GrpcClientProtocolConfig;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests gRPC client config protocol settings.
 */
class GrpcConfigProtocolTest {

    @Test
    void testDefaults() {
        GrpcClientProtocolConfig config = GrpcClientProtocolConfig.create();
        assertThat(config.pollWaitTime(), is(Duration.ofSeconds(10)));
        assertThat(config.abortPollTimeExpired(), is(false));
        assertThat(config.initBufferSize(), is(2048));
        assertThat(config.heartbeatPeriod(), is(Duration.ofSeconds(0)));
    }

    @Test
    void testApplicationConfig() {
        GrpcClientProtocolConfig config = GrpcClientProtocolConfig.create(
                Config.create(ConfigSources.classpath("application.yaml")).get("grpc-client"));
        assertThat(config.pollWaitTime(), is(Duration.ofSeconds(30)));
        assertThat(config.abortPollTimeExpired(), is(true));
        assertThat(config.initBufferSize(), is(10000));
        assertThat(config.heartbeatPeriod(), is(Duration.ofSeconds(10)));
    }
}
