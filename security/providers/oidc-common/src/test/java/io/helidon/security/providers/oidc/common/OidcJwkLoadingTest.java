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

package io.helidon.security.providers.oidc.common;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Errors;
import io.helidon.common.configurable.ResourceConfig;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.faulttolerance.CircuitBreaker;
import io.helidon.faulttolerance.CircuitBreakerConfig;
import io.helidon.faulttolerance.ResilientValue;
import io.helidon.faulttolerance.Retry;
import io.helidon.faulttolerance.RetryConfig;
import io.helidon.faulttolerance.Timeout;
import io.helidon.faulttolerance.TimeoutConfig;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OidcJwkLoadingTest {
    private static final String JWK_JSON = """
            {"keys":[{"kty":"oct","kid":"test-key","alg":"HS256",
                      "key_ops":["sign","verify"],
                      "k":"FdFYFzERwC2uCBB46pZQi4GG85LujR8obt-KWRBICVQ"}]}
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void nestedFaultToleranceConfigurationUsesFaultToleranceDefaults() {
        Config defaults = config(Map.of("client-id", "client",
                                        "client-secret", "secret",
                                        "identity-uri", "https://identity.example",
                                        "jwk-loader.retry.calls", "5",
                                        "jwk-loader.retry.delay", "PT0.007S",
                                        "jwk-loader.circuit-breaker.volume", "4",
                                        "jwk-loader.circuit-breaker.delay", "PT9S"));
        Config tenant = config(Map.of("name", "tenant",
                                      "jwk-loader.retry.calls", "2",
                                      "jwk-loader.circuit-breaker.success-threshold", "2"));

        TenantConfig tenantConfig = TenantConfig.tenantBuilder()
                .config(defaults)
                .config(tenant)
                .build();

        assertThat(tenantConfig.jwkRetry().prototype().calls(), is(2));
        assertThat(tenantConfig.jwkRetry().prototype().delay(), is(Duration.ofMillis(200)));
        assertThat(tenantConfig.jwkRetry().prototype().overallTimeout(), is(Duration.ofSeconds(11)));
        assertThat(tenantConfig.jwkTimeout().prototype().timeout(), is(Duration.ofSeconds(5)));
        assertThat(tenantConfig.jwkTimeout().prototype().currentThread(), is(true));
        assertThat(tenantConfig.jwkCircuitBreaker().prototype().volume(), is(1));
        assertThat(tenantConfig.jwkCircuitBreaker().prototype().errorRatio(), is(100));
        assertThat(tenantConfig.jwkCircuitBreaker().prototype().delay(), is(Duration.ofSeconds(5)));
        assertThat(tenantConfig.jwkCircuitBreaker().prototype().successThreshold(), is(2));
    }

    @Test
    void usesConfiguredFaultToleranceInstances() {
        Retry retry = Retry.builder().calls(1).overallTimeout(Duration.ofSeconds(5)).build();
        CircuitBreaker circuitBreaker = CircuitBreaker.builder().volume(1).build();
        Timeout timeout = Timeout.builder().timeout(Duration.ofSeconds(1)).currentThread(true).build();

        TenantConfig tenantConfig = baseBuilder()
                .jwkRetry(retry)
                .jwkCircuitBreaker(circuitBreaker)
                .jwkTimeout(timeout)
                .build();

        assertThat(tenantConfig.jwkRetry(), sameInstance(retry));
        assertThat(tenantConfig.jwkCircuitBreaker(), sameInstance(circuitBreaker));
        assertThat(tenantConfig.jwkTimeout(), sameInstance(timeout));
    }

    @Test
    void unusedFaultToleranceSuppliersAreLazy() {
        AtomicInteger creations = new AtomicInteger();
        OidcConfig config = baseBuilder()
                .signJwk(fixedJwkResource())
                .jwkRetry(() -> {
                    creations.incrementAndGet();
                    return BaseBuilder.defaultJwkRetry();
                })
                .jwkCircuitBreaker(() -> {
                    creations.incrementAndGet();
                    return BaseBuilder.defaultJwkCircuitBreaker();
                })
                .jwkTimeout(() -> {
                    creations.incrementAndGet();
                    return BaseBuilder.defaultJwkTimeout();
                })
                .build();

        assertThat(creations.get(), is(0));

        config.jwkRetry();
        config.jwkCircuitBreaker();
        config.jwkTimeout();
        assertThat(creations.get(), is(3));
    }

    @Test
    void prototypeFaultToleranceInstancesAreIndependent() {
        RetryConfig retry = RetryConfig.builder()
                .calls(1)
                .overallTimeout(Duration.ofSeconds(1))
                .buildPrototype();
        CircuitBreakerConfig circuitBreaker = CircuitBreakerConfig.builder()
                .volume(1)
                .errorRatio(100)
                .successThreshold(1)
                .delay(Duration.ofDays(1))
                .addApplyOn(ResilientValue.UnavailableException.class)
                .buildPrototype();
        TimeoutConfig timeout = TimeoutConfig.builder()
                .timeout(Duration.ofSeconds(1))
                .currentThread(true)
                .buildPrototype();
        OidcConfig.Builder builder = baseBuilder()
                .signJwk(fixedJwkResource())
                .jwkRetry(retry)
                .jwkCircuitBreaker(circuitBreaker)
                .jwkTimeout(timeout);

        OidcConfig first = builder.build();
        OidcConfig second = builder.build();

        assertThat(first.jwkRetry(), not(sameInstance(second.jwkRetry())));
        assertThat(first.jwkCircuitBreaker(), not(sameInstance(second.jwkCircuitBreaker())));
        assertThat(first.jwkTimeout(), not(sameInstance(second.jwkTimeout())));
        assertThrows(ResilientValue.UnavailableException.class,
                     () -> first.jwkCircuitBreaker().invoke(() -> {
                         throw new ResilientValue.UnavailableException("not ready");
                     }));
        assertThat(second.jwkCircuitBreaker().invoke(() -> "available"), is("available"));
    }

    @Test
    void rejectsInconsistentJwkTimeoutAtStartup() {
        ResourceConfig reloadableJwk = ResourceConfig.builder()
                .path(temporaryDirectory.resolve("keys.json"))
                .buildPrototype();
        assertThrows(Errors.ErrorMessagesException.class,
                     () -> baseBuilder()
                             .signJwk(reloadableJwk)
                             .jwkTimeout(Timeout.builder().timeout(Duration.ZERO).build())
                             .build());
        assertThrows(Errors.ErrorMessagesException.class,
                     () -> baseBuilder()
                             .signJwk(reloadableJwk)
                             .jwkRetry(Retry.builder().overallTimeout(Duration.ofSeconds(4)).build())
                             .build());
        assertThrows(Errors.ErrorMessagesException.class,
                     () -> baseBuilder()
                             .signJwk(reloadableJwk)
                             .jwkTimeout(Timeout.builder()
                                                 .timeout(Duration.ofSeconds(1))
                                                 .currentThread(false)
                                                 .build())
                             .build());
    }

    @Test
    void consumerTimeoutUsesJwkDefaults() {
        ResourceConfig reloadableJwk = ResourceConfig.builder()
                .path(temporaryDirectory.resolve("keys.json"))
                .buildPrototype();

        OidcConfig config = baseBuilder()
                .signJwk(reloadableJwk)
                .jwkTimeout(it -> it.timeout(Duration.ofSeconds(1)))
                .build();

        assertThat(config.jwkTimeout().prototype().currentThread(), is(true));
    }

    @Test
    void generatedMetadataContainsFaultToleranceOptions() throws IOException {
        String metadata;
        try (var stream = BaseBuilder.class.getResourceAsStream("/META-INF/helidon/config-metadata.json")) {
            assertThat(stream, is(notNullValue()));
            metadata = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(metadata,
                   containsString("\"key\":\"jwk-loader.retry\",\"type\":"
                                          + "\"io.helidon.faulttolerance.Retry\""));
        assertThat(metadata,
                   containsString("\"key\":\"jwk-loader.circuit-breaker\",\"type\":"
                                          + "\"io.helidon.faulttolerance.CircuitBreaker\""));
        assertThat(metadata,
                   containsString("\"key\":\"jwk-loader.timeout\",\"type\":"
                                          + "\"io.helidon.faulttolerance.Timeout\""));
    }

    @Test
    void replacesInheritedRetryDelayStrategy() {
        Config delayFactorDefaults = config(Map.of("client-id", "client",
                                                   "client-secret", "secret",
                                                   "identity-uri", "https://identity.example",
                                                   "jwk-loader.retry.delay-factor", "2"));
        Config jitterTenant = config(Map.of("name", "tenant",
                                             "jwk-loader.retry.jitter", "PT0.003S"));

        TenantConfig jitterConfig = TenantConfig.tenantBuilder()
                .config(delayFactorDefaults)
                .config(jitterTenant)
                .build();

        assertThat(jitterConfig.jwkRetry().prototype().delayFactor(), is(-1D));
        assertThat(jitterConfig.jwkRetry().prototype().jitter(), is(Duration.ofMillis(3)));

        Config jitterDefaults = config(Map.of("client-id", "client",
                                              "client-secret", "secret",
                                              "identity-uri", "https://identity.example",
                                              "jwk-loader.retry.jitter", "PT0.004S"));
        Config delayFactorTenant = config(Map.of("name", "tenant",
                                                 "jwk-loader.retry.delay-factor", "3"));

        TenantConfig delayFactorConfig = TenantConfig.tenantBuilder()
                .config(jitterDefaults)
                .config(delayFactorTenant)
                .build();

        assertThat(delayFactorConfig.jwkRetry().prototype().delayFactor(), is(3D));
        assertThat(delayFactorConfig.jwkRetry().prototype().jitter(), is(Duration.ofSeconds(-1)));

        Config explicitDelayFactorTenant = config(Map.of("name", "tenant",
                                                          "jwk-loader.retry.delay-factor", "3",
                                                          "jwk-loader.retry.jitter", "PT0.004S"));
        TenantConfig explicitDelayFactorConfig = TenantConfig.tenantBuilder()
                .config(jitterDefaults)
                .config(explicitDelayFactorTenant)
                .build();
        assertThat(explicitDelayFactorConfig.jwkRetry().prototype().delayFactor(), is(3D));
        assertThat(explicitDelayFactorConfig.jwkRetry().prototype().jitter(), is(Duration.ofMillis(4)));
    }

    @Test
    void validatesFixedResourcesAndDefersFileResources() {
        assertThrows(IllegalArgumentException.class,
                     () -> baseBuilder().signJwk(JwkKeys.builder().build()));

        ResourceConfig conflicting = ResourceConfig.builder()
                .path(temporaryDirectory.resolve("keys.json"))
                .contentPlain(JWK_JSON)
                .buildPrototype();
        assertThrows(IllegalArgumentException.class, () -> baseBuilder().signJwk(conflicting));

        ResourceConfig missingClasspathResource = ResourceConfig.builder()
                .resourcePath("missing-oidc-jwk.json")
                .buildPrototype();
        assertThrows(IllegalArgumentException.class, () -> baseBuilder().signJwk(missingClasspathResource));

        Path keyPath = temporaryDirectory.resolve("keys.json");
        baseBuilder()
                .signJwk(ResourceConfig.builder().path(keyPath).buildPrototype())
                .jwkRetry(singleCallRetry())
                .jwkCircuitBreaker(twoFailureCircuitBreaker())
                .build();
    }

    @Test
    void genericConfigDoesNotRequireSigningJwk() {
        OidcConfig config = baseBuilder()
                .webclient(it -> it.clearServices().servicesDiscoverServices(false))
                .build();

        assertThat(config.tokenEndpointUri(), is(URI.create("https://identity.example/oauth2/v1/token")));
        assertThat(config.appWebClient(), is(notNullValue()));
        assertThat(config.signJwk().keys().isEmpty(), is(true));
    }

    @Test
    void classifiesOnlyRecoverableTenantSourcesAsLazy() {
        TenantConfig missingSigningSource = baseBuilder().build();
        assertThat(missingSigningSource.tenantLoadingLazy(), is(false));

        TenantConfig ignoredSigningResource = baseBuilder()
                .signJwk(ResourceConfig.builder()
                                 .path(temporaryDirectory.resolve("unused-keys.json"))
                                 .buildPrototype())
                .validateJwtWithJwk(false)
                .build();
        assertThat(ignoredSigningResource.tenantLoadingLazy(), is(false));

        TenantConfig fixedMetadataWithRemoteJwk = baseBuilder()
                .oidcMetadata(ResourceConfig.builder()
                                      .contentPlain("{\"jwks_uri\":\"https://identity.example/jwks\"}")
                                      .buildPrototype())
                .build();
        assertThat(fixedMetadataWithRemoteJwk.tenantLoadingLazy(), is(true));

        TenantConfig reloadableMetadata = baseBuilder()
                .oidcMetadata(ResourceConfig.builder()
                                      .path(temporaryDirectory.resolve("metadata.json"))
                                      .buildPrototype())
                .build();
        assertThat(reloadableMetadata.tenantLoadingLazy(), is(true));

        TenantConfig reloadableSigningJwk = baseBuilder()
                .signJwk(ResourceConfig.builder()
                                 .path(temporaryDirectory.resolve("keys.json"))
                                 .buildPrototype())
                .build();
        assertThat(reloadableSigningJwk.tenantLoadingLazy(), is(true));
    }

    @Test
    void rejectsInvalidDynamicResourceUrisAtStartup() {
        ResourceConfig relative = ResourceConfig.builder()
                .uri(URI.create("relative/jwk.json"))
                .buildPrototype();
        ResourceConfig unsupported = ResourceConfig.builder()
                .uri(URI.create("urn:example:jwk"))
                .buildPrototype();

        assertThrows(IllegalArgumentException.class, () -> baseBuilder().signJwk(relative));
        assertThrows(IllegalArgumentException.class, () -> baseBuilder().oidcMetadata(relative));
        assertThrows(IllegalArgumentException.class, () -> baseBuilder().signJwk(unsupported));
        assertThrows(IllegalArgumentException.class, () -> baseBuilder().oidcMetadata(unsupported));
    }

    @Test
    void rejectsRelativeEndpointInFixedMetadataAtStartup() {
        ResourceConfig metadata = ResourceConfig.builder()
                .contentPlain("{\"token_endpoint\":\"/relative/token\"}")
                .buildPrototype();

        assertThrows(Errors.ErrorMessagesException.class,
                     () -> baseBuilder()
                             .oidcMetadata(metadata)
                             .signJwk(fixedJwkResource())
                             .build());
    }

    @Test
    void rejectsUnsupportedEndpointSchemesAtStartup() {
        ResourceConfig metadata = ResourceConfig.builder()
                .contentPlain("{\"token_endpoint\":\"ftp://identity.example/token\"}")
                .buildPrototype();

        assertThrows(Errors.ErrorMessagesException.class,
                     () -> baseBuilder()
                             .oidcMetadata(metadata)
                             .signJwk(fixedJwkResource())
                             .build());
        assertThrows(Errors.ErrorMessagesException.class,
                     () -> baseBuilder()
                             .identityUri(URI.create("file:/identity"))
                             .signJwk(fixedJwkResource())
                             .build());
        assertThrows(Errors.ErrorMessagesException.class,
                     () -> baseBuilder()
                             .authorizationEndpointUri(URI.create("ftp://identity.example/authorize"))
                             .signJwk(fixedJwkResource())
                             .build());
    }

    @Test
    void idcsMetadataWithoutIdentityReportsConfigurationError() {
        String metadata = "{\"jwks_uri\":\"https://identity.example/jwks\"}";
        Errors.ErrorMessagesException exception = assertThrows(Errors.ErrorMessagesException.class,
                                                               () -> OidcConfig.builder()
                                                                       .clientId("client")
                                                                       .clientSecret("secret")
                                                                       .serverType("idcs")
                                                                       .oidcMetadata(ResourceConfig.builder()
                                                                                             .contentPlain(metadata)
                                                                                             .buildPrototype())
                                                                       .build());

        assertThat(exception.getMessage(), containsString("Identity URI"));
    }

    @Test
    void relativeEndpointInReloadableMetadataCanRecover() throws IOException {
        Path metadataPath = temporaryDirectory.resolve("metadata.json");
        Files.writeString(metadataPath, "{\"token_endpoint\":\"/relative/token\"}");
        OidcConfig config = baseBuilder()
                .oidcMetadata(ResourceConfig.builder().path(metadataPath).buildPrototype())
                .signJwk(fixedJwkResource())
                .jwkRetry(singleCallRetry())
                .jwkCircuitBreaker(twoFailureCircuitBreaker())
                .build();

        assertThrows(ResilientValue.UnavailableException.class, config::signJwk);

        Files.writeString(metadataPath, "{}");

        assertThat(config.signJwk().keys().size(), is(1));
    }

    @Test
    void unsupportedEndpointInReloadableMetadataCanRecover() throws IOException {
        Path metadataPath = temporaryDirectory.resolve("metadata.json");
        Files.writeString(metadataPath, "{\"token_endpoint\":\"ftp://identity.example/token\"}");
        OidcConfig config = baseBuilder()
                .oidcMetadata(ResourceConfig.builder().path(metadataPath).buildPrototype())
                .signJwk(fixedJwkResource())
                .jwkRetry(singleCallRetry())
                .jwkCircuitBreaker(twoFailureCircuitBreaker())
                .build();

        assertThrows(ResilientValue.UnavailableException.class, config::signJwk);

        Files.writeString(metadataPath, "{}");

        assertThat(config.signJwk().keys().size(), is(1));
    }

    @Test
    void wrongTypedEndpointInReloadableMetadataCanRecover() throws IOException {
        Path metadataPath = temporaryDirectory.resolve("metadata.json");
        Files.writeString(metadataPath, "{\"token_endpoint\":1}");
        OidcConfig config = reloadableMetadataConfig(metadataPath);

        assertThrows(ResilientValue.UnavailableException.class, config::signJwk);

        Files.writeString(metadataPath, "{}");

        assertThat(config.signJwk().keys().size(), is(1));
    }

    @Test
    void wrongTypedIssuerInReloadableMetadataCanRecover() throws IOException {
        Path metadataPath = temporaryDirectory.resolve("metadata.json");
        Files.writeString(metadataPath, "{\"issuer\":1}");
        OidcConfig config = reloadableMetadataConfig(metadataPath);

        assertThrows(ResilientValue.UnavailableException.class, config::signJwk);

        Files.writeString(metadataPath, "{}");

        assertThat(config.signJwk().keys().size(), is(1));
    }

    @Test
    void fileResourceRecoversAfterItAppears() throws IOException {
        Path keyPath = temporaryDirectory.resolve("keys.json");
        OidcConfig config = baseBuilder()
                .signJwk(ResourceConfig.builder().path(keyPath).buildPrototype())
                .jwkRetry(singleCallRetry())
                .jwkCircuitBreaker(twoFailureCircuitBreaker())
                .build();

        assertThrows(ResilientValue.UnavailableException.class, config::signJwk);

        Files.writeString(keyPath, JWK_JSON);

        assertThat(config.signJwk().keys().size(), is(1));
    }

    @Test
    void metadataFileRecoversAfterItAppears() throws IOException {
        Path metadataPath = temporaryDirectory.resolve("metadata.json");
        OidcConfig config = baseBuilder()
                .oidcMetadata(ResourceConfig.builder().path(metadataPath).buildPrototype())
                .signJwk(ResourceConfig.builder().contentPlain(JWK_JSON).buildPrototype())
                .jwkRetry(singleCallRetry())
                .jwkCircuitBreaker(twoFailureCircuitBreaker())
                .build();

        assertThrows(ResilientValue.UnavailableException.class, config::signJwk);

        Files.writeString(metadataPath, "{}");

        assertThat(config.signJwk().keys().size(), is(1));
    }

    @Test
    void missingDiscoveredJwkUriCanRecover() {
        AtomicInteger metadataRequests = new AtomicInteger();
        AtomicInteger jwkRequests = new AtomicInteger();
        AtomicInteger serverPort = new AtomicInteger();
        WebServer server = WebServer.builder()
                .routing(routing -> routing
                        .get("/identity/.well-known/openid-configuration", (req, res) -> {
                            int request = metadataRequests.incrementAndGet();
                            res.header(HeaderValues.CONTENT_TYPE_JSON);
                            if (request == 1) {
                                res.send("{}");
                            } else {
                                res.send("{\"jwks_uri\":\"http://localhost:" + serverPort.get() + "/jwks\"}");
                            }
                        })
                        .get("/jwks", (req, res) -> {
                            jwkRequests.incrementAndGet();
                            res.header(HeaderValues.CONTENT_TYPE_JSON).send(JWK_JSON);
                        }))
                .build()
                .start();
        serverPort.set(server.port());
        try {
            OidcConfig config = remoteBuilder(server)
                    .jwkRetry(singleCallRetry())
                    .jwkCircuitBreaker(twoFailureCircuitBreaker())
                    .build();

            assertThrows(ResilientValue.UnavailableException.class, config::signJwk);
            assertThat(config.signJwk().keys().size(), is(1));
            assertThat(metadataRequests.get(), is(2));
            assertThat(jwkRequests.get(), is(1));
        } finally {
            server.stop();
        }
    }

    @Test
    void wellKnownMetadataUsesJwkReadTimeout() {
        AtomicInteger metadataRequests = new AtomicInteger();
        AtomicInteger jwkRequests = new AtomicInteger();
        AtomicInteger serverPort = new AtomicInteger();
        WebServer server = WebServer.builder()
                .routing(routing -> routing
                        .get("/identity/.well-known/openid-configuration", (req, res) -> {
                            metadataRequests.incrementAndGet();
                            delayResponse();
                            res.header(HeaderValues.CONTENT_TYPE_JSON)
                                    .send("{\"jwks_uri\":\"http://localhost:" + serverPort.get() + "/jwks\"}");
                        })
                        .get("/jwks", (req, res) -> {
                            jwkRequests.incrementAndGet();
                            res.header(HeaderValues.CONTENT_TYPE_JSON).send(JWK_JSON);
                        }))
                .build()
                .start();
        serverPort.set(server.port());
        try {
            OidcConfig config = remoteBuilder(server)
                    .jwkRetry(singleCallRetry())
                    .jwkTimeout(shortJwkTimeout())
                    .jwkCircuitBreaker(twoFailureCircuitBreaker())
                    .build();

            long started = System.nanoTime();
            assertThrows(ResilientValue.UnavailableException.class, config::signJwk);
            assertThat(Duration.ofNanos(System.nanoTime() - started), lessThan(Duration.ofMillis(750)));
            assertThat(metadataRequests.get(), is(1));
            assertThat(jwkRequests.get(), is(0));
        } finally {
            server.stop();
        }
    }

    @Test
    void discoveredJwkUsesJwkReadTimeout() {
        AtomicInteger metadataRequests = new AtomicInteger();
        AtomicInteger jwkRequests = new AtomicInteger();
        AtomicInteger serverPort = new AtomicInteger();
        WebServer server = WebServer.builder()
                .routing(routing -> routing
                        .get("/identity/.well-known/openid-configuration", (req, res) -> {
                            metadataRequests.incrementAndGet();
                            res.header(HeaderValues.CONTENT_TYPE_JSON)
                                    .send("{\"jwks_uri\":\"http://localhost:" + serverPort.get() + "/jwks\"}");
                        })
                        .get("/jwks", (req, res) -> {
                            jwkRequests.incrementAndGet();
                            delayResponse();
                            res.header(HeaderValues.CONTENT_TYPE_JSON).send(JWK_JSON);
                        }))
                .build()
                .start();
        serverPort.set(server.port());
        try {
            OidcConfig config = remoteBuilder(server)
                    .jwkRetry(singleCallRetry())
                    .jwkTimeout(shortJwkTimeout())
                    .jwkCircuitBreaker(twoFailureCircuitBreaker())
                    .build();

            long started = System.nanoTime();
            assertThrows(ResilientValue.UnavailableException.class, config::signJwk);
            assertThat(Duration.ofNanos(System.nanoTime() - started), lessThan(Duration.ofMillis(750)));
            assertThat(metadataRequests.get(), is(1));
            assertThat(jwkRequests.get(), is(1));
        } finally {
            server.stop();
        }
    }

    @Test
    void idcsReloadObtainsFreshToken() {
        AtomicInteger tokenRequests = new AtomicInteger();
        AtomicInteger serverPort = new AtomicInteger();
        AtomicReference<String> jwkAuthorization = new AtomicReference<>();
        WebServer server = WebServer.builder()
                .routing(routing -> routing
                        .get("/identity/.well-known/openid-configuration", (req, res) -> {
                            res.header(HeaderValues.CONTENT_TYPE_JSON)
                                    .send("{\"token_endpoint\":\"http://localhost:" + serverPort.get() + "/token\","
                                                  + "\"jwks_uri\":\"http://localhost:" + serverPort.get() + "/jwks\"}");
                        })
                        .post("/token", (req, res) -> {
                            if (tokenRequests.incrementAndGet() == 1) {
                                res.status(Status.SERVICE_UNAVAILABLE_503).send();
                            } else {
                                res.header(HeaderValues.CONTENT_TYPE_JSON).send("{\"access_token\":\"fresh-token\"}");
                            }
                        })
                        .get("/jwks", (req, res) -> {
                            jwkAuthorization.set(req.headers().get(HeaderNames.AUTHORIZATION).get());
                            res.header(HeaderValues.CONTENT_TYPE_JSON).send(JWK_JSON);
                        }))
                .build()
                .start();
        serverPort.set(server.port());
        try {
            OidcConfig config = remoteBuilder(server)
                    .serverType("idcs")
                    .tokenEndpointAuthentication(OidcConfig.ClientAuthentication.NONE)
                    .jwkRetry(singleCallRetry())
                    .jwkCircuitBreaker(twoFailureCircuitBreaker())
                    .build();

            assertThrows(ResilientValue.UnavailableException.class, config::signJwk);
            assertThat(config.signJwk().keys().size(), is(1));
            assertThat(tokenRequests.get(), is(2));
            assertThat(jwkAuthorization.get(), is("Bearer fresh-token"));
        } finally {
            server.stop();
        }
    }

    private static OidcConfig.Builder baseBuilder() {
        return OidcConfig.builder()
                .identityUri(URI.create("https://identity.example"))
                .clientId("client")
                .clientSecret("secret")
                .oidcMetadataWellKnown(false)
                .cookieEncryptionPassword("test-password".toCharArray());
    }

    private static OidcConfig.Builder remoteBuilder(WebServer server) {
        return OidcConfig.builder()
                .identityUri(URI.create("http://localhost:" + server.port() + "/identity"))
                .clientId("client")
                .clientSecret("secret")
                .webclient(it -> it.clearServices().servicesDiscoverServices(false))
                .cookieEncryptionPassword("test-password".toCharArray());
    }

    private static Config config(Map<String, String> values) {
        return Config.builder()
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .sources(ConfigSources.create(values))
                .build();
    }

    private static RetryConfig singleCallRetry() {
        return RetryConfig.builder()
                .calls(1)
                .delay(Duration.ZERO)
                .overallTimeout(Duration.ofSeconds(5))
                .buildPrototype();
    }

    private static TimeoutConfig shortJwkTimeout() {
        return TimeoutConfig.builder()
                .timeout(Duration.ofMillis(100))
                .currentThread(true)
                .buildPrototype();
    }

    private static CircuitBreakerConfig twoFailureCircuitBreaker() {
        return CircuitBreakerConfig.builder()
                .volume(2)
                .errorRatio(100)
                .successThreshold(1)
                .delay(Duration.ofSeconds(1))
                .buildPrototype();
    }

    private static void delayResponse() {
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while delaying test response", e);
        }
    }

    private static ResourceConfig fixedJwkResource() {
        return ResourceConfig.builder()
                .contentPlain(JWK_JSON)
                .buildPrototype();
    }

    private static OidcConfig reloadableMetadataConfig(Path metadataPath) {
        return baseBuilder()
                .oidcMetadata(ResourceConfig.builder().path(metadataPath).buildPrototype())
                .signJwk(fixedJwkResource())
                .jwkRetry(singleCallRetry())
                .jwkCircuitBreaker(twoFailureCircuitBreaker())
                .build();
    }

}
