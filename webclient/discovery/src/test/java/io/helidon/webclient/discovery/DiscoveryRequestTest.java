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
import java.net.URI;
import java.util.Map;
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
import org.junit.jupiter.api.Test;

import static java.util.HashMap.newHashMap;
import static java.util.logging.Level.FINER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class DiscoveryRequestTest {

    private DiscoveryRequestTest() {
        super();
    }

    @Test
    void discoveryRequestTest() {
        Map<String, String> m = newHashMap(7);
        m.put("helidon-discovery-ids", " prod  ,, test ");
        m.put("helidon-discovery-prod-prefix-uri", "http://example.com/prod/");
        m.put("helidon-discovery-test-prefix-uri", "http://example.com/test"); // note lack of trailing slash
        m.put("helidon-discovery-test-name", "EXPERIMENTAL");

        ClientUri u = ClientUri.create(URI.create("http://example.com/prod/foo"));
        Optional<DiscoveryRequest> o = DiscoveryRequest.of(m, u);
        assertThat(o.isPresent(), is(true));
        DiscoveryRequest d = o.get();
        assertThat(d.discoveryName(), is("prod"));
        assertThat(d.defaultUri(), is(URI.create("http://example.com:80/prod/"))); // note that ClientUri has added :80 internally
        assertThat(d.extraPath(), is("foo"));
        // By spec, URI path resolution takes /prod/ (trailing slash) + foo (no leading slash) and yields /prod/foo
        assertThat(d.defaultUri().resolve(d.extraPath()), is(URI.create("http://example.com:80/prod/foo")));

        u = ClientUri.create(URI.create("http://example.com/test/foo"));
        o = DiscoveryRequest.of(m, u);
        assertThat(o.isPresent(), is(true));
        d = o.get();
        assertThat(d.discoveryName(), is("EXPERIMENTAL"));
        assertThat(d.defaultUri(), is(URI.create("http://example.com:80/test"))); // note lack of trailing slash, addition of :80
        assertThat(d.extraPath(), is("foo"));
        // By spec, URI path resolution takes /test (no trailing slash) + foo (no leading slash) and yields /foo
        assertThat(d.defaultUri().resolve(d.extraPath()), is(URI.create("http://example.com:80/foo")));
    }

    @Test
    void testCustomDiscoveryNames() {
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
                        uris[0] = message.substring("Initial ClientUri: ".length());
                    } else if (message.startsWith("Final ClientUri: ")) {
                        uris[1] = message.substring("Final ClientUri: ".length());
                    }
                }
            };
        l.addHandler(h);
        WebClient c = WebClient.builder()
            .config(Services.get(Config.class).get(this.getClass().getSimpleName() + "-testCustomDiscoveryNames"))
            .build();
        Level level = l.getLevel();
        l.setLevel(FINER);
        try {
            // base-uri is http://prod.example.com/foo; full request therefore will be http://prod.example.com:80/foo/foo
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

}
