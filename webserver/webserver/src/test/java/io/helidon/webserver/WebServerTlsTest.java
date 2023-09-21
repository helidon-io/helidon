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

package io.helidon.webserver;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class WebServerTlsTest {

    @Test
    void sslFromConfig() {
        Config config = Config.builder().sources(ConfigSources.classpath("config-with-disabled-tls.conf")).build();
        WebServerTls tls = WebServerTls.builder()
                .config(config.get("tls"))
                .build();

        assertThat(tls.enabled(), is(false));
        assertThat(tls.sslContext(), nullValue());
    }

    @Test
    void defaultManager() {
        Config config = Config.builder().sources(ConfigSources.classpath("config-with-disabled-tls.conf")).build();
        WebServerTls tls = WebServerTls.builder()
                .config(config.get("tls"))
                .build();

        assertThat(tls.manager(),
                   instanceOf(ConfiguredTlsManager.class));
        ConfiguredTlsManager configuredTlsManager = (ConfiguredTlsManager) tls.manager();
        assertThat(configuredTlsManager.name(),
                   equalTo("@default"));
        assertThat(configuredTlsManager.type(),
                   equalTo("tls-manager"));
    }

}
