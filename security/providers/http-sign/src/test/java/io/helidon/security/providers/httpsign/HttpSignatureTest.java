/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
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
public class HttpSignatureTest {
    @Test
    public void testValid() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"rsa-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\"";

        testValid(validSignature);
    }

    @Test
    public void testValidInvalidComponent() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"rsa-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",hurhur=\"ignored\"";

        testValid(validSignature);
    }

    @Test
    public void testValidRepeatedComponent() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"hamc-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",algorithm=\"rsa-sha256\"";

        testValid(validSignature);
    }

    @Test
    public void testValidInvalidLastComponent1() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"hamc-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",algorithm=\"rsa-sha256\",abcd";

        testValid(validSignature);
    }

    @Test
    public void testValidInvalidLastComponent2() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"hamc-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",algorithm=\"rsa-sha256\",abcd=";

        testValid(validSignature);
    }

    @Test
    public void testValidInvalidLastComponent3() {
        String validSignature = "keyId=\"rsa-key-1\",algorithm=\"hamc-sha256\","
                + "headers=\"(request-target) host date digest content-length\","
                + "signature=\"Base64(RSA-SHA256(signing string))\",algorithm=\"rsa-sha256\",abcd=\"asf";

        testValid(validSignature);
    }

    @Test
    public void testInvalid1() {
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
    public void testInvalid2() {
        String invalidSignature = "This is a wrong signature";

        HttpSignature httpSignature = HttpSignature.fromHeader(invalidSignature, false);
        Optional<String> validate = httpSignature.validate();

        validate.ifPresentOrElse(msg -> assertThat(msg, containsString("keyId is a mandatory")),
                                 () -> fail("Should have failed validation"));
    }

    @Test
    public void testSignRsa() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("DATE", List.of("Thu, 08 Jun 2014 18:32:30 GMT"));
        headers.put("Authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));
        headers.put("host", List.of("example.org"));

        SecurityEnvironment env = buildSecurityEnv("/my/resource", headers);
        OutboundTargetDefinition outboundDef = OutboundTargetDefinition.builder("rsa-key-12345")
                .privateKeyConfig(KeyConfig.keystoreBuilder()
                                          .keystore(Resource.create(Paths.get("src/test/resources/keystore.p12")))
                                          .keystorePassphrase("password".toCharArray())
                                          .keyAlias("myPrivateKey")
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
    public void testSignHmac() {
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
    public void testSignHmacAddHeaders() {
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
    public void testVerifyRsa() {
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
                .publicKeyConfig(KeyConfig.keystoreBuilder()
                                         .keystore(Resource.create(Paths.get("src/test/resources/keystore.p12")))
                                         .keystorePassphrase("password".toCharArray())
                                         .certAlias("service_cert")
                                         .build())
                .build();

        signature.validate(buildSecurityEnv("/my/resource", headers),
                           inboundClientDef,
                           List.of("date"))
                .ifPresent(Assertions::fail);
    }

    @Test
    public void testVerifyHmac() {
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
                           List.of("date"))
                .ifPresent(Assertions::fail);
    }

    private SecurityEnvironment buildSecurityEnv(String path, Map<String, List<String>> headers) {
        return SecurityEnvironment.builder()
                .path(path)
                .headers(headers)
                .build();
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
