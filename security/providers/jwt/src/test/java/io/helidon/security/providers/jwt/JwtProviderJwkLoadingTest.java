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

package io.helidon.security.providers.jwt;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import io.helidon.common.configurable.Resource;
import io.helidon.common.configurable.ResourceConfig;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.faulttolerance.CircuitBreakerConfig;
import io.helidon.faulttolerance.ResilientValue;
import io.helidon.faulttolerance.RetryConfig;
import io.helidon.faulttolerance.TimeoutConfig;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.JwtException;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.jwt.jwk.JwkRSA;
import io.helidon.security.util.TokenHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtProviderJwkLoadingTest {
    private static final JwkKeys SIGN_KEYS = JwkKeys.builder()
            .resource(Resource.create("sign-jwk.json"))
            .build();

    @TempDir
    Path tempDir;

    @Test
    void missingDynamicPathDoesNotPreventStartup() {
        Path keysPath = tempDir.resolve("missing-jwk.json");
        JwtProvider provider = provider(keysPath, false, defaultCircuitBreaker());

        AuthenticationResponse response = provider.authenticate(request(validToken()));

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.description().orElseThrow(),
                   is("JWT verification keys are temporarily unavailable"));
        assertThat(response.responseHeaders().get("WWW-Authenticate"),
                   is(List.of("Bearer")));
    }

    @Test
    void customTokenHandlerUsesServiceUnavailableForMissingDynamicPath() {
        Path keysPath = tempDir.resolve("missing-jwk.json");
        JwtProvider provider = providerBuilder(keysPath, false, defaultCircuitBreaker())
                .atnTokenHandler(TokenHandler.forHeader("X-JWT"))
                .build();

        AuthenticationResponse response = provider.authenticate(request("X-JWT", validToken()));

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElseThrow(), is(503));
        assertThat(response.responseHeaders().isEmpty(), is(true));
    }

    @Test
    void optionalProviderAbstainsWhenDynamicKeysAreUnavailable() {
        Path keysPath = tempDir.resolve("missing-jwk.json");
        JwtProvider provider = provider(keysPath, true, defaultCircuitBreaker());

        AuthenticationResponse response = provider.authenticate(request(validToken()));

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.ABSTAIN));
        assertThat(response.responseHeaders().isEmpty(), is(true));
    }

    @Test
    void dynamicKeysRecoverWhenFileAppears() throws IOException {
        Path keysPath = tempDir.resolve("verify-jwk.json");
        JwtProvider provider = provider(keysPath, false, twoFailureCircuitBreaker());

        assertThat(provider.authenticate(request(validToken())).status(),
                   is(SecurityResponse.SecurityStatus.FAILURE));

        writeVerificationKeys(keysPath);

        assertThat(provider.authenticate(request(validToken())).status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void malformedTokenDoesNotAttemptDynamicLoad() throws IOException {
        Path keysPath = tempDir.resolve("verify-jwk.json");
        JwtProvider provider = provider(keysPath, false, defaultCircuitBreaker());

        assertThat(provider.authenticate(request("not-a-jwt")).status(),
                   is(SecurityResponse.SecurityStatus.FAILURE));

        writeVerificationKeys(keysPath);

        assertThat(provider.authenticate(request(validToken())).status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void invalidTypedClaimsDoNotEscapeOrAttemptDynamicLoad() throws IOException {
        Path keysPath = tempDir.resolve("verify-jwk.json");
        JwtProvider required = provider(keysPath, false, defaultCircuitBreaker());
        JwtProvider optional = provider(keysPath, true, defaultCircuitBreaker());

        AuthenticationResponse requiredResponse = required.authenticate(request(invalidTypedClaimToken()));
        AuthenticationResponse optionalResponse = optional.authenticate(request(invalidTypedClaimToken()));

        assertThat(requiredResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(optionalResponse.status(), is(SecurityResponse.SecurityStatus.ABSTAIN));
        writeVerificationKeys(keysPath);
        assertThat(required.authenticate(request(validToken())).status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void signedTokenWithoutKeyIdDoesNotAttemptDynamicLoad() throws IOException {
        Path keysPath = tempDir.resolve("verify-jwk.json");
        JwtProvider provider = provider(keysPath, false, defaultCircuitBreaker());

        AuthenticationResponse response = provider.authenticate(request(signedTokenWithoutKeyId()));

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.description().orElseThrow(), containsString("no kid is defined"));
        writeVerificationKeys(keysPath);
        assertThat(provider.authenticate(request(validToken())).status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void unsignedTokensDoNotRequireVerificationKeys() {
        JwtProvider withoutKeys = JwtProvider.builder()
                .allowUnsigned(true)
                .build();
        JwtProvider withEmptyKeys = JwtProvider.builder()
                .verifyJwk(JwkKeys.builder().build())
                .allowUnsigned(true)
                .build();

        assertThat(withoutKeys.authenticate(request(unsignedToken(null))).status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(withEmptyKeys.authenticate(request(unsignedToken(null))).status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void unsignedTokensDoNotLoadDynamicVerificationKeys() {
        Path keysPath = tempDir.resolve("missing-jwk.json");
        JwtProvider provider = providerBuilder(keysPath, false, defaultCircuitBreaker())
                .allowUnsigned(true)
                .build();

        assertThat(provider.authenticate(request(unsignedToken(null))).status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void allowUnsignedDoesNotPermitOtherTokensWithoutKeys() {
        JwtProvider provider = JwtProvider.builder()
                .allowUnsigned(true)
                .build();

        assertThat(provider.authenticate(request(validToken())).status(),
                   is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(provider.authenticate(request(unsignedToken("unexpected"))).status(),
                   is(SecurityResponse.SecurityStatus.FAILURE));
    }

    @Test
    void reusedBuilderCreatesIndependentDynamicLoader() throws IOException {
        Path keysPath = tempDir.resolve("verify-jwk.json");
        JwtProvider.Builder builder = providerBuilder(keysPath, false, defaultCircuitBreaker());
        JwtProvider firstProvider = builder.build();

        assertThat(firstProvider.authenticate(request(validToken())).status(),
                   is(SecurityResponse.SecurityStatus.FAILURE));
        writeVerificationKeys(keysPath);

        JwtProvider secondProvider = builder.build();
        assertThat(secondProvider.authenticate(request(validToken())).status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void unusedFaultToleranceSuppliersAreLazy() {
        AtomicInteger creations = new AtomicInteger();
        JwtProvider.builder()
                .authenticate(false)
                .verifyJwk(ResourceConfig.builder()
                                   .path(tempDir.resolve("missing.json"))
                                   .buildPrototype())
                .jwkRetry(() -> {
                    creations.incrementAndGet();
                    return RetryConfig.builder(oneCallRetry()).build();
                })
                .jwkCircuitBreaker(() -> {
                    creations.incrementAndGet();
                    return CircuitBreakerConfig.builder(defaultCircuitBreaker()).build();
                })
                .jwkTimeout(() -> {
                    creations.incrementAndGet();
                    return TimeoutConfig.builder(testTimeout()).build();
                })
                .build();

        assertThat(creations.get(), is(0));
    }

    @Test
    void dynamicFaultToleranceSuppliersAreCreatedOnce() {
        AtomicInteger creations = new AtomicInteger();
        JwtProvider.builder()
                .verifyJwk(ResourceConfig.builder()
                                   .path(tempDir.resolve("missing.json"))
                                   .buildPrototype())
                .jwkRetry(() -> {
                    creations.incrementAndGet();
                    return RetryConfig.builder(oneCallRetry()).build();
                })
                .jwkCircuitBreaker(() -> {
                    creations.incrementAndGet();
                    return CircuitBreakerConfig.builder(defaultCircuitBreaker()).build();
                })
                .jwkTimeout(() -> {
                    creations.incrementAndGet();
                    return TimeoutConfig.builder(testTimeout()).build();
                })
                .build();

        assertThat(creations.get(), is(3));
    }

    @Test
    void fixedSourcesFailFast() {
        assertThrows(JwtException.class,
                     () -> JwtProvider.builder().verifyJwk(JwkKeys.builder().build()).build());
        assertThrows(RuntimeException.class,
                     () -> JwtProvider.builder()
                             .verifyJwk(ResourceConfig.builder()
                                                .contentPlain("not-json")
                                                .buildPrototype())
                             .build());
        assertThrows(RuntimeException.class,
                     () -> JwtProvider.builder()
                             .verifyJwk(ResourceConfig.builder()
                                                .resourcePath("does-not-exist.json")
                                                .buildPrototype())
                             .build());
        assertThrows(RuntimeException.class,
                     () -> JwtProvider.builder()
                             .authenticate(false)
                             .verifyJwk(ResourceConfig.builder()
                                                .contentPlain("not-json")
                                                .buildPrototype())
                             .build());
    }

    @Test
    void disabledAuthenticationOrSignatureDoesNotRequireKeys() {
        JwtProvider.builder()
                .authenticate(false)
                .build();
        JwtProvider.builder()
                .authenticate(false)
                .verifyJwk(ResourceConfig.builder()
                                   .path(tempDir.resolve("missing.json"))
                                   .buildPrototype())
                .build();

        JwtProvider.Builder builder = JwtProvider.builder()
                .verifySignature(false)
                .expectedIssuer("jwt.example.com");
        builder.expectedAudience("audience.application.id");
        builder.build();
    }

    @Test
    void resourceMustHaveExactlyOneSelector() {
        assertThrows(JwtException.class,
                     () -> JwtProvider.builder()
                             .verifyJwk(ResourceConfig.builder().buildPrototype())
                             .build());
        assertThrows(JwtException.class,
                     () -> JwtProvider.builder()
                             .verifyJwk(ResourceConfig.builder()
                                                .path(tempDir.resolve("missing.json"))
                                                .contentPlain("{\"keys\":[]}")
                                                .buildPrototype())
                             .build());
    }

    @Test
    void dynamicUriMustBeAbsoluteAndSupportedByResource() {
        assertThrows(JwtException.class,
                     () -> JwtProvider.builder()
                             .verifyJwk(ResourceConfig.builder()
                                                .uri(URI.create("relative/jwk.json"))
                                                .buildPrototype())
                             .build());
        assertThrows(JwtException.class,
                     () -> JwtProvider.builder()
                             .verifyJwk(ResourceConfig.builder()
                                                .uri(URI.create("urn:example:jwk"))
                                                .buildPrototype())
                             .build());
        assertThrows(JwtException.class,
                     () -> JwtProvider.builder()
                             .verifyJwk(ResourceConfig.builder()
                                                .uri(URI.create("https:/jwks"))
                                                .buildPrototype())
                             .build());
        assertThrows(JwtException.class,
                     () -> JwtProvider.builder()
                             .verifyJwk(ResourceConfig.create(Config.just("""
                                                                                  uri: "https://example.invalid/jwks"
                                                                                  proxy-host: " "
                                                                                  """,
                                                                          MediaTypes.APPLICATION_YAML)))
                             .build());

        JwtProvider.builder()
                .authenticate(false)
                .verifyJwk(ResourceConfig.builder()
                                   .uri(tempDir.resolve("missing.json").toUri())
                                   .buildPrototype())
                .build();
        JwtProvider.builder()
                .authenticate(false)
                .verifyJwk(ResourceConfig.builder()
                                   .uri(URI.create("https://example.invalid/jwks?credential=sensitive"))
                                   .buildPrototype())
                .build();
    }

    @Test
    void invalidFaultToleranceConfigurationFailsAtStartup() {
        String configText = """
                atn-token:
                  jwk:
                    resource:
                      path: '%s'
                jwk-loader:
                  retry:
                    calls: 0
                """.formatted(tempDir.resolve("missing.json").toString().replace("'", "''"));

        assertThrows(IllegalArgumentException.class,
                     () -> JwtProvider.create(Config.just(configText, MediaTypes.APPLICATION_YAML)));
    }

    @Test
    void faultToleranceConfigurationIsAppliedFromConfig() throws IOException {
        Path keysPath = tempDir.resolve("missing-jwk.json");
        String configText = """
                atn-token:
                  jwk:
                    resource:
                      path: '%s'
                jwk-loader:
                  retry:
                    calls: 1
                    delay: "PT0S"
                    overall-timeout: "PT1S"
                  timeout:
                    timeout: "PT1S"
                  circuit-breaker:
                    volume: 2
                    error-ratio: 100
                    success-threshold: 1
                    delay: "PT30S"
                """.formatted(keysPath.toString().replace("'", "''"));

        JwtProvider provider = JwtProvider.create(Config.just(configText, MediaTypes.APPLICATION_YAML));

        assertThat(provider.authenticate(request(validToken())).status(),
                   is(SecurityResponse.SecurityStatus.FAILURE));
        writeVerificationKeys(keysPath);
        assertThat(provider.authenticate(request(validToken())).status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void partialConfigPreservesProgrammaticFaultToleranceValues() throws IOException {
        Path keysPath = tempDir.resolve("missing-jwk.json");
        String configText = """
                atn-token:
                  jwk:
                    resource:
                      path: '%s'
                jwk-loader:
                  retry:
                    calls: 1
                    delay: "PT0S"
                    overall-timeout: "PT1S"
                  timeout:
                    timeout: "PT1S"
                """.formatted(keysPath.toString().replace("'", "''"));

        JwtProvider provider = JwtProvider.builder()
                .jwkCircuitBreaker(twoFailureCircuitBreaker())
                .config(Config.just(configText, MediaTypes.APPLICATION_YAML))
                .build();

        assertThat(provider.authenticate(request(validToken())).status(),
                   is(SecurityResponse.SecurityStatus.FAILURE));
        writeVerificationKeys(keysPath);
        assertThat(provider.authenticate(request(validToken())).status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void generatedMetadataContainsFaultToleranceOptions() throws IOException {
        String metadata;
        try (var stream = JwtProvider.class.getResourceAsStream("/META-INF/helidon/config-metadata.json")) {
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
    void rejectsInconsistentJwkTimeoutAtStartup() {
        ResourceConfig reloadableJwk = ResourceConfig.builder()
                .path(tempDir.resolve("missing.json"))
                .buildPrototype();
        assertThrows(IllegalArgumentException.class,
                     () -> JwtProvider.builder()
                             .verifyJwk(reloadableJwk)
                             .jwkTimeout(TimeoutConfig.builder().timeout(Duration.ZERO).buildPrototype())
                             .build());
        assertThrows(IllegalArgumentException.class,
                     () -> JwtProvider.builder()
                             .verifyJwk(reloadableJwk)
                             .jwkRetry(RetryConfig.builder().overallTimeout(Duration.ofSeconds(4)).buildPrototype())
                             .build());
        assertThrows(IllegalArgumentException.class,
                     () -> JwtProvider.builder()
                             .verifyJwk(reloadableJwk)
                             .jwkTimeout(TimeoutConfig.builder()
                                                 .timeout(Duration.ofSeconds(1))
                                                 .currentThread(false)
                                                 .buildPrototype())
                             .build());
    }

    @Test
    void consumerTimeoutUsesJwkDefaults() {
        ResourceConfig reloadableJwk = ResourceConfig.builder()
                .path(tempDir.resolve("missing.json"))
                .buildPrototype();

        JwtProvider provider = JwtProvider.builder()
                .verifyJwk(reloadableJwk)
                .jwkTimeout(it -> it.timeout(Duration.ofSeconds(1)))
                .build();

        assertThat(provider, is(notNullValue()));
    }

    @Test
    void warningsDescribeSafeFailureCategories() throws IOException {
        Logger logger = Logger.getLogger(ResilientValue.class.getName());
        CapturingHandler handler = new CapturingHandler();
        Level originalLevel = logger.getLevel();
        boolean originalUseParentHandlers = logger.getUseParentHandlers();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);

        Path missingPath = tempDir.resolve("secret-location.json");
        Path malformedPath = tempDir.resolve("malformed.json");
        Path emptyPath = tempDir.resolve("empty.json");
        try {
            provider(missingPath, false, defaultCircuitBreaker()).authenticate(request(validToken()));
            Files.writeString(malformedPath, "not-json");
            provider(malformedPath, false, defaultCircuitBreaker()).authenticate(request(validToken()));
            Files.writeString(emptyPath, "{\"keys\":[]}");
            provider(emptyPath, false, defaultCircuitBreaker()).authenticate(request(validToken()));

            String messages = String.join("\n", handler.warningMessages());
            assertThat(messages, containsString("filesystem source could not be read"));
            assertThat(messages, containsString("filesystem source does not contain valid JSON"));
            assertThat(messages, containsString("filesystem source does not contain usable verification keys"));
            assertThat(messages, not(containsString(tempDir.toString())));
            assertThat("warning must not attach raw causes", handler.hasThrown(), is(false));
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(originalUseParentHandlers);
            logger.setLevel(originalLevel);
        }
    }

    private static JwtProvider provider(Path keysPath,
                                        boolean optional,
                                        CircuitBreakerConfig circuitBreakerConfig) {
        return providerBuilder(keysPath, optional, circuitBreakerConfig).build();
    }

    private static JwtProvider.Builder providerBuilder(Path keysPath,
                                                       boolean optional,
                                                       CircuitBreakerConfig circuitBreakerConfig) {
        return JwtProvider.builder()
                .verifyJwk(ResourceConfig.builder()
                                   .path(keysPath)
                                   .buildPrototype())
                .jwkRetry(oneCallRetry())
                .jwkTimeout(TimeoutConfig.builder()
                                    .timeout(Duration.ofSeconds(1))
                                    .currentThread(true)
                                    .buildPrototype())
                .jwkCircuitBreaker(circuitBreakerConfig)
                .optional(optional);
    }

    private static RetryConfig oneCallRetry() {
        return RetryConfig.builder()
                .calls(1)
                .delay(Duration.ZERO)
                .overallTimeout(Duration.ofSeconds(1))
                .buildPrototype();
    }

    private static CircuitBreakerConfig defaultCircuitBreaker() {
        return CircuitBreakerConfig.builder()
                .volume(1)
                .errorRatio(100)
                .successThreshold(1)
                .delay(Duration.ofSeconds(30))
                .buildPrototype();
    }

    private static CircuitBreakerConfig twoFailureCircuitBreaker() {
        return CircuitBreakerConfig.builder()
                .volume(2)
                .errorRatio(100)
                .successThreshold(1)
                .delay(Duration.ofSeconds(30))
                .buildPrototype();
    }

    private static TimeoutConfig testTimeout() {
        return TimeoutConfig.builder()
                .timeout(Duration.ofSeconds(1))
                .currentThread(true)
                .buildPrototype();
    }

    private static String validToken() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.builder()
                .subject("user-id")
                .preferredUsername("user")
                .issuer("jwt.example.com")
                .algorithm(JwkRSA.ALG_RS256)
                .keyId("verify-rsa")
                .issueTime(now)
                .expirationTime(now.plus(1, ChronoUnit.HOURS))
                .addAudience("audience.application.id")
                .build();
        return SignedJwt.sign(jwt, SIGN_KEYS.forKeyId("sign-rsa").orElseThrow()).tokenContent();
    }

    private static String signedTokenWithoutKeyId() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.builder()
                .subject("user-id")
                .preferredUsername("user")
                .issuer("jwt.example.com")
                .algorithm(JwkRSA.ALG_RS256)
                .issueTime(now)
                .expirationTime(now.plus(1, ChronoUnit.HOURS))
                .addAudience("audience.application.id")
                .build();
        return SignedJwt.sign(jwt, SIGN_KEYS.forKeyId("sign-rsa").orElseThrow()).tokenContent();
    }

    private static String invalidTypedClaimToken() {
        return "eyJhbGciOiJSUzI1NiIsImtpZCI6InZlcmlmeS1yc2EifQ.eyJzdWIiOiJqb2UiLCJleHAiOiJvb3BzIn0.AA";
    }

    private static String unsignedToken(String keyId) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.builder()
                .subject("user-id")
                .preferredUsername("user")
                .algorithm(Jwk.ALG_NONE)
                .issueTime(now)
                .expirationTime(now.plus(1, ChronoUnit.HOURS));
        if (keyId != null) {
            builder.keyId(keyId);
        }
        return SignedJwt.sign(builder.build(), Jwk.NONE_JWK).tokenContent();
    }

    private static ProviderRequest request(String token) {
        return request("Authorization", "bearer " + token);
    }

    private static ProviderRequest request(String header, String token) {
        ProviderRequest request = mock(ProviderRequest.class);
        SecurityEnvironment environment = SecurityEnvironment.builder()
                .header(header, token)
                .build();
        when(request.env()).thenReturn(environment);
        return request;
    }

    private static void writeVerificationKeys(Path path) throws IOException {
        Files.write(path, Resource.create("verify-jwk.json").bytes());
    }

    private static final class CapturingHandler extends Handler {
        private final Formatter formatter = new SimpleFormatter();
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            records.clear();
        }

        private List<String> warningMessages() {
            return records.stream()
                    .filter(record -> record.getLevel().equals(Level.WARNING))
                    .map(formatter::formatMessage)
                    .toList();
        }

        private boolean hasThrown() {
            return records.stream().anyMatch(record -> record.getThrown() != null);
        }
    }
}
