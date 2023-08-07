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
package io.helidon.webclient;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import io.helidon.common.testing.junit5.ResetSystemProperties;
import io.helidon.config.Config;

import io.netty.channel.ChannelHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import org.junit.jupiter.api.Test;

import static io.helidon.webclient.WebClientRequestBuilderImpl.relativizeNoProxy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
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
        assertThat(relativizeNoProxy(URI.create("http://localhost/foo"), proxy, false).toString(),
                is("/foo"));
        assertThat(relativizeNoProxy(URI.create("http://www.localhost/foo"), proxy, false).toString(),
                is("http://www.localhost/foo"));
        assertThat(relativizeNoProxy(URI.create("http://identity.oc9qadev.com/foo/bar"), proxy, false).toString(),
                is("/foo/bar"));
        assertThat(relativizeNoProxy(URI.create("http://identity.oci1234.oc9qadev.com/foo/bar"), proxy, false).toString(),
                is("/foo/bar"));
    }

    @Test
    void testForceRelativeUrisViaWebClientConfiguration() {
        Config config = Config.create();
        Proxy proxy = Proxy.create(config.get("proxy"));
        WebClientConfiguration webConfig = WebClientConfiguration.builder()
                .config(config.get("force-relative-uris"))
                .proxy(proxy)
                .build();
        validateRelativizeNoProxy(webConfig);
    }

    @Test
    void testForceRelativeUrisViaWebClient() {
        Proxy proxy = Proxy.builder()
                .host("localhost")
                .port(8080)
                .build();
        WebClientConfiguration webConfig = WebClient.builder()
                .relativeUris(true)
                .proxy(proxy)
                .configuration();
        validateRelativizeNoProxy(webConfig);
    }


    @Test
    @ResetSystemProperties
    void testProxyWithSystemSelectorDirect() {
        //We need to ensure there will be no properties interfering from our local system properties
        clearSystemProxyProperties();

        Proxy proxy = Proxy.create();
        Optional<ChannelHandler> handler = proxy.handler(URI.create("www.example.com:80"));
        assertThat(handler.isPresent(), is(false)); //no proxy set -> direct approach
    }

    @Test
    @ResetSystemProperties
    void testProxyWithSystemSelectorHttpHandler() {
        //We need to ensure there will be no properties interfering from our local system properties
        clearSystemProxyProperties();
        System.setProperty("http.proxyHost", "localhost");
        System.setProperty("http.proxyPort", "8080");

        Proxy proxy = Proxy.create();
        Optional<ChannelHandler> handler = proxy.handler(URI.create("www.example.com:80"));
        assertThat(handler.isPresent(), is(true));
        assertThat(handler.get(), instanceOf(HttpProxyHandler.class));
    }

    @Test
    @ResetSystemProperties
    void testProxyWithSystemSelectorSocksHandler() {
        //We need to ensure there will be no properties interfering from our local system properties
        clearSystemProxyProperties();
        System.setProperty("socksProxyHost", "localhost");
        System.setProperty("socksProxyPort", "1080");

        Proxy proxy = Proxy.create();
        Optional<ChannelHandler> handler = proxy.handler(URI.create("www.example.com:80"));
        assertThat(handler.isPresent(), is(true));
        assertThat(handler.get(), instanceOf(Socks5ProxyHandler.class));
    }

    private void clearSystemProxyProperties() {
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");
    }

    @Test
    void testDefaultProxyType() {
        Config config = Config.create();
        Proxy proxy = Proxy.create(config.get("proxy"));
        assertThat(proxy.type(), is(Proxy.ProxyType.HTTP));
    }

    @Test
    void testDefaultSystem1() {
        Proxy proxy = Proxy.create();
        assertThat(proxy.type(), is(Proxy.ProxyType.SYSTEM));
    }

    @Test
    void testDefaultSystem2() {
        Proxy proxy = Proxy.create(Config.empty());
        assertThat(proxy.type(), is(Proxy.ProxyType.SYSTEM));
    }

    private void validateRelativizeNoProxy(WebClientConfiguration webConfig) {
        boolean relativeUris = webConfig.relativeUris();
        Proxy proxy = webConfig.proxy().get();
        assertThat(relativizeNoProxy(URI.create("http://www.localhost/foo"), proxy, relativeUris).toString(),
                   is("/foo"));
        assertThat(relativizeNoProxy(URI.create("http://identity.oci.com/foo/bar"), proxy, relativeUris).toString(),
                   is("/foo/bar"));
    }

    private URI address(String host, int port) {
        return URI.create("http://" + host + ":" + port);
    }
}
