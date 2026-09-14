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

import java.net.URI;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.uri.UriQuery;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.SecurityTime;
import io.helidon.security.Subject;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;

import org.junit.jupiter.api.Test;

import static io.helidon.security.providers.httpsign.SignedHeadersConfig.REQUEST_TARGET;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link HttpSignProvider}.
 */
abstract class CurrentHttpSignProviderTest {
    static final String INBOUND_DATE = "Sun, 08 Jun 2014 18:32:30 GMT";
    static final String STALE_DATE = "Sun, 08 Jun 2014 18:27:29 GMT";
    static final String STALE_BOUNDARY_DATE = "Sun, 08 Jun 2014 18:27:30 GMT";
    static final String FUTURE_BOUNDARY_DATE = "Sun, 08 Jun 2014 18:37:30 GMT";
    static final String FUTURE_DATE = "Sun, 08 Jun 2014 18:37:31 GMT";

    abstract HttpSignProvider getProvider();

    @Test
    void testInboundSignatureRsa() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));
        headers.put("authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));

        HttpSignProvider provider = getProvider();

        SecurityContext context = mock(SecurityContext.class);
        SecurityEnvironment signingEnv = SecurityEnvironment.builder(inboundTime())
                .path("/my/resource")
                .headers(headers)
                .build();
        Keys keyConfig = Keys.builder()
                .keystore(keystore -> keystore
                        .keystore(Resource.create(Paths.get("src/test/resources/keystore.p12")))
                        .passphrase("password")
                        .keyAlias("myPrivateKey"))
                .build();
        headers.put("Signature",
                    List.of(signatureHeader(signingEnv,
                                            OutboundTargetDefinition.builder("rsa-key-12345")
                                                    .signedHeaders(signedHeaders())
                                                    .privateKeyConfig(keyConfig)
                                                    .build(),
                                            false)));
        SecurityEnvironment se = SecurityEnvironment.builder(inboundTime())
                .path("/my/resource")
                .headers(headers)
                .build();
        EndpointConfig ep = EndpointConfig.create();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(se);
        when(request.endpointConfig()).thenReturn(ep);

        AuthenticationResponse atnResponse = provider.authenticate(request);

        assertThat(atnResponse.description().orElse("Unknown problem"),
                   atnResponse.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));

        atnResponse.user()
                .map(Subject::principal)
                .ifPresentOrElse(principal -> {
                    assertThat(principal.getName(), is("aUser"));
                    assertThat(principal.abacAttribute(HttpSignProvider.ATTRIB_NAME_KEY_ID), is(Optional.of("rsa-key-12345")));
                }, () -> fail("User must be filled"));

    }

    @Test
    void testInboundSignatureHmac() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));
        headers.put("authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));

        HttpSignProvider provider = getProvider();

        SecurityContext context = mock(SecurityContext.class);
        SecurityEnvironment signingEnv = SecurityEnvironment.builder(inboundTime())
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
        SecurityEnvironment se = SecurityEnvironment.builder(inboundTime())
                .path("/my/resource")
                .headers(headers)
                .build();
        EndpointConfig ep = EndpointConfig.create();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(se);
        when(request.endpointConfig()).thenReturn(ep);

        AuthenticationResponse atnResponse = provider.authenticate(request);

        assertThat(atnResponse.description().orElse("Unknown problem"),
                   atnResponse.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));

        atnResponse.service()
                .map(Subject::principal)
                .ifPresentOrElse(principal -> {
                    assertThat(principal.getName(), is("aSetOfTrustedServices"));
                    assertThat(principal.abacAttribute(HttpSignProvider.ATTRIB_NAME_KEY_ID), is(Optional.of("myServiceKeyId")));
                }, () -> fail("User must be filled"));
    }

    @Test
    void testInboundSignatureHmacFixedHeader() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));
        headers.put("authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));
        headers.put("Signature",
                    List.of("keyId=\"myServiceKeyId\",algorithm=\"hmac-sha256\",headers=\"date host (request-target) "
                                    + "authorization\","
                                    + "signature=\"+u2QV7HWQRxTzSpXmNGFgQO4iLkKN/XXBPKHMRm4tKg=\""));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = getProvider().authenticate(request);

        assertThat(atnResponse.description().orElse("Unknown problem"),
                   atnResponse.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void testInboundSignatureRejectsStaleDate() {
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

        AuthenticationResponse atnResponse = getProvider().authenticate(request);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("Date header is outside the allowed validity interval"));
    }

    @Test
    void testInboundSignatureRejectsFutureDate() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(FUTURE_DATE));
        headers.put("authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(futureTime())
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

        AuthenticationResponse atnResponse = getProvider().authenticate(request);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("Date header is outside the allowed validity interval"));
    }

    @Test
    void testInboundSignatureRejectsMissingSignedDate() {
        Map<String, List<String>> signingHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        signingHeaders.put("host", List.of("example.org"));
        signingHeaders.put("date", List.of(INBOUND_DATE));

        Map<String, List<String>> requestHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        requestHeaders.putAll(signingHeaders);
        requestHeaders.remove("date");

        assertSignedDateRejected(signingHeaders,
                                 requestHeaders,
                                 inboundTime(),
                                 "Date header is signed, but missing from request");
    }

    @Test
    void testInboundSignatureRejectsMultipleDateValues() {
        Map<String, List<String>> signingHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        signingHeaders.put("host", List.of("example.org"));
        signingHeaders.put("date", List.of(INBOUND_DATE));

        Map<String, List<String>> requestHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        requestHeaders.putAll(signingHeaders);
        requestHeaders.put("date", List.of(INBOUND_DATE, INBOUND_DATE));

        assertSignedDateRejected(signingHeaders,
                                 requestHeaders,
                                 inboundTime(),
                                 "Date header must contain exactly one value");
    }

    @Test
    void testInboundSignatureRejectsUnparsableDate() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of("not-a-date"));

        assertSignedDateRejected(headers,
                                 new TreeMap<>(headers),
                                 inboundTime(),
                                 "Date header cannot be parsed");
    }

    @Test
    void testInboundSignatureRejectsDateLikeMalformedValues() {
        for (String date : List.of("Sun, 08 Foo 2014 18:32:30 GMT",
                                   "Sunday, 08-Jun-14 18:32:30 PST",
                                   "Sun Jun  8 18:32:30 GMT 2014")) {
            Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            headers.put("host", List.of("example.org"));
            headers.put("date", List.of(date));

            assertSignedDateRejected(headers,
                                     new TreeMap<>(headers),
                                     inboundTime(),
                                     "Date header cannot be parsed");
        }
    }

    @Test
    void testInboundSignatureAcceptsDateAtValidityBoundary() {
        assertDateAccepted(STALE_BOUNDARY_DATE, securityTime(27, 30));
        assertDateAccepted(FUTURE_BOUNDARY_DATE, securityTime(37, 30));
    }

    @Test
    void testInboundSignatureAcceptsObsoleteHttpDateFormats() {
        assertDateAccepted("Sunday, 08-Jun-14 18:32:30 GMT", inboundTime());
        assertDateAccepted("Sun Jun  8 18:32:30 2014", inboundTime());
    }

    @Test
    void testInboundSignatureResolvesRfc850YearAgainstSecurityTime() {
        SecurityTime time = securityTime(2076, 32, 30);

        assertDateAccepted("Monday, 08-Jun-76 18:32:30 GMT", time, time);
    }

    @Test
    void testInboundAuthorizationSignatureDoesNotRequireCarrierHeader() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(inboundTime())
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

        AuthenticationResponse atnResponse = getProvider().authenticate(request);

        assertThat(atnResponse.description().orElse("Unknown problem"),
                   atnResponse.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void testInboundAuthorizationSignatureRejectsAdditionalAuthorizationValue() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(inboundTime())
                .path("/my/resource")
                .headers(headers)
                .build();
        String signatureHeader = signatureHeader(signingEnv,
                                                 OutboundTargetDefinition.builder("myServiceKeyId")
                                                         .hmacSecret("MyPasswordForHmac")
                                                         .signedHeaders(signedHeadersNoAuthorization())
                                                         .build(),
                                                 false);
        headers.put("Authorization", List.of("Signature " + signatureHeader, "Bearer unsigned"));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = getProvider().authenticate(request);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("cannot be combined with other authorization values"));
    }

    @Test
    void testInboundAuthorizationSignatureRejectsCommaDelimitedAdditionalAuthorizationValue() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(inboundTime())
                .path("/my/resource")
                .headers(headers)
                .build();
        String signatureHeader = signatureHeader(signingEnv,
                                                 OutboundTargetDefinition.builder("myServiceKeyId")
                                                         .hmacSecret("MyPasswordForHmac")
                                                         .signedHeaders(signedHeadersNoAuthorization())
                                                         .build(),
                                                 false);
        headers.put("Authorization", List.of("Signature " + signatureHeader + ", Bearer unsigned"));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = getProvider().authenticate(request);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("Unexpected data after signature parameters"));
    }

    @Test
    void testInboundAuthorizationSignatureRejectsCommaDelimitedQuotedAuthorizationValue() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(inboundTime())
                .path("/my/resource")
                .headers(headers)
                .build();
        String signatureHeader = signatureHeader(signingEnv,
                                                 OutboundTargetDefinition.builder("myServiceKeyId")
                                                         .hmacSecret("MyPasswordForHmac")
                                                         .signedHeaders(signedHeadersNoAuthorization())
                                                         .build(),
                                                 false);
        headers.put("Authorization", List.of("Signature " + signatureHeader + ", Digest username=\"u\""));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = getProvider().authenticate(request);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("Unexpected data after signature parameters"));
    }

    @Test
    void testInboundAuthorizationSignatureRejectsStaleDate() {
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

        AuthenticationResponse atnResponse = requiredAuthorizationProvider().authenticate(request);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("Date header is outside the allowed validity interval"));
    }

    @Test
    void testInboundAuthorizationSignatureRejectsFutureDate() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(FUTURE_DATE));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(futureTime())
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

        AuthenticationResponse atnResponse = requiredAuthorizationProvider().authenticate(request);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("Date header is outside the allowed validity interval"));
    }

    @Test
    void testInboundSignatureBindsQueryParameters() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(inboundTime())
                .path("/my/resource")
                .queryParam("id", "1")
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
                                      .queryParam("id", "2")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = getProvider().authenticate(request);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("Signature is not valid"));
    }

    @Test
    void testInboundSignatureBindsRawQueryParams() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(inboundTime())
                .path("/my/resource")
                .queryParams(UriQuery.create("b=2&a=1&a=3&encoded=a%2Fb&admin"))
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
                                      .queryParams(UriQuery.create("a=1&a=3&b=2&encoded=a%2Fb&admin"))
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = getProvider().authenticate(request);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("Signature is not valid"));
    }

    @Test
    void testInboundSignatureAcceptsFixedRawQueryParamsSignature() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));
        headers.put("Signature",
                    List.of("keyId=\"myServiceKeyId\",algorithm=\"hmac-sha256\","
                                    + "headers=\"date host (request-target)\","
                                    + "signature=\"rLp+n5JpYjSUhnS8KOJmzz/bPS3RWAFs1CnR4RL3KcI=\""));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my%2Fresource")
                                      .queryParams(UriQuery.create("b=2&a=1&a=3&encoded=a%2Fb&admin"))
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = getProvider().authenticate(request);

        assertThat(atnResponse.description().orElse("Unknown problem"),
                   atnResponse.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void testInboundRsaSignatureWithMalformedRequestTargetFailsAuthentication() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));
        headers.put("Signature",
                    List.of("keyId=\"rsa-key-12345\",algorithm=\"rsa-sha256\",headers=\"date host (REQUEST-TARGET)\","
                                    + "signature=\"AAAA\""));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = getProvider().authenticate(request);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
    }

    @Test
    void testOutboundSignatureRsa() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        // the generated host contains port as well, so we must explicitly define it here
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));

        SecurityContext context = mock(SecurityContext.class);
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .path("/my/resource")
                .targetUri(URI.create("http://example.org/my/resource"))
                .headers(headers)
                .build();

        EndpointConfig outboundEp = EndpointConfig.create();

        boolean outboundSupported = getProvider().isOutboundSupported(request, outboundEnv, outboundEp);
        assertThat("Outbound should be supported", outboundSupported, is(true));

        OutboundSecurityResponse response = getProvider().outboundSecurity(request, outboundEnv, outboundEp);

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));

        Map<String, List<String>> updatedHeaders = response.requestHeaders();
        assertThat(updatedHeaders, notNullValue());

        //and now the value
        validateSignatureHeader(
                outboundEnv,
                updatedHeaders.get("Signature").iterator().next(),
                "rsa-key-12345",
                "rsa-sha256",
                List.of("date", "host", REQUEST_TARGET, "authorization"),
                "ptxE46kM/gV8L6Q0jcrY5Sxet7vy/rqldwxJfWT5ncbALbwvr4puc3/M0q8pT/srI/bLvtPPZxQN9flaWyHo2ieypRSRZe5/2FrcME"
                        + "+XuGNOu9BVJlCrALgLwi2VGJ3i2BIH2EvpLqF4TmM7AHIn/E6trWf30Kr90sTrk1ewx7kJ0bPVfY6Pv1mJpuA4MVr"
                        + "++BvvXMuGooMI+nepToPlseGgtnYMJPuTRwZJbTLo02yN1rKnRZauCxCCd0bgi9zhJRlXFuoLzthCgqHElCXVXrW+ZGACUaRDC"
                        + "+XawXg6eyMWp6GVegS/NVRnaqEkBsl0hn7X/dmEXDDERyK66qn0WA==");
    }

    @Test
    void testOutboundSignatureHmac() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        // the generated host contains port as well, so we must explicitly define it here
        headers.put("host", List.of("localhost"));
        headers.put("date", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));

        SecurityContext context = mock(SecurityContext.class);
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);

        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .path("/second/someOtherPath")
                .targetUri(URI.create("http://localhost/second/someOtherPath"))
                .headers(headers)
                .build();

        EndpointConfig outboundEp = EndpointConfig.create();

        boolean outboundSupported = getProvider().isOutboundSupported(request, outboundEnv, outboundEp);
        assertThat("Outbound should be supported", outboundSupported, is(true));

        OutboundSecurityResponse response = getProvider().outboundSecurity(request, outboundEnv, outboundEp);

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));

        Map<String, List<String>> updatedHeaders = response.requestHeaders();
        assertThat(updatedHeaders, notNullValue());

        //and now the value
        validateSignatureHeader(outboundEnv,
                                updatedHeaders.get("Signature").iterator().next(),
                                "myServiceKeyId",
                                "hmac-sha256",
                                List.of("date", REQUEST_TARGET, "host"),
                                "WGgirKG0IJQPGosDuTDi40uABCbxDm4oduKiH+iVuII=");
    }

    @Test
    void testOutboundSignatureHmacGeneratesHostHeader() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("date", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));

        SecurityContext context = mock(SecurityContext.class);
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);

        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .path("/second/someOtherPath")
                .targetUri(URI.create("http://localhost/second/someOtherPath"))
                .headers(headers)
                .build();

        OutboundSecurityResponse response = getProvider().outboundSecurity(request, outboundEnv, EndpointConfig.create());

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.requestHeaders().get("host"), is(List.of("localhost")));
    }

    @Test
    void testDefaultOutboundSignaturePostDoesNotIncludeEntityHeaders() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("localhost"));
        headers.put("date", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("content-length", List.of("123"));
        headers.put("content-type", List.of("text/plain"));
        headers.put("digest", List.of("sha-256=value"));

        SecurityContext context = mock(SecurityContext.class);
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);

        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .method("POST")
                .path("/second/someOtherPath")
                .targetUri(URI.create("http://localhost/second/someOtherPath"))
                .headers(headers)
                .build();

        OutboundSecurityResponse response = getProvider().outboundSecurity(request, outboundEnv, EndpointConfig.create());

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        String signatureHeader = response.requestHeaders().get("Signature").iterator().next();
        assertThat(signatureHeader, containsString("headers=\"date (request-target) host\""));
        assertThat(signatureHeader.contains("content-length"), is(false));
        assertThat(signatureHeader.contains("content-type"), is(false));
        assertThat(signatureHeader.contains("digest"), is(false));
    }

    @Test
    void testOutboundAuthorizationCarrierDoesNotSignPreviousAuthorizationHeader() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", List.of("localhost"));
        headers.put("date", List.of(INBOUND_DATE));
        headers.put("authorization", List.of("Bearer token"));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(mock(SecurityContext.class));

        HttpSignProvider provider = HttpSignProvider.builder()
                .addAcceptHeader(HttpSignHeader.AUTHORIZATION)
                .addInbound(InboundClientDefinition.builder("myServiceKeyId")
                                    .principalName("aSetOfTrustedServices")
                                    .hmacSecret("MyPasswordForHmac")
                                    .build())
                .outbound(OutboundConfig.builder()
                                  .addTarget(OutboundTarget.builder("authorization")
                                                     .addTransport("http")
                                                     .addHost("localhost")
                                                     .addPath("/authorization/.*")
                                                     .customObject(OutboundTargetDefinition.class,
                                                                   OutboundTargetDefinition.builder("myServiceKeyId")
                                                                           .header(HttpSignHeader.AUTHORIZATION)
                                                                           .hmacSecret("MyPasswordForHmac")
                                                                           .build())
                                                     .build())
                                  .build())
                .build();
        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .path("/authorization/resource")
                .targetUri(URI.create("http://localhost/authorization/resource"))
                .headers(headers)
                .build();

        OutboundSecurityResponse response = provider.outboundSecurity(request, outboundEnv, EndpointConfig.create());

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        String authorization = response.requestHeaders().get("Authorization").iterator().next();
        HttpSignature httpSignature = HttpSignature.fromAuthorizationHeader(authorization.substring("Signature ".length()), false);
        assertThat(httpSignature.getHeaders(), is(List.of("date", REQUEST_TARGET, "host")));

        ProviderRequest inboundRequest = mock(ProviderRequest.class);
        when(inboundRequest.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                             .path("/authorization/resource")
                                             .headers(response.requestHeaders())
                                             .build());
        AuthenticationResponse authentication = provider.authenticate(inboundRequest);

        assertThat(authentication.description().orElse("Unknown problem"),
                   authentication.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void testOutboundAuthorizationCarrierIgnoresExplicitAuthorizationSignedHeader() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", List.of("localhost"));
        headers.put("date", List.of(INBOUND_DATE));
        headers.put("authorization", List.of("Bearer token"));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(mock(SecurityContext.class));

        HttpSignProvider provider = HttpSignProvider.builder()
                .addAcceptHeader(HttpSignHeader.AUTHORIZATION)
                .addInbound(InboundClientDefinition.builder("myServiceKeyId")
                                    .principalName("aSetOfTrustedServices")
                                    .hmacSecret("MyPasswordForHmac")
                                    .build())
                .outbound(OutboundConfig.builder()
                                  .addTarget(OutboundTarget.builder("authorization")
                                                     .addTransport("http")
                                                     .addHost("localhost")
                                                     .addPath("/authorization/.*")
                                                     .customObject(OutboundTargetDefinition.class,
                                                                   OutboundTargetDefinition.builder("myServiceKeyId")
                                                                           .header(HttpSignHeader.AUTHORIZATION)
                                                                           .hmacSecret("MyPasswordForHmac")
                                                                           .signedHeaders(signedHeaders())
                                                                           .build())
                                                     .build())
                                  .build())
                .build();
        SecurityEnvironment outboundEnv = SecurityEnvironment.builder()
                .path("/authorization/resource")
                .targetUri(URI.create("http://localhost/authorization/resource"))
                .headers(headers)
                .build();

        OutboundSecurityResponse response = provider.outboundSecurity(request, outboundEnv, EndpointConfig.create());

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        String authorization = response.requestHeaders().get("Authorization").iterator().next();
        HttpSignature httpSignature = HttpSignature.fromAuthorizationHeader(authorization.substring("Signature ".length()), false);
        assertThat(httpSignature.getHeaders(), is(List.of("date", "host", REQUEST_TARGET)));

        ProviderRequest inboundRequest = mock(ProviderRequest.class);
        when(inboundRequest.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                             .path("/authorization/resource")
                                             .headers(response.requestHeaders())
                                             .build());
        AuthenticationResponse authentication = provider.authenticate(inboundRequest);

        assertThat(authentication.description().orElse("Unknown problem"),
                   authentication.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    static String signatureHeader(SecurityEnvironment env,
                                  OutboundTargetDefinition outboundDefinition,
                                  boolean backwardCompatibleEol) {
        return HttpSignature.sign(env, outboundDefinition, new HashMap<>(), backwardCompatibleEol)
                .toSignatureHeader();
    }

    static SignedHeadersConfig signedHeaders() {
        return SignedHeadersConfig.builder()
                .defaultConfig(SignedHeadersConfig.HeadersConfig
                                       .create(List.of("date", "host", REQUEST_TARGET, "authorization")))
                .build();
    }

    static SignedHeadersConfig signedHeadersNoAuthorization() {
        return SignedHeadersConfig.builder()
                .defaultConfig(SignedHeadersConfig.HeadersConfig.create(List.of("date", "host", REQUEST_TARGET)))
                .build();
    }

    static SecurityTime inboundTime() {
        return securityTime(32, 30);
    }

    static SecurityTime staleTime() {
        return securityTime(27, 29);
    }

    static SecurityTime futureTime() {
        return securityTime(37, 31);
    }

    private static SecurityTime securityTime(int minute, int second) {
        return securityTime(2014, minute, second);
    }

    private static SecurityTime securityTime(int year, int minute, int second) {
        return SecurityTime.builder()
                .timeZone(ZoneId.of("GMT"))
                .value(ChronoField.YEAR, year)
                .value(ChronoField.MONTH_OF_YEAR, 6)
                .value(ChronoField.DAY_OF_MONTH, 8)
                .value(ChronoField.HOUR_OF_DAY, 18)
                .value(ChronoField.MINUTE_OF_HOUR, minute)
                .value(ChronoField.SECOND_OF_MINUTE, second)
                .value(ChronoField.MILLI_OF_SECOND, 0)
                .build();
    }

    private static HttpSignProvider requiredAuthorizationProvider() {
        return HttpSignProvider.builder()
                .optional(false)
                .addAcceptHeader(HttpSignHeader.AUTHORIZATION)
                .addInbound(InboundClientDefinition.builder("myServiceKeyId")
                                    .principalName("aSetOfTrustedServices")
                                    .hmacSecret("MyPasswordForHmac")
                                    .build())
                .build();
    }

    private void assertDateAccepted(String date, SecurityTime signingTime) {
        assertDateAccepted(date, signingTime, inboundTime());
    }

    private void assertDateAccepted(String date, SecurityTime signingTime, SecurityTime validationTime) {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(date));

        SecurityEnvironment signingEnv = SecurityEnvironment.builder(signingTime)
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
        when(request.env()).thenReturn(SecurityEnvironment.builder(validationTime)
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = getProvider().authenticate(request);

        assertThat(atnResponse.description().orElse("Unknown problem"),
                   atnResponse.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    private void assertSignedDateRejected(Map<String, List<String>> signingHeaders,
                                          Map<String, List<String>> requestHeaders,
                                          SecurityTime signingTime,
                                          String expectedDescription) {
        SecurityEnvironment signingEnv = SecurityEnvironment.builder(signingTime)
                .path("/my/resource")
                .headers(signingHeaders)
                .build();
        requestHeaders.put("Signature",
                           List.of(signatureHeader(signingEnv,
                                                   OutboundTargetDefinition.builder("myServiceKeyId")
                                                           .hmacSecret("MyPasswordForHmac")
                                                           .signedHeaders(signedHeadersNoAuthorization())
                                                           .build(),
                                                   false)));

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(requestHeaders)
                                      .build());

        AuthenticationResponse atnResponse = getProvider().authenticate(request);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString(expectedDescription));
    }

    private void validateSignatureHeader(
            SecurityEnvironment env,
            String signatureHeader,
            String myServiceKeyId,
            String algorithm,
            List<String> headers,
            String actualSignature) {
        HttpSignature httpSignature = HttpSignature.fromHeader(signatureHeader, true);

        String reason = httpSignature.getAlgorithm() + ", " + httpSignature.getHeaders() + ", " + httpSignature
                .getSignedString(new HashMap<>(), env);

        assertThat(reason, httpSignature.getKeyId(), is(myServiceKeyId));
        assertThat(reason, httpSignature.getAlgorithm(), is(algorithm));
        assertThat(reason, httpSignature.getHeaders(), is(headers));
        assertThat(reason, httpSignature.getBase64Signature(), is(actualSignature));
    }
}
