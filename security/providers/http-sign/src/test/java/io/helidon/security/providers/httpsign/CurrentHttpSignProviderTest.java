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
import java.time.Duration;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.common.reactive.Single;
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
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    static final String INBOUND_DATE = "Sun, 08 Jun 2014 18:32:30 GMT";
    static final String STALE_DATE = "Sun, 08 Jun 2014 18:27:29 GMT";
    static final String FUTURE_DATE = "Sun, 08 Jun 2014 18:37:31 GMT";

    abstract HttpSignProvider getProvider();

    @Test
    void testInboundSignatureRsa() throws ExecutionException, InterruptedException {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));
        headers.put("authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));

        HttpSignProvider provider = getProvider();

        SecurityContext context = mock(SecurityContext.class);
        when(context.executorService()).thenReturn(ForkJoinPool.commonPool());
        SecurityEnvironment signingEnv = SecurityEnvironment.builder(inboundTime())
                .path("/my/resource")
                .headers(headers)
                .build();
        headers.put("Signature",
                    List.of(signatureHeader(signingEnv,
                                            OutboundTargetDefinition.builder("rsa-key-12345")
                                                    .privateKeyConfig(KeyConfig.keystoreBuilder()
                                                                              .keystore(Resource.create(Paths.get(
                                                                                      "src/test/resources/keystore.p12")))
                                                                              .keystorePassphrase("password".toCharArray())
                                                                              .keyAlias("myPrivateKey")
                                                                              .build())
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

        AuthenticationResponse atnResponse = Single.create(provider.authenticate(request)).await(TIMEOUT);

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
    void testInboundSignatureHmac() throws InterruptedException, ExecutionException {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("host", List.of("example.org"));
        headers.put("date", List.of(INBOUND_DATE));
        headers.put("authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));

        HttpSignProvider provider = getProvider();

        SecurityContext context = mock(SecurityContext.class);
        when(context.executorService()).thenReturn(ForkJoinPool.commonPool());
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

        AuthenticationResponse atnResponse = Single.create(provider.authenticate(request)).await(TIMEOUT);

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

        SecurityContext context = mock(SecurityContext.class);
        when(context.executorService()).thenReturn(ForkJoinPool.commonPool());
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = Single.create(getProvider().authenticate(request)).await(TIMEOUT);

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

        SecurityContext context = mock(SecurityContext.class);
        when(context.executorService()).thenReturn(ForkJoinPool.commonPool());
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path("/my/resource")
                                      .headers(headers)
                                      .build());

        AuthenticationResponse atnResponse = Single.create(getProvider().authenticate(request)).await(TIMEOUT);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("Date header is outside the allowed validity interval"));
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

        AuthenticationResponse atnResponse = authenticate(getProvider(), headers);

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

        AuthenticationResponse atnResponse = authenticate(getProvider(), headers);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("cannot be combined with other authorization values"));
    }

    @Test
    void testInboundAuthorizationSignatureRejectsCommaDelimitedAdditionalValue() {
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

        AuthenticationResponse atnResponse = authenticate(getProvider(), headers);

        assertThat(atnResponse.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(atnResponse.description().orElse("Unknown problem"),
                   containsString("Unexpected data after signature parameters"));
    }

    @Test
    void testOutboundAuthorizationCarrierDoesNotSignPreviousAuthorizationHeader() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", List.of("localhost"));
        headers.put("date", List.of(INBOUND_DATE));
        headers.put("authorization", List.of("Bearer token"));

        SecurityContext context = mock(SecurityContext.class);
        when(context.executorService()).thenReturn(ForkJoinPool.commonPool());
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);

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

        OutboundSecurityResponse response = Single.create(provider.outboundSecurity(request, outboundEnv, EndpointConfig.create()))
                .await(TIMEOUT);

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        String authorization = response.requestHeaders().get("Authorization").iterator().next();
        HttpSignature httpSignature = HttpSignature.fromAuthorizationHeader(authorization.substring("Signature ".length()), false);
        assertThat(httpSignature.getHeaders(), is(List.of("date", REQUEST_TARGET, "host")));

        AuthenticationResponse authentication = authenticate(provider, response.requestHeaders(), "/authorization/resource");

        assertThat(authentication.description().orElse("Unknown problem"),
                   authentication.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }

    @Test
    void testOutboundSignatureRsa() throws ExecutionException, InterruptedException {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        // the generated host contains port as well, so we must explicitly define it here
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));

        SecurityContext context = mock(SecurityContext.class);
        when(context.executorService()).thenReturn(ForkJoinPool.commonPool());
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

        OutboundSecurityResponse response = Single.create(getProvider().outboundSecurity(request, outboundEnv, outboundEp))
                .await(TIMEOUT);

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
    void testOutboundSignatureHmac() throws ExecutionException, InterruptedException {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        // the generated host contains port as well, so we must explicitly define it here
        headers.put("host", List.of("localhost"));
        headers.put("date", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));

        SecurityContext context = mock(SecurityContext.class);
        when(context.executorService()).thenReturn(ForkJoinPool.commonPool());
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

        OutboundSecurityResponse response = Single.create(getProvider().outboundSecurity(request, outboundEnv, outboundEp))
                                                                  .await(TIMEOUT);

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
        return SecurityTime.builder()
                .timeZone(ZoneId.of("GMT"))
                .value(ChronoField.YEAR, 2014)
                .value(ChronoField.MONTH_OF_YEAR, 6)
                .value(ChronoField.DAY_OF_MONTH, 8)
                .value(ChronoField.HOUR_OF_DAY, 18)
                .value(ChronoField.MINUTE_OF_HOUR, minute)
                .value(ChronoField.SECOND_OF_MINUTE, second)
                .value(ChronoField.MILLI_OF_SECOND, 0)
                .build();
    }

    private static AuthenticationResponse authenticate(HttpSignProvider provider, Map<String, List<String>> headers) {
        return authenticate(provider, headers, "/my/resource");
    }

    private static AuthenticationResponse authenticate(HttpSignProvider provider,
                                                       Map<String, List<String>> headers,
                                                       String path) {
        SecurityContext context = mock(SecurityContext.class);
        when(context.executorService()).thenReturn(ForkJoinPool.commonPool());
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(SecurityEnvironment.builder(inboundTime())
                                      .path(path)
                                      .headers(headers)
                                      .build());

        return Single.create(provider.authenticate(request)).await(TIMEOUT);
    }
}
