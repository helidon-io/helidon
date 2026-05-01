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
abstract class OldHttpSignProviderTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String INBOUND_DATE = "Sun, 08 Jun 2014 18:32:30 GMT";
    private static final String STALE_DATE = "Sun, 08 Jun 2014 18:27:29 GMT";
    private static final String FUTURE_DATE = "Sun, 08 Jun 2014 18:37:31 GMT";

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
                                                    .build())));
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
                                                    .build())));
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
                                                    .build())));

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
                                                    .build())));

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
                "Rm5PjuUdJ927esGQ2gm/6QBEM9IM7J5qSZuP8NV8+GXUf"
                        + "boUV6ST2EYLYniFGt5/3BO/2+vqQdqezdTVPr/JCwqBx+9T9ZynG7YqRj"
                        + "KvXzcmvQOu5vQmCK5x/HR0fXU41Pjq+jywsD0k6KdxF6TWr6tvWRbwFet"
                        + "+YSb0088o/65Xeqghw7s0vShf7jPZsaaIHnvM9SjWgix9VvpdEn4NDvqh"
                        + "ebieVD3Swb1VG5+/7ECQ9VAlX30U5/jQ5hPO3yuvRlg5kkMjJiN7tf/68"
                        + "If/5O2Z4H+7VmW0b1U69/JoOQJA0av1gCX7HVfa/YTCxIK4UFiI6h963q"
                        + "2x7LSkqhdWGA==");
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
                                "SkeKVi6BoUd2/aUfXyIVIFAKEkKp7sg2KsS1UieB/+E=");
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

    private static String signatureHeader(SecurityEnvironment env, OutboundTargetDefinition outboundDefinition) {
        return HttpSignature.sign(env, outboundDefinition, new HashMap<>(), true)
                .toSignatureHeader();
    }

    private static SignedHeadersConfig signedHeaders() {
        return SignedHeadersConfig.builder()
                .defaultConfig(SignedHeadersConfig.HeadersConfig
                                       .create(List.of("date", "host", REQUEST_TARGET, "authorization")))
                .build();
    }

    private static SecurityTime inboundTime() {
        return securityTime(32, 30);
    }

    private static SecurityTime staleTime() {
        return securityTime(27, 29);
    }

    private static SecurityTime futureTime() {
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
}
