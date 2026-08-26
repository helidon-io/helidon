/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.security;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebClientSecurityTest {

    @Test
    void registryWebClientUsesOwningRegistrySecurity() {
        AtomicInteger outboundCalls = new AtomicInteger();
        Security security = Security.builder()
                .addOutboundSecurityProvider((request, environment, config) -> {
                    outboundCalls.incrementAndGet();
                    return OutboundSecurityResponse.abstain();
                })
                .build();
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(Security.class, security)
                                                                                .build());
        try {
            WebClient managedClient = manager.registry().get(WebClient.class);
            WebClientService securityService = managedClient.prototype()
                    .services()
                    .stream()
                    .filter(service -> service.type().equals("security"))
                    .findFirst()
                    .orElseThrow();
            WebClientService stopBeforeNetwork = (chain, request) -> {
                throw new TestException();
            };
            Http1Client client = Http1Client.builder()
                    .baseUri("https://example.test")
                    .servicesDiscoverServices(false)
                    .addService(securityService)
                    .addService(stopBeforeNetwork)
                    .build();

            assertThrows(TestException.class, () -> client.get().request());

            assertThat("Owning registry outbound provider calls", outboundCalls.get(), is(1));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void matchesHttpsOutboundTargetUsingRequestScheme() {
        AtomicReference<SecurityEnvironment> outboundEnvironment = new AtomicReference<>();
        Security security = Security.builder()
                .addOutboundSecurityProvider((request, environment, config) -> {
                    outboundEnvironment.set(environment);
                    return OutboundSecurityResponse.abstain();
                })
                .build();
        OutboundConfig outboundConfig = OutboundConfig.builder()
                .addTarget(OutboundTarget.builder("https-target")
                                   .addTransport("https")
                                   .addHost("idcs.example.test")
                                   .addPath("/oauth2/v1/introspect")
                                   .addMethod("POST")
                                   .build())
                .build();
        WebClientService stopBeforeNetwork = (chain, request) -> {
            throw new TestException();
        };
        Http1Client client = Http1Client.builder()
                .baseUri("https://idcs.example.test/oauth2/v1/introspect")
                .servicesDiscoverServices(false)
                .addService(WebClientSecurity.create(security))
                .addService(stopBeforeNetwork)
                .build();

        assertThrows(TestException.class, () -> client.post().request());

        SecurityEnvironment environment = outboundEnvironment.get();
        assertThat(environment.targetUri().getScheme(), is("https"));
        assertThat(environment.transport(), is("https"));
        assertThat(environment.requestedQuery().isEmpty(), is(true));
        assertThat(outboundConfig.findTarget(environment).isPresent(), is(true));
    }

    @Test
    void requestedQueryMatchesSerializedQueryWithoutChangingDefaultEncoding() {
        AtomicReference<SecurityEnvironment> outboundEnvironment = new AtomicReference<>();
        AtomicReference<String> outboundTarget = new AtomicReference<>();
        Security security = Security.builder()
                .addOutboundSecurityProvider((request, environment, config) -> {
                    outboundEnvironment.set(environment);
                    return OutboundSecurityResponse.abstain();
                })
                .build();
        WebClientService stopBeforeNetwork = (chain, request) -> {
            outboundTarget.set(request.uri().pathWithQueryAndFragment());
            throw new TestException();
        };
        Http1Client client = Http1Client.builder()
                .servicesDiscoverServices(false)
                .addService(WebClientSecurity.create(security))
                .addService(stopBeforeNetwork)
                .build();

        assertThrows(TestException.class, () -> client.get()
                .uri("https://example.test/{path}")
                .pathParam("path", "p")
                .queryParam("q", "a%2Fb")
                .request());

        SecurityEnvironment environment = outboundEnvironment.get();
        assertThat(outboundTarget.get(), is("/p?q=a%252Fb"));
        assertThat(environment.targetUri().getRawQuery(), is("q=a%252Fb"));
        assertThat(environment.requestedPath().rawPath(), is("/p"));
        assertThat(environment.requestedQuery().orElseThrow().rawValue(), is("q=a%252Fb"));
        assertThat(environment.queryParams().rawValue(), is("q=a%252Fb"));
        assertThat(environment.queryParams().get("q"), is("a%2Fb"));

        assertThrows(TestException.class, () -> client.get()
                .uri("https://example.test/{path}")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("q", "a%2Fb")
                .request());

        environment = outboundEnvironment.get();
        assertThat(outboundTarget.get(), is("/p?q=a%2Fb"));
        assertThat(environment.targetUri().getRawQuery(), is("q=a%2Fb"));
        assertThat(environment.requestedPath().rawPath(), is("/p"));
        assertThat(environment.requestedQuery().orElseThrow().rawValue(), is("q=a%2Fb"));
        assertThat(environment.queryParams().rawValue(), is("q=a%252Fb"));
        assertThat(environment.queryParams().get("q"), is("a%2Fb"));

        assertThrows(TestException.class, () -> client.get()
                .uri("https://example.test/{path}")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("q", "a#b")
                .request());

        environment = outboundEnvironment.get();
        assertThat(outboundTarget.get(), is("/p?q=a#b"));
        assertThat(environment.targetUri().getRawQuery(), is("q=a"));
        assertThat(environment.targetUri().getRawFragment(), is("b"));
        assertThat(environment.requestedQuery().orElseThrow().rawValue(), is("q=a#b"));
        assertThat(environment.queryParams().rawValue(), is("q=a%23b"));
        assertThat(environment.queryParams().get("q"), is("a#b"));

        assertThrows(TestException.class, () -> client.get()
                .uri(URI.create("https://example.test/p?"))
                .request());

        environment = outboundEnvironment.get();
        assertThat(outboundTarget.get(), is("/p?"));
        assertThat(environment.targetUri().getRawQuery(), is(""));
        assertThat(environment.requestedQuery().orElseThrow().rawValue(), is(""));
    }

    private static final class TestException extends RuntimeException {
    }
}
