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

package io.helidon.webserver.http1;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

public class ConnectionConfigTest {

    @Test
    void testConnectionConfig() {
        // This will pick up application.yaml from the classpath as default configuration file
        TestConnectionSelectorProvider.reset();

        Config config = Config.create();
        WebServer.builder().config(config.get("server")).build();

        Map<String, Http1Config> http1Configs = TestConnectionSelectorProvider.config();

        assertThat(http1Configs, hasKey("@default"));
        assertThat(http1Configs, hasKey("other"));
        assertThat("Discover services is disabled for admin port", http1Configs, not(hasKey("admin")));

        Http1Config http1Config = http1Configs.get("@default");
        assertThat(http1Config.maxPrologueLength(), is(4096));
        assertThat(http1Config.maxHeadersSize(), is(8192));
        assertThat(http1Config.validatePath(), is(true));
        assertThat(http1Config.validateRequestHeaders(), is(true));
        assertThat(http1Config.validateResponseHeaders(), is(false));

        http1Config = http1Configs.get("other");
        assertThat(http1Config.maxPrologueLength(), is(81));
        assertThat(http1Config.maxHeadersSize(), is(42));
        assertThat(http1Config.validatePath(), is(false));
        assertThat(http1Config.validateRequestHeaders(), is(false));
        assertThat(http1Config.validateResponseHeaders(), is(true));
    }

}
