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

package io.helidon.webclient.tests;

import java.net.InetSocketAddress;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ProxySetupTest {
    private static final Config CONFIG = Config.just(ConfigSources.classpath("proxy-setup-test.yaml"));

    @Test
    void testNoProxy() {
        WebClient webClient = WebClient.create(WebClientConfig.create(CONFIG.get("no-proxy")));
        Proxy proxy = webClient.prototype().proxy();

        assertThat(proxy.type(), is(Proxy.ProxyType.NONE));
    }

    @Test
    void testSystemProxy() {
        WebClient webClient = WebClient.create(WebClientConfig.create(CONFIG.get("system-proxy")));
        Proxy proxy = webClient.prototype().proxy();

        assertThat(proxy.type(), is(Proxy.ProxyType.SYSTEM));
    }

    @Test
    void testHttpProxy() {
        WebClient webClient = WebClient.create(WebClientConfig.create(CONFIG.get("http-proxy")));
        Proxy proxy = webClient.prototype().proxy();

        assertThat(proxy.type(), is(Proxy.ProxyType.HTTP));
        assertThat(proxy.host(), is("proxy.host.example"));
        assertThat(proxy.port(), is(9999));
        assertThat(proxy.username(), optionalValue(is("user")));
        assertThat(proxy.password(), optionalValue(is("password".toCharArray())));
        assertThat(proxy.isNoHosts(new InetSocketAddress("www.helidon.io", 80)), is(true));
        assertThat(proxy.isNoHosts(new InetSocketAddress("start.helidon.io", 80)), is(true));
        assertThat(proxy.isNoHosts(new InetSocketAddress("www.oracle.com", 80)), is(false));
    }
}
