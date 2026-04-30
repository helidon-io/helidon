/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

package io.helidon.security.providers.httpsign;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.SubjectType;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link HttpSignProvider} configured through a builder.
 */
class CurrentHttpSignProviderBuilderTest extends CurrentHttpSignProviderTest {
    private static HttpSignProvider instance;

    @BeforeAll
    static void initClass() {
        instance = HttpSignProvider.builder()
                .addAcceptHeader(HttpSignHeader.AUTHORIZATION)
                .addAcceptHeader(HttpSignHeader.SIGNATURE)
                .optional(true)
                .realm("prime")
                .inboundRequiredHeaders(inboundRequiredHeaders(SignedHeadersConfig
                                                                       .REQUEST_TARGET, "host"))
                .addInbound(hmacInbound())
                .addInbound(rsaInbound())
                .outbound(OutboundConfig.builder()
                                  .addTarget(rsaOutbound())
                                  .addTarget(hmacOutbound())
                                  .build())
                .build();
    }

    @Test
    void testDefaultInboundRequiredHeadersRejectDateOnlySignature() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(inboundTime())
                .path("/my/resource")
                .headers(headers)
                .build();
        SignedHeadersConfig dateOnly = SignedHeadersConfig.builder()
                .defaultConfig(SignedHeadersConfig.HeadersConfig.create(List.of("date")))
                .build();
        OutboundTargetDefinition outboundDefinition = OutboundTargetDefinition.builder("myServiceKeyId")
                .hmacSecret("MyPasswordForHmac")
                .signedHeaders(dateOnly)
                .build();
        headers.put("Signature",
                    List.of(HttpSignature.sign(signingEnv, outboundDefinition, new HashMap<>(), false)
                                    .toSignatureHeader()));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = HttpSignProvider.builder()
                .addInbound(hmacInbound())
                .build()
                .authenticate(request);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("Header (request-target) is required"));
    }

    @Test
    void testDefaultInboundRequiredHeadersAcceptGetHeadDeleteAndFallbackMethods() {
        for (String method : List.of("GET", "HEAD", "DELETE", "PATCH")) {
            Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            headers.put("host", List.of("example.org"));
            headers.put("date", List.of(INBOUND_DATE));

            AuthenticationResponse atnResponse = authenticateWithDefaultProvider(method,
                                                                                 headers,
                                                                                 signedHeadersNoAuthorization());

            assertThat(method + ": " + atnResponse.description().orElse("Unknown problem"),
                       atnResponse.status(),
                       is(SecurityResponse.SecurityStatus.SUCCESS));
        }
    }

    @Test
    void testDefaultInboundRequiredHeadersRejectMissingHostAfterRequestTargetIsSigned() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("date", List.of(INBOUND_DATE));
        SignedHeadersConfig dateAndTarget = SignedHeadersConfig.builder()
                .defaultConfig(SignedHeadersConfig.HeadersConfig
                                       .create(List.of("date", SignedHeadersConfig.REQUEST_TARGET)))
                .build();

        AuthenticationResponse atnResponse = authenticateWithDefaultProvider("GET", headers, dateAndTarget);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("Header host is required"));
    }

    @Test
    void testDefaultInboundRequiredHeadersRejectUnsignedAuthorizationIfPresent() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));
        headers.put("authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));

        AuthenticationResponse atnResponse = authenticateWithDefaultProvider("GET",
                                                                             headers,
                                                                             signedHeadersNoAuthorization());

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("Header authorization is required"));
    }

    @Test
    void testDefaultInboundRequiredHeadersAcceptAuthorizationCarrierWithoutSigningCarrierHeader() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));
        headers.put("Authorization",
                    List.of("Signature keyId=\"myServiceKeyId\",algorithm=\"hmac-sha256\","
                                    + "headers=\"date host (request-target)\","
                                    + "signature=\"+Ur0BxGJeEmXBBgHyH4aWZmsl4KGe5WQNu0iyzIPGIU=\""));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = HttpSignProvider.builder()
                .addInbound(hmacInbound())
                .build()
                .authenticate(request);

        assertThat(atnResponse.description().orElse("Unknown problem"),
                   atnResponse.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void testDefaultInboundRequiredHeadersAcceptUnsignedPostPutEntityHeaders() {
        for (String method : List.of("POST", "PUT")) {
            for (String header : List.of("content-length", "content-type", "digest")) {
                Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                headers.put("host", List.of("example.org"));
                headers.put("date", List.of(INBOUND_DATE));
                headers.put(header, List.of("content-length".equals(header) ? "123" : "value"));

                AuthenticationResponse atnResponse = authenticateWithDefaultProvider(method,
                                                                                     headers,
                                                                                     signedHeadersNoAuthorization());

                assertThat(method + " " + header,
                           atnResponse.status(),
                           is(SecurityResponse.SecurityStatus.SUCCESS));
            }
        }
    }

    @Test
    void testDefaultInboundRequiredHeadersAcceptPostPutWithoutEntityHeaders() {
        for (String method : List.of("POST", "PUT")) {
            Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            headers.put("host", List.of("example.org"));
            headers.put("date", List.of(INBOUND_DATE));

            AuthenticationResponse atnResponse = authenticateWithDefaultProvider(method,
                                                                                 headers,
                                                                                 signedHeadersNoAuthorization());

            assertThat(method + ": " + atnResponse.description().orElse("Unknown problem"),
                       atnResponse.status(),
                       is(SecurityResponse.SecurityStatus.SUCCESS));
        }
    }

    @Test
    void testInboundDateValidityCanBeDisabledFromBuilder() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(STALE_DATE));
        headers.put("authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(staleTime())
                .path("/my/resource")
                .headers(headers)
                .build();
        headers.put("Signature",
                    List.of(signatureHeader(signingEnv,
                                            OutboundTargetDefinition.builder("myServiceKeyId")
                                                    .hmacSecret("MyPasswordForHmac")
                                                    .signedHeaders(signedHeaders())
                                                    .build(),
                                            false)));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = HttpSignProvider.builder()
                .inboundDateValidity(Duration.ZERO)
                .addInbound(hmacInbound())
                .build()
                .authenticate(request);

        assertThat(atnResponse.description().orElse("Unknown problem"),
                   atnResponse.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void testInboundDateValidityCanBeExtendedFromBuilder() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(STALE_DATE));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(staleTime())
                .path("/my/resource")
                .headers(headers)
                .build();
        headers.put("Signature",
                    List.of(signatureHeader(signingEnv,
                                            OutboundTargetDefinition.builder("myServiceKeyId")
                                                    .hmacSecret("MyPasswordForHmac")
                                                    .signedHeaders(signedHeadersNoAuthorization())
                                                    .build(),
                                            false)));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = HttpSignProvider.builder()
                .inboundDateValidity(Duration.ofMinutes(10))
                .addInbound(hmacInbound())
                .build()
                .authenticate(request);

        assertThat(atnResponse.description().orElse("Unknown problem"),
                   atnResponse.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void testInboundDateValidityCanBeDisabledForAuthorizationHeader() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(STALE_DATE));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(staleTime())
                .path("/my/resource")
                .headers(headers)
                .build();
        String signatureHeader = signatureHeader(signingEnv,
                                                 OutboundTargetDefinition.builder("myServiceKeyId")
                                                         .hmacSecret("MyPasswordForHmac")
                                                         .signedHeaders(signedHeadersNoAuthorization())
                                                         .build(),
                                                 false);
        headers.put("Authorization", List.of("Signature " + signatureHeader));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = HttpSignProvider.builder()
                .addAcceptHeader(HttpSignHeader.AUTHORIZATION)
                .inboundDateValidity(Duration.ZERO)
                .addInbound(hmacInbound())
                .build()
                .authenticate(request);

        assertThat(atnResponse.description().orElse("Unknown problem"),
                   atnResponse.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    private static AuthenticationResponse authenticateWithDefaultProvider(String method,
                                                                          Map<String, List<String>> headers,
                                                                          SignedHeadersConfig signedHeaders) {
        SecurityEnvironment signingEnv = SecurityEnvironment.builder(inboundTime())
                .method(method)
                .path("/my/resource")
                .headers(headers)
                .build();
        headers.put("Signature",
                    List.of(signatureHeader(signingEnv,
                                            OutboundTargetDefinition.builder("myServiceKeyId")
                                                    .hmacSecret("MyPasswordForHmac")
                                                    .signedHeaders(signedHeaders)
                                                    .build(),
                                            false)));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .method(method)
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        return HttpSignProvider.builder()
                .addInbound(hmacInbound())
                .build()
                .authenticate(request);
    }

    private static OutboundTarget hmacOutbound() {
        return OutboundTarget.builder("second")
                .addTransport("http")
                .addHost("localhost")
                .addPath("/second/.*")
                .customObject(OutboundTargetDefinition.class,
                              OutboundTargetDefinition
                                      .builder("myServiceKeyId")
                                      .hmacSecret("MyPasswordForHmac")
                                      .build())
                .build();
    }

    private static OutboundTarget rsaOutbound() {
        return OutboundTarget.builder("first")
                .addTransport("http")
                .addHost("example.org")
                .addPath("/my/.*")
                .customObject(OutboundTargetDefinition.class, OutboundTargetDefinition
                        .builder("rsa-key-12345")
                        .signedHeaders(inboundRequiredHeaders("host", SignedHeadersConfig
                                .REQUEST_TARGET))
                        .privateKeyConfig(Keys.builder()
                                                  .keystore(keystore ->
                                                                    keystore.keystore(Resource.create(Paths.get(
                                                                                    "src/test/resources/keystore.p12")))
                                                                            .passphrase("password")
                                                                            .keyAlias("myPrivateKey"))
                                                  .build())
                        .build())
                .build();
    }

    private static InboundClientDefinition rsaInbound() {
        return InboundClientDefinition.builder("rsa-key-12345")
                .principalName("aUser")
                .subjectType(SubjectType.USER)
                .publicKeyConfig(Keys.builder()
                                         .keystore(keystore ->
                                                           keystore.keystore(Resource.create(Paths.get(
                                                                           "src/test/resources/keystore.p12")))
                                                                   .passphrase("password")
                                                                   .certAlias("service_cert"))
                                         .build())
                .build();
    }

    private static InboundClientDefinition hmacInbound() {
        return InboundClientDefinition.builder("myServiceKeyId")
                .principalName("aSetOfTrustedServices")
                .hmacSecret("MyPasswordForHmac")
                .build();
    }

    private static SignedHeadersConfig inboundRequiredHeaders(String requestTarget, String host) {
        return SignedHeadersConfig.builder()
                .defaultConfig(SignedHeadersConfig.HeadersConfig
                                       .create(List.of("date")))
                .config("get",
                        SignedHeadersConfig.HeadersConfig
                                .create(List.of("date",
                                                                 requestTarget,
                                                                 host),
                                        List.of("authorization")))
                .build();
    }

    @Override
    HttpSignProvider getProvider() {
        return instance;
    }
}
