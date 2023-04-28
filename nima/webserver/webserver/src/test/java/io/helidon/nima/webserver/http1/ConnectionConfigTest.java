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

package io.helidon.nima.webserver.http1;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.helidon.config.Config;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.spi.ServerConnectionProvider;
import io.helidon.nima.webserver.spi.ServerConnectionSelector;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

public class ConnectionConfigTest {

    @Test
    void testConnectionConfig() {
        // This will pick up application.yaml from the classpath as default configuration file
        Config config = Config.create();
        TestProvider provider = new TestProvider();
        WebServer.builder().config(config.get("server")).addConnectionProvider(provider).build();
        assertThat(provider.isConfig(), is(true));
        Http1Config http1Config = provider.config();
        assertThat(http1Config.maxPrologueLength(), is(4096));
        assertThat(http1Config.maxHeadersSize(), is(8192));
        assertThat(http1Config.validatePath(), is(false));
        assertThat(http1Config.validateHeaders(), is(false));
    }

    private static class TestProvider implements ServerConnectionProvider {

        private Http1Config http1Config = null;
        private Config config = null;

        @Override
        public Iterable<String> configKeys() {
            return List.of("http_1_1");
        }

        @Override
        public ServerConnectionSelector create(Function<String, Config> configs) {
            config = configs.apply("http_1_1");
            http1Config = Http1ConfigDefault.toBuilder(config).build();
            return mock(ServerConnectionSelector.class);
        }

        private Http1Config config() {
            return http1Config;
        }

        private boolean isConfig() {
            return http1Config != null;
        }

    }

}
