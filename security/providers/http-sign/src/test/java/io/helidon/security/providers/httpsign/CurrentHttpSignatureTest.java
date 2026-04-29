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
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.security.SecurityEnvironment;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link HttpSignature}.
 */
class CurrentHttpSignatureTest {
    @Test
    void testValid() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"rsa-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\"";

        testValid(validSignature);
    }

    @Test
    void testValidInvalidComponent() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"rsa-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",hurhur=\"ignored\"";

        testValid(validSignature);
    }

    @Test
    void testValidRepeatedComponent() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"hamc-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",algorithm=\"rsa-sha256\"";

        testValid(validSignature);
    }

    @Test
    void testValidInvalidLastComponent1() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"hamc-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",algorithm=\"rsa-sha256\",abcd";

        testValid(validSignature);
    }

    @Test
    void testValidInvalidLastComponent2() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"hamc-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",algorithm=\"rsa-sha256\",abcd=";

        testValid(validSignature);
    }

    @Test
    void testValidInvalidLastComponent3() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"hamc-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",algorithm=\"rsa-sha256\",abcd=\"asf";

        testValid(validSignature);
    }

    @Test
    void testInvalid1() {
        String invalidSignature = "keyId=\"rsa-key-1\",algorithm=\"hamc-sha256\","
                // missing quotes for headers
                + "headers=(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",algorithm=\"rsa-sha256\",abcd=\"asf";

        HttpSignature httpSignature = HttpSignature.fromHeader(invalidSignature, false);
        Optional<String> validate = httpSignature.validate();

        validate.ifPresentOrElse(msg -> assertThat(msg, containsString("signature is a mandatory")),
                                 () -> fail("Should have failed validation"));
    }

    @Test
    void testInvalid2() {
        String invalidSignature = "This is a wrong signature";

        HttpSignature httpSignature = HttpSignature.fromHeader(invalidSignature, false);
        Optional<String> validate = httpSignature.validate();

        validate.ifPresentOrElse(msg -> assertThat(msg, containsString("keyId is a mandatory")),
                                 () -> fail("Should have failed validation"));
    }

    @Test
    void testSignRsa() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("DATE", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("Authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));
        headers.put("host", List.of("example.org"));

        SecurityEnvironment env = buildSecurityEnv("/my/resource", headers);
        OutboundTargetDefinition outboundDef = OutboundTargetDefinition.builder("rsa-key-12345")
                .privateKeyConfig(Keys.builder()
                                          .keystore(keystore ->
                                                            keystore.keystore(Resource.create(Paths.get(
                                                                            "src/test/resources/keystore.p12")))
                                                                    .passphrase("password")
                                                                    .keyAlias("myPrivateKey"))
                                          .build())
                .signedHeaders(SignedHeadersConfig.builder()
                                       .defaultConfig(SignedHeadersConfig
                                                              .HeadersConfig
                                                              .create(List.of("date",
                                                                              "host",
                                                                              "(request-target)",
                                                                              "authorization")))
                                       .build())
                .build();

        HttpSignature signature = HttpSignature.sign(env, outboundDef, new HashMap<>(), false);
        assertThat(signature.getBase64Signature(),
                   is("ptxE46kM/gV8L6Q0jcrY5Sxet7vy/rqldwxJfWT5ncbALbwvr4puc3/M0q8pT/srI/bLvtPPZxQN9flaWyHo2ieypRSRZe5/2FrcME"
                              + "+XuGNOu9BVJlCrALgLwi2VGJ3i2BIH2EvpLqF4TmM7AHIn/E6trWf30Kr90sTrk1ewx7kJ0bPVfY6Pv1mJpuA4MVr"
                              + "++BvvXMuGooMI+nepToPlseGgtnYMJPuTRwZJbTLo02yN1rKnRZauCxCCd0bgi9zhJRlXFuoLzthCgqHElCXVXrW"
                              + "+ZGACUaRDC+XawXg6eyMWp6GVegS/NVRnaqEkBsl0hn7X/dmEXDDERyK66qn0WA=="));
    }

    @Test
    void testSignHmac() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("DATE", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("Authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));
        headers.put("host", List.of("example.org"));
        SecurityEnvironment env = buildSecurityEnv("/my/resource", headers);

        OutboundTargetDefinition outboundDef = OutboundTargetDefinition.builder("myServiceKeyId")
                .hmacSecret("MyPasswordForHmac")
                .signedHeaders(SignedHeadersConfig.builder()
                                       .defaultConfig(SignedHeadersConfig
                                                              .HeadersConfig
                                                              .create(List.of("date",
                                                                              "host",
                                                                              "(request-target)",
                                                                              "authorization")))
                                       .build())
                .build();

        HttpSignature signature = HttpSignature.sign(env, outboundDef, new HashMap<>(), false);

        assertThat(signature.getBase64Signature(), is("yaxxY9oY0+qKhAr9sYCfmYQyKjRVctN6z1c9ANhbZ/c="));
    }

    @Test
    void testSignedStringUsesRawQueryParams() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("DATE", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("Authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));
        headers.put("host", List.of("example.org"));
        SecurityEnvironment env = SecurityEnvironment.builder()
                .path("/my/resource")
                .queryParams(UriQuery.create("b=2&a=1&a=3&encoded=a%2Fb&admin"))
                .headers(headers)
                .build();

        HttpSignature signature = new HttpSignature("myServiceKeyId",
                                                    "hmac-sha256",
                                                    List.of("date",
                                                            "host",
                                                            "(request-target)",
                                                            "authorization"),
                                                    false);

        assertThat(signature.getSignedString(null, env),
                   is("date: Thu, 08 Jun 2014 18:32:30 GMT\n"
                              + "host: example.org\n"
                              + "(request-target): get /my/resource?b=2&a=1&a=3&encoded=a%2Fb&admin\n"
                              + "authorization: basic dXNlcm5hbWU6cGFzc3dvcmQ="));
    }

    @Test
    void testInboundValidationUsesRequestedTargetSnapshot() throws Exception {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("DATE", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("Authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));
        headers.put("host", List.of("example.org"));

        String signedString = "date: Thu, 08 Jun 2014 18:32:30 GMT\n"
                + "host: example.org\n"
                + "(request-target): get /my%2Fresource?\n"
                + "authorization: basic dXNlcm5hbWU6cGFzc3dvcmQ=";
        String algorithm = "HmacSHA256";
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec("MyPasswordForHmac".getBytes(StandardCharsets.UTF_8), algorithm));
        HttpSignature signature = HttpSignature.fromHeader(
                "keyId=\"myServiceKeyId\",algorithm=\"hmac-sha256\",headers=\"date host (request-target) authorization\","
                        + "signature=\""
                        + Base64.getEncoder().encodeToString(mac.doFinal(signedString.getBytes(StandardCharsets.UTF_8)))
                        + "\"", false);
        signature.validate().ifPresent(Assertions::fail);

        UriQueryWriteable queryParams = UriQueryWriteable.create();
        queryParams.fromQueryString("added=true");
        SecurityEnvironment env = SecurityEnvironment.builder()
                .path("/my/resource")
                .queryParams(queryParams)
                .requestedPath(UriPath.create("/my%2Fresource"))
                .requestedQuery(Optional.of(UriQuery.empty()))
                .headers(headers)
                .build();
        queryParams.clear();
        queryParams.fromQueryString("changed=true");

        InboundClientDefinition inboundClientDef = InboundClientDefinition.builder("myServiceKeyId")
                .principalName("theService")
                .hmacSecret("MyPasswordForHmac")
                .build();

        signature.validate(env,
                           inboundClientDef,
                           List.of("date"),
                           Duration.ZERO)
                .ifPresent(Assertions::fail);
    }

    @Test
    void testSignHmacAddHeaders() {
        SecurityEnvironment env = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost/test/path"))
                .build();

        OutboundTargetDefinition outboundDef = OutboundTargetDefinition.builder("myServiceKeyId")
                .hmacSecret("MyPasswordForHmac")
                .signedHeaders(SignedHeadersConfig.builder()
                                       .defaultConfig(SignedHeadersConfig
                                                              .HeadersConfig
                                                              .create(List.of("date",
                                                                              "host")))
                                       .build())
                .build();

        // just make sure this does not throw an exception for missing headers
        HttpSignature.sign(env, outboundDef, new HashMap<>(), false);
    }

    @Test
    void testSignHmacAddsHostWithoutUndefinedPort() {
        SecurityEnvironment env = SecurityEnvironment.builder()
                .targetUri(URI.create("http://example.org/test/path"))
                .build();
        Map<String, List<String>> newHeaders = new HashMap<>();

        OutboundTargetDefinition outboundDef = OutboundTargetDefinition.builder("myServiceKeyId")
                .hmacSecret("MyPasswordForHmac")
                .signedHeaders(SignedHeadersConfig.builder()
                                       .defaultConfig(SignedHeadersConfig
                                                              .HeadersConfig
                                                              .create(List.of("date",
                                                                              "host")))
                                       .build())
                .build();

        HttpSignature.sign(env, outboundDef, newHeaders, false);

        assertThat(newHeaders.get("host"), is(List.of("example.org")));
    }

    @Test
    void testSignHmacAddsHostWithExplicitPortsAndIpv6Literals() {
        assertAddedHost("http://example.org:8080/test/path", "example.org:8080");
        assertAddedHost("http://[::1]:8080/test/path", "[::1]:8080");
        assertAddedHost("http://[::1]/test/path", "[::1]");
    }

    @Test
    void testVerifyRsa() {
        HttpSignature signature = HttpSignature.fromHeader("keyId=\"rsa-key-12345\",algorithm=\"rsa-sha256\",headers=\"date "
                                                                   + "host (request-target) authorization\","
                                                                   + "signature=\"ptxE46kM/gV8L6Q0jcrY5Sxet7vy"
                                                                   + "/rqldwxJfWT5ncbALbwvr4puc3/M0q8pT/srI"
                                                                   + "/bLvtPPZxQN9flaWyHo2ieypRSRZe5/2FrcME"
                                                                   + "+XuGNOu9BVJlCrALgLwi2VGJ3i2BIH2EvpLqF4TmM7AHIn"
                                                                   + "/E6trWf30Kr90sTrk1ewx7kJ0bPVfY6Pv1mJpuA4MVr++BvvXMuGooMI"
                                                                   + "+nepToPlseGgtnYMJPuTRwZJbTLo02yN1rKnRZauCxCCd0bgi9zhJRlX"
                                                                   + "FuoLzthCgqHElCXVXrW+ZGACUaRDC+XawXg6eyMWp6GVegS/NVRnaqEk"
                                                                   + "Bsl0hn7X/dmEXDDERyK66qn0WA==\"",
                                                           false);
        signature.validate().ifPresent(Assertions::fail);

        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("DATE", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("Authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));
        headers.put("host", List.of("example.org"));

        InboundClientDefinition inboundClientDef = InboundClientDefinition.builder("rsa-key-12345")
                .principalName("theService")
                .publicKeyConfig(Keys.builder()
                                         .keystore(keystore ->
                                                           keystore.keystore(Resource.create(Paths.get(
                                                                           "src/test/resources/keystore.p12")))
                                                                   .passphrase("password")
                                                                   .certAlias("service_cert"))
                                         .build())
                .build();

        signature.validate(buildSecurityEnv("/my/resource", headers),
                           inboundClientDef,
                           List.of("date"),
                           Duration.ZERO)
                .ifPresent(Assertions::fail);
    }

    @Test
    void testVerifyHmac() {
        HttpSignature signature = HttpSignature.fromHeader(
                "keyId=\"myServiceKeyId\",algorithm=\"hmac-sha256\",headers=\"date host (request-target) authorization\","
                        + "signature=\"yaxxY9oY0+qKhAr9sYCfmYQyKjRVctN6z1c9ANhbZ/c=\"", false);

        signature.validate().ifPresent(Assertions::fail);

        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("DATE", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("Authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));
        headers.put("host", List.of("example.org"));
        SecurityEnvironment env = buildSecurityEnv("/my/resource", headers);

        InboundClientDefinition inboundClientDef = InboundClientDefinition.builder("myServiceKeyId")
                .principalName("theService")
                .hmacSecret("MyPasswordForHmac")
                .build();

        signature.validate(env,
                           inboundClientDef,
                           List.of("date"),
                           Duration.ZERO)
                .ifPresent(Assertions::fail);
    }

    private SecurityEnvironment buildSecurityEnv(String path, Map<String, List<String>> headers) {
        return SecurityEnvironment.builder()
                .path(path)
                .headers(headers)
                .build();
    }

    private void assertAddedHost(String uri, String expectedHost) {
        SecurityEnvironment env = SecurityEnvironment.builder()
                .targetUri(URI.create(uri))
                .build();
        Map<String, List<String>> newHeaders = new HashMap<>();

        OutboundTargetDefinition outboundDef = OutboundTargetDefinition.builder("myServiceKeyId")
                .hmacSecret("MyPasswordForHmac")
                .signedHeaders(SignedHeadersConfig.builder()
                                       .defaultConfig(SignedHeadersConfig
                                                              .HeadersConfig
                                                              .create(List.of("date",
                                                                              "host")))
                                       .build())
                .build();

        HttpSignature.sign(env, outboundDef, newHeaders, false);

        assertThat(newHeaders.get("host"), is(List.of(expectedHost)));
    }

    private void testValid(String validSignature) {
        HttpSignature httpSignature = HttpSignature.fromHeader(validSignature, false);

        assertThat(httpSignature.getAlgorithm(), is("rsa-sha256"));
        assertThat(httpSignature.getKeyId(), is("rsa-key-1"));
        assertThat(httpSignature.getBase64Signature(), is("Base64(RSA-SHA256(signing string))"));
        assertThat(httpSignature.getHeaders(),
                   equalTo(List.of("(request-target)", "host", "date", "digest", "content-length")));
    }

}
