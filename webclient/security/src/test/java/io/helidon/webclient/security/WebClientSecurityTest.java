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

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.Subject;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.spi.AuditProvider;
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
    void registryWebClientWithoutOutboundProvidersSkipsSecurityContextCreation() {
        AtomicInteger providerLookups = new AtomicInteger();
        Security security = (Security) Proxy.newProxyInstance(Security.class.getClassLoader(),
                                                              new Class<?>[] {Security.class},
                                                              (proxy, method, args) -> {
                                                                  if (method.getName().equals("resolveOutboundProvider")) {
                                                                      providerLookups.incrementAndGet();
                                                                      return List.of();
                                                                  }
                                                                  return switch (method.getName()) {
                                                                      case "enabled" -> true;
                                                                      case "equals" -> proxy == args[0];
                                                                      case "hashCode" -> System.identityHashCode(proxy);
                                                                      case "toString" -> "Security without outbound providers";
                                                                      default -> throw new AssertionError(
                                                                              "Unexpected security call: " + method);
                                                                  };
                                                              });
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(Security.class, security)
                                                                                .build());
        try {
            WebClientService stopBeforeNetwork = (chain, request) -> {
                throw new TestException();
            };
            Http1Client client = Http1Client.builder()
                    .baseUri("https://example.test")
                    .servicesDiscoverServices(false)
                    .addService(managedSecurityService(manager))
                    .addService(stopBeforeNetwork)
                    .build();

            assertThrows(TestException.class, () -> client.get().request());

            assertThat("Outbound provider lookups", providerLookups.get(), is(1));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void registryWebClientSkipsDisabledSecurityWithOutboundProvider() {
        AtomicInteger outboundCalls = new AtomicInteger();
        Security security = Security.builder()
                .enabled(false)
                .addOutboundSecurityProvider((request, environment, config) -> {
                    outboundCalls.incrementAndGet();
                    return OutboundSecurityResponse.abstain();
                })
                .build();
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(Security.class, security)
                                                                                .build());
        try {
            WebClientService stopBeforeNetwork = (chain, request) -> {
                throw new TestException();
            };
            Http1Client client = Http1Client.builder()
                    .baseUri("https://example.test")
                    .servicesDiscoverServices(false)
                    .addService(managedSecurityService(manager))
                    .addService(stopBeforeNetwork)
                    .build();

            assertThrows(TestException.class, () -> client.get().request());

            assertThat("Disabled security outbound provider calls", outboundCalls.get(), is(0));
        } finally {
            manager.shutdown();
        }
    }

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
            WebClientService stopBeforeNetwork = (chain, request) -> {
                throw new TestException();
            };
            Http1Client client = Http1Client.builder()
                    .baseUri("https://example.test")
                    .servicesDiscoverServices(false)
                    .addService(managedSecurityService(manager))
                    .addService(stopBeforeNetwork)
                    .build();

            assertThrows(TestException.class, () -> client.get().request());

            assertThat("Owning registry outbound provider calls", outboundCalls.get(), is(1));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void registryWebClientPreservesAuthenticatedSubjects() {
        Subject user = Subject.create(Principal.create("test-user"));
        Subject service = Subject.create(Principal.create("test-service"));
        AtomicInteger requestAudits = new AtomicInteger();
        AtomicInteger clientAudits = new AtomicInteger();
        AtomicReference<String> clientAuditTracingId = new AtomicReference<>();
        Security requestSecurity = Security.builder()
                .authenticationProvider(request -> AuthenticationResponse.success(user, service))
                .addAuditProvider(outboundAuditor(requestAudits, new AtomicReference<>()))
                .disableTracing()
                .build();
        SecurityContext securityContext = requestSecurity.createContext("test-request");
        securityContext.authenticate();
        Context context = Context.create();
        context.register(securityContext);

        AtomicReference<Optional<Subject>> outboundUser = new AtomicReference<>();
        AtomicReference<Optional<Subject>> outboundService = new AtomicReference<>();
        Security clientSecurity = Security.builder()
                .addOutboundSecurityProvider((request, environment, config) -> {
                    outboundUser.set(request.subject());
                    outboundService.set(request.service());
                    return OutboundSecurityResponse.abstain();
                })
                .addAuditProvider(outboundAuditor(clientAudits, clientAuditTracingId))
                .disableTracing()
                .build();
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(Security.class,
                                                                                                     clientSecurity)
                                                                                .build());
        try {
            WebClientService stopBeforeNetwork = (chain, request) -> {
                throw new TestException();
            };
            Http1Client client = Http1Client.builder()
                    .baseUri("https://example.test")
                    .servicesDiscoverServices(false)
                    .addService(managedSecurityService(manager))
                    .addService(stopBeforeNetwork)
                    .build();

            Contexts.runInContext(context,
                                  () -> assertThrows(TestException.class, () -> client.get().request()));

            assertThat("Outbound user subject", outboundUser.get(), is(Optional.of(user)));
            assertThat("Outbound service subject", outboundService.get(), is(Optional.of(service)));
            assertThat("Request security outbound audits", requestAudits.get(), is(0));
            assertThat("Client security outbound audits", clientAudits.get(), is(1));
            assertThat("Client audit tracing ID", clientAuditTracingId.get(), is(securityContext.id()));
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

    private static WebClientService managedSecurityService(ServiceRegistryManager manager) {
        WebClient managedClient = manager.registry().get(WebClient.class);
        return managedClient.prototype()
                .services()
                .stream()
                .filter(service -> service.type().equals("security"))
                .findFirst()
                .orElseThrow();
    }

    private static AuditProvider outboundAuditor(AtomicInteger count, AtomicReference<String> tracingId) {
        return () -> event -> {
            if ("outbound.outbound".equals(event.eventType())) {
                count.incrementAndGet();
                tracingId.set(event.tracingId());
            }
        };
    }

    private static final class TestException extends RuntimeException {
    }
}
