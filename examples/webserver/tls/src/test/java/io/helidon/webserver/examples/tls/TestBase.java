/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.tls;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1Client.Http1ClientBuilder;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
abstract class TestBase {

    private final Http1Client client;

    TestBase(Http1ClientBuilder clientBuilder) {
        this.client = clientBuilder.tls(Tls.builder().trustAll(true)).build();
    }

    static Config createConfig() {
        return Config.create(ConfigSources.classpath("test-application.yaml"),
                ConfigSources.classpath("application.yaml"));
    }

    @Test
    void testSsl() {
        assertThat(client.get().request(String.class), is("Hello!"));
    }
}
