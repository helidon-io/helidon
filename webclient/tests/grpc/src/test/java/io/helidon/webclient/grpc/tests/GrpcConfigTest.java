/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import io.helidon.webclient.grpc.GrpcClientConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests gRPC client config settings.
 */
class GrpcConfigTest {

    @Test
    void testDefaults() {
        GrpcClientConfig config = GrpcClientConfig.create();
        assertThat(config.protocolConfig().pollWaitTime(), is(Duration.ofSeconds(10)));
        assertThat(config.protocolConfig().abortPollTimeExpired(), is(false));
        assertThat(config.protocolConfig().initBufferSize(), is(2048));
        assertThat(config.protocolConfig().heartbeatPeriod(), is(Duration.ofSeconds(0)));
    }

    @Test
    void testApplicationConfig() {
        GrpcClientConfig config = GrpcClientConfig.create(
                Config.create(ConfigSources.classpath("application.yaml"))
                        .get("grpc-client"));
        assertThat(config.protocolConfig().pollWaitTime(), is(Duration.ofSeconds(30)));
        assertThat(config.protocolConfig().abortPollTimeExpired(), is(true));
        assertThat(config.protocolConfig().initBufferSize(), is(10000));
        assertThat(config.protocolConfig().heartbeatPeriod(), is(Duration.ofSeconds(10)));
    }
}
