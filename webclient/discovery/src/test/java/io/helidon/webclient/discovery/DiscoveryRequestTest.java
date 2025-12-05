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
package io.helidon.webclient.discovery;

import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogRecord;

import io.helidon.config.Config;
import io.helidon.service.registry.Services;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.discovery.DefaultWebClientDiscovery.DiscoveryRequest;
import io.helidon.webclient.spi.WebClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static java.util.HashMap.newHashMap;
import static java.util.logging.Level.FINER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class DiscoveryRequestTest {

    private WebClient c;

    private DiscoveryRequestTest() {
        super();
    }

    @BeforeEach
    void setup(TestInfo ti) {
        // Get the proper configuration in ../../../../../../../target/test-classes/application.yaml for the current
        // test. For a method in this class named, e.g., testFoo, the top-level WebClient configuration key in
        // ../../../../../../../target/test-classes/application.yaml will be DiscoveryRequestTest-testFoo.
        this.c = WebClient.builder()
            .config(Services.get(Config.class)
                    .get(ti.getTestClass().map(Class::getSimpleName).get()
                         + "-"
                         + ti.getTestMethod().map(Method::getName).get()))
            .build();
    }

    @Test
    void testOrdering() {
        List<WebClientService> services = c.prototype().services();
        assertThat(services, hasSize(1));
        Collection<? extends Entry<? extends URI, ? extends String>> discoveryNames =
            ((DefaultWebClientDiscovery) services.get(0)).discoveryNames();
        assertThat(discoveryNames, hasSize(4));
        var i = discoveryNames.iterator();
        Entry<? extends URI, ? extends String> e = i.next();
        assertThat(e.getKey(), is(URI.create("https://example.com:443/xxxxxx/xxxx")));
        assertThat(e.getValue(), is("long2"));
        e = i.next();
        assertThat(e.getKey(), is(URI.create("https://example.com:443/medium/long")));
        assertThat(e.getValue(), is("long"));
        e = i.next();
        assertThat(e.getKey(), is(URI.create("https://example.com:443/medium")));
        assertThat(e.getValue(), is("medium"));
        e = i.next();
        assertThat(e.getKey(), is(URI.create("https://example.com:443/")));
        assertThat(e.getValue(), is("short"));
    }

    @Test
    void testSimpleActualUsage() {
        String[] uris = new String[2];
        Logger l = Logger.getLogger(DefaultWebClientDiscovery.class.getName());
        Handler h = new Handler() {
                @Override
                public void close() {}
                @Override
                public void flush() {}
                @Override
                public void publish(LogRecord r) {
                    if (r == null) {
                        return;
                    }
                    String message = r.getMessage();
                    if (message.startsWith("Initial ClientUri: ")) {
                        uris[0] = r.getParameters()[0].toString();
                    } else if (message.startsWith("Final ClientUri: ")) {
                        uris[1] = r.getParameters()[0].toString();
                    }
                }
            };
        l.addHandler(h);
        Level level = l.getLevel();
        l.setLevel(FINER);
        try {
            c.get("foo").request();
        } catch (IllegalArgumentException dns) {
            assertThat(dns.getMessage(), is("Failed to get address for host prod.example.com"));
        } catch (UncheckedIOException expected) {
            assertThat(expected.getCause(), is(instanceOf(java.net.SocketTimeoutException.class)));
        } finally {
            l.removeHandler(h);
            l.setLevel(level);
        }
        // see src/test/resources/application.yaml
        assertThat(uris[0], containsString("http://prod.example.com:80/foo/foo"));
        assertThat(uris[1], is("http://prod.example.com:80/foo"));
    }

    @Test
    void discoveryRequestTest() {
        List<Entry<URI, String>> discoveryNames = new ArrayList<>();
        // Note trailing slash:
        discoveryNames.add(new SimpleEntry<>(ClientUri.create(URI.create("http://example.com/prod/")).toUri(), "prod"));
        // Note lack of trailing slash:
        discoveryNames.add(new SimpleEntry<>(ClientUri.create(URI.create("http://example.com/test")).toUri(), "EXPERIMENTAL"));
        ClientUri u = ClientUri.create(URI.create("http://example.com/prod/foo"));
        Optional<DiscoveryRequest> o = DiscoveryRequest.of(discoveryNames, u);
        assertThat(o.isPresent(), is(true));
        DiscoveryRequest d = o.get();
        assertThat(d.discoveryName(), is("prod"));
        assertThat(d.defaultUri(), is(URI.create("http://example.com:80/prod/"))); // note that ClientUri has added :80 internally
        assertThat(d.extraPath(), is("foo"));
        // By spec, URI path resolution takes /prod/ (trailing slash) + foo (no leading slash) and yields /prod/foo
        assertThat(d.defaultUri().resolve(d.extraPath()), is(URI.create("http://example.com:80/prod/foo")));

        u = ClientUri.create(URI.create("http://example.com/test/foo"));
        o = DiscoveryRequest.of(discoveryNames, u);
        assertThat(o.isPresent(), is(true));
        d = o.get();
        assertThat(d.discoveryName(), is("EXPERIMENTAL"));
        assertThat(d.defaultUri(), is(URI.create("http://example.com:80/test"))); // note lack of trailing slash, addition of :80
        assertThat(d.extraPath(), is("foo"));
        // By spec, URI path resolution takes /test (no trailing slash) + foo (no leading slash) and yields /foo
        assertThat(d.defaultUri().resolve(d.extraPath()), is(URI.create("http://example.com:80/foo")));
    }

}
