/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.webclient;

import java.net.URI;
import java.util.Set;
import java.util.function.Function;

import io.helidon.config.Config;
import org.junit.jupiter.api.Test;

import static io.helidon.webclient.WebClientRequestBuilderImpl.relativizeNoProxy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Unit test for {@link Proxy}.
 */
class ProxyTest {

    @Test
    void testNoProxyHandling() {
        Set<String> noProxy = Set.of("localhost:8080",
                                     ".helidon.io",
                                     "www.oracle.com",
                                     "192.168.1.1",
                                     "[2001:db8:85a3:8d3:1319:8a2e:370:7348]:443",
                                     ".0.0.1",
                                     "[::1]");

        Function<URI, Boolean> fun = Proxy.prepareNoProxy(noProxy);

        assertThat("[::1]:80", fun.apply(address("[::1]", 80)), is(true));
        assertThat("localhost:8080", fun.apply(address("localhost", 8080)), is(true));
        assertThat("localhost:8081", fun.apply(address("localhost", 8081)), is(false));
        assertThat("helidon.io:80", fun.apply(address("helidon.io", 80)), is(true));
        assertThat("docs.helidon.io:80", fun.apply(address("docs.helidon.io", 80)), is(true));
        assertThat("www.oracle.com:443", fun.apply(address("www.oracle.com", 443)), is(true));
        assertThat("docs.oracle.com:443", fun.apply(address("docs.oracle.com", 443)), is(false));
        assertThat("192.168.1.1:8081", fun.apply(address("192.168.1.1", 8081)), is(true));
        assertThat("192.168.1.2:8081", fun.apply(address("192.168.1.2", 8081)), is(false));
        assertThat("127.0.0.1:80", fun.apply(address("127.0.0.1", 80)), is(false));
        assertThat("[2001:db8:85a3:8d3:1319:8a2e:370:7348]:443",
                   fun.apply(address("[2001:db8:85a3:8d3:1319:8a2e:370:7348]", 443)),
                   is(true));
        assertThat("[2001:db8:85a3:8d3:1319:8a2e:370:7349]:443",
                   fun.apply(address("[2001:db8:85a3:8d3:1319:8a2e:370:7349]", 443)),
                   is(false));
        assertThat("[2001:db8:85a3:8d3:1319:8a2e:370:7348]:445",
                   fun.apply(address("[2001:db8:85a3:8d3:1319:8a2e:370:7348]", 445)),
                   is(false));

    }

    @Test
    void testNoProxyHandlingPredicate() {
        Config config = Config.create();
        Proxy proxy = Proxy.create(config.get("proxy"));
        assertThat(relativizeNoProxy(URI.create("http://localhost/foo"), proxy).toString(),
                is("/foo"));
        assertThat(relativizeNoProxy(URI.create("http://www.localhost/foo"), proxy).toString(),
                is("http://www.localhost/foo"));
        assertThat(relativizeNoProxy(URI.create("http://identity.oc9qadev.com/foo/bar"), proxy).toString(),
                is("/foo/bar"));
        assertThat(relativizeNoProxy(URI.create("http://identity.oci1234.oc9qadev.com/foo/bar"), proxy).toString(),
                is("/foo/bar"));
    }

    @Test
    void testDefaultProxyType() {
        Config config = Config.create();
        Proxy proxy = Proxy.create(config.get("proxy"));
        assertThat(proxy.type(), is(Proxy.ProxyType.HTTP));
    }

    private URI address(String host, int port) {
        return URI.create("http://" + host + ":" + port);
    }
}