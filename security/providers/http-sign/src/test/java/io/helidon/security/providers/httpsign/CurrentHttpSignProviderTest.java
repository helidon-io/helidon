/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;

import org.junit.jupiter.api.Test;

import static io.helidon.security.providers.httpsign.SignedHeadersConfig.REQUEST_TARGET;
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

    abstract HttpSignProvider getProvider();

    @Test
    void testInboundSignatureRsa() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        headers.put("Signature",
                    List.of("keyId=\"rsa-key-12345\",algorithm=\"rsa-sha256\",headers=\"date "
                                    + "host (request-target) authorization\","
                                    + "signature=\"ptxE46kM/gV8L6Q0jcrY5Sxet7vy/rqldwxJfWT5ncbALbwvr4puc3/M0q8pT/srI/bLvtPPZxQN9flaWyHo2ieypRSRZe5/2FrcME"
                                    + "+XuGNOu9BVJlCrALgLwi2VGJ3i2BIH2EvpLqF4TmM7AHIn/E6trWf30Kr90sTrk1ewx7kJ0bPVfY6Pv1mJpuA4MVr"
                                    + "++BvvXMuGooMI+nepToPlseGgtnYMJPuTRwZJbTLo02yN1rKnRZauCxCCd0bgi9zhJRlXFuoLzthCgqHElCXVXrW+ZGACUaRDC"
                                    + "+XawXg6eyMWp6GVegS/NVRnaqEkBsl0hn7X/dmEXDDERyK66qn0WA==\""));
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));

        HttpSignProvider provider = getProvider();

        SecurityContext context = mock(SecurityContext.class);
        SecurityEnvironment se = SecurityEnvironment.builder()
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

        headers.put("Signature",
                    List.of("keyId=\"myServiceKeyId\",algorithm=\"hmac-sha256\",headers=\"date host (request-target) "
                                    + "authorization\","
                                    + "signature=\"yaxxY9oY0+qKhAr9sYCfmYQyKjRVctN6z1c9ANhbZ/c=\""));
        headers.put("host", List.of("example.org"));
        headers.put("date", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));

        HttpSignProvider provider = getProvider();

        SecurityContext context = mock(SecurityContext.class);
        SecurityEnvironment se = SecurityEnvironment.builder()
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
