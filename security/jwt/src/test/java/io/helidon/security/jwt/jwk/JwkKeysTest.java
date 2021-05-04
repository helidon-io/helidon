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

package io.helidon.security.jwt.jwk;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import io.helidon.common.configurable.Resource;
import io.helidon.security.jwt.JwtException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link JwkKeys}.
 */
public class JwkKeysTest {
    private static JwkKeys customKeys;
    private static JwkKeys googleKeys;
    private static JwkKeys auth0Keys;

    @BeforeAll
    public static void init() {
        customKeys = JwkKeys.builder()
                .resource(Resource.create("jwk_data.json"))
                .build();

        auth0Keys = JwkKeys.builder()
                .resource(Resource.create("auth0-jwk.json"))
                .build();

        googleKeys = JwkKeys.builder()
                .resource(Resource.create("google-jwk.json"))
                .build();
    }

    @Test
    public void testGoogleJwkDocument() {
        Optional<Jwk> key = googleKeys
                .forKeyId("eb29843dd7334cf989e1db6a2b0c6e07a10a9cd3");

        assertThat(key.isPresent(), is(true));

        Jwk jwkKey = key.get();
    }

    @Test
    public void testUseSig() {
        Jwk jwk = customKeys.forKeyId("HS_512").get();

        assertThat(jwk.supports(Jwk.USE_SIGNATURE, Jwk.OPERATION_SIGN), is(true));
        assertThat(jwk.supports(Jwk.USE_SIGNATURE, Jwk.OPERATION_VERIFY), is(true));
        assertThat(jwk.supports(Jwk.USE_ENCRYPTION, Jwk.OPERATION_ENCRYPT), is(false));
        assertThat(jwk.supports(Jwk.USE_ENCRYPTION, Jwk.OPERATION_DECRYPT), is(false));
    }

    @Test
    public void testKeyOpsSig() {
        Jwk jwk = customKeys.forKeyId("hmac-secret-001").get();

        assertThat(jwk.supports(Jwk.USE_SIGNATURE, Jwk.OPERATION_SIGN), is(true));
        assertThat(jwk.supports(Jwk.USE_SIGNATURE, Jwk.OPERATION_VERIFY), is(true));
        assertThat(jwk.supports(Jwk.USE_ENCRYPTION, Jwk.OPERATION_DECRYPT), is(false));
        assertThat(jwk.supports(Jwk.USE_ENCRYPTION, Jwk.OPERATION_ENCRYPT), is(false));
    }

    @Test
    public void testUseEnc() {
        Jwk jwk = customKeys.forKeyId("1").get();

        assertThat(jwk.supports(Jwk.USE_SIGNATURE, Jwk.OPERATION_SIGN), is(false));
        assertThat(jwk.supports(Jwk.USE_SIGNATURE, Jwk.OPERATION_VERIFY), is(false));
        assertThat(jwk.supports(Jwk.USE_ENCRYPTION, Jwk.OPERATION_ENCRYPT), is(true));
        assertThat(jwk.supports(Jwk.USE_ENCRYPTION, Jwk.OPERATION_DECRYPT), is(true));
    }

    @Test
    public void testAuth0JwkDocument() {
        String keyId = "QzBCMDM1QTI2MjRFMTFDNDBDRTYwRkU4RDdEMzU5RTcwNDRBNjhCNQ";
        auth0Keys.forKeyId(keyId).ifPresentOrElse(key -> {
            assertThat(key.algorithm(), is(JwkRSA.ALG_RS256));
            assertThat(key.keyType(), is(Jwk.KEY_TYPE_RSA));
            assertThat(key.usage(), is(Optional.of(Jwk.USE_SIGNATURE)));
            assertThat(key.keyId(), is(keyId));

            assertThat(key, instanceOf(JwkPki.class));
            assertThat(key, instanceOf(JwkRSA.class));

            JwkPki pki = (JwkPki) key;
            assertThat(pki.certificateChain(), not(Optional.empty()));
            assertThat(pki.privateKey(), is(Optional.empty()));
            assertThat(pki.publicKey(), not(Optional.empty()));
            assertThat(pki.sha1Thumbprint(), not(Optional.empty()));
            assertThat(Base64.getUrlEncoder().encodeToString(pki.sha1Thumbprint().get()),
                       is("QzBCMDM1QTI2MjRFMTFDNDBDRTYwRkU4RDdEMzU5RTcwNDRBNjhCNQ=="));
            assertThat(pki.sha256Thumbprint(), is(Optional.empty()));
        }, () -> fail("Key with id \"" + keyId + "\" should be presenit in auth0-jwk.json file"));
    }

    @Test
    public void testSignFailsForEncUse() {
        Jwk jwk = customKeys.forKeyId("1").get();

        try {
            jwk.sign("someBytes".getBytes());
            fail("Signing should have failed as key is for encryption only");
        } catch (JwtException e) {
            //expected
            assertThat(e.getMessage(), endsWith("does not support signing of requests"));
        }
    }

    @Test
    public void testNone() {
        Jwk jwk = customKeys.forKeyId("none").get();

        boolean signed = jwk.verifySignature("test".getBytes(), Jwk.EMPTY_BYTES);
        assertThat(signed, is(true));
    }

    private void testRsa(String keyId, String algorithm) {
        customKeys.forKeyId(keyId).ifPresentOrElse(key -> {
            assertThat(key.algorithm(), is(algorithm));
            assertThat(key.keyType(), is(Jwk.KEY_TYPE_RSA));
            assertThat(key.usage(), is(Optional.of(Jwk.USE_SIGNATURE)));
            assertThat(key.keyId(), is(keyId));

            assertThat(key, instanceOf(JwkPki.class));
            assertThat(key, instanceOf(JwkRSA.class));

            JwkPki pki = (JwkPki) key;
            assertThat(pki.certificateChain(), is(Optional.empty()));
            assertThat(pki.privateKey(), not(Optional.empty()));
            assertThat(pki.publicKey(), not(Optional.empty()));
            assertThat(pki.sha1Thumbprint(), is(Optional.empty()));
            assertThat(pki.sha256Thumbprint(), is(Optional.empty()));

            // now test sign/verify
            byte[] bytes = "someTextToSign 3232".getBytes(StandardCharsets.UTF_8);
            byte[] sig = key.sign(bytes);

            assertThat(sig, notNullValue());
            assertThat(sig.length, not(0));

            assertThat(key.verifySignature(bytes, sig), is(true));
        }, () -> fail("RSA Key with kid \"" + keyId + "\" should be present in "
                              + "jwk_data.json file"));
    }

    @Test
    public void testRsa() {
        testRsa("cc34c0a0-bd5a-4a3c-a50d-a2a7db7643df", JwkRSA.ALG_RS256);
        testRsa("RS_384", JwkRSA.ALG_RS384);
        testRsa("RS_512", JwkRSA.ALG_RS512);
    }

    @Test
    public void testEc() {
        testEc("ec-secret-001", JwkEC.ALG_ES256);
        testEc("ES_384", JwkEC.ALG_ES384);
        testEc("ES_512", JwkEC.ALG_ES512);
    }

    private void testEc(String keyId, String algorithm) {
        customKeys.forKeyId(keyId).ifPresentOrElse(key -> {
            assertThat(key.algorithm(), is(algorithm));
            assertThat(key.keyType(), is(Jwk.KEY_TYPE_EC));
            assertThat(key.usage(), is(Optional.of(Jwk.USE_SIGNATURE)));
            assertThat(key.keyId(), is(keyId));
            assertThat(key.operations(), is(Optional.empty()));

            assertThat(key, instanceOf(JwkPki.class));
            assertThat(key, instanceOf(JwkEC.class));

            JwkPki pki = (JwkPki) key;
            assertThat(pki.certificateChain(), is(Optional.empty()));
            assertThat(pki.privateKey(), not(Optional.empty()));
            assertThat(pki.publicKey(), not(Optional.empty()));
            assertThat(pki.sha1Thumbprint(), is(Optional.empty()));
            assertThat(pki.sha256Thumbprint(), is(Optional.empty()));

            // now test sign/verify
            byte[] bytes = "someTextToSign 3232".getBytes(StandardCharsets.UTF_8);
            byte[] sig = key.sign(bytes);

            assertThat(sig, notNullValue());
            assertThat(sig.length, not(0));

            assertThat(key.verifySignature(bytes, sig), is(true));
        }, () -> fail("Key should be in json: " + keyId));
    }

    @Test
    public void testOct() {
        testOct("HS_384", JwkOctet.ALG_HS384);
        testOct("HS_512", JwkOctet.ALG_HS512);
    }

    private void testOct(String keyId, String algorithm) {
        customKeys.forKeyId(keyId).ifPresentOrElse(key -> {
            assertThat(key.algorithm(), is(algorithm));
            assertThat(key.keyType(), is(Jwk.KEY_TYPE_OCT));
            assertThat(key.usage(), is(Optional.of(Jwk.USE_SIGNATURE)));
            assertThat(key.keyId(), is(keyId));
            assertThat(key.operations(), is(Optional.empty()));

            assertThat(key, instanceOf(JwkOctet.class));
            assertThat(key, not(instanceOf(JwkPki.class)));

            JwkOctet oct = (JwkOctet) key;
            assertThat(oct.getKeyBytes(), notNullValue());
            assertThat(oct.getKeyBytes().length, not(0));

            // now test sign/verify
            byte[] bytes = "someTextToSign 3232".getBytes(StandardCharsets.UTF_8);
            byte[] sig = key.sign(bytes);

            assertThat(sig, notNullValue());
            assertThat(sig.length, not(0));

            assertThat(key.verifySignature(bytes, sig), is(true));
        }, () -> fail("Octet Key with kid \"" + keyId + "\" should be present in "
                              + "jwk_data.json file"));
    }

    @Test
    public void testCustomOct() {
        String keyId = "hmac-secret-001";
        customKeys.forKeyId(keyId).ifPresentOrElse(key -> {
            assertThat(key.algorithm(), is(JwkOctet.ALG_HS256));
            assertThat(key.keyType(), is(Jwk.KEY_TYPE_OCT));
            assertThat(key.usage(), is(Optional.empty()));
            assertThat(key.keyId(), is(keyId));
            assertThat(key.operations(), is(Optional.of(List.of(Jwk.OPERATION_SIGN,
                                                                Jwk.OPERATION_VERIFY))));

            assertThat(key, instanceOf(JwkOctet.class));
            assertThat(key, not(instanceOf(JwkPki.class)));

            JwkOctet oct = (JwkOctet) key;
            assertThat(Base64.getUrlEncoder().encodeToString(oct.getKeyBytes()),
                       is("FdFYFzERwC2uCBB46pZQi4GG85LujR8obt-KWRBICVQ="));

            // now test sign/verify
            byte[] bytes = "someTextToSign 3232".getBytes(StandardCharsets.UTF_8);
            byte[] sig = key.sign(bytes);

            assertThat(sig, notNullValue());
            assertThat(sig.length, not(0));

            assertThat(key.verifySignature(bytes, sig), is(true));
        }, () -> fail("Octet Key with kid \"" + keyId + "\" should be present in "
                              + "jwk_data.json file"));
    }

    @Test
    public void testECUsingBuilder() {
        //hack this a bit, so I do not have to create a key pair in java
        String fileKeyid = "ec-secret-001";
        customKeys.forKeyId(fileKeyid).ifPresentOrElse(keyFromFile -> {
            String keyId = "some_key_id";
            //the key must be an EC key
            JwkEC ec = (JwkEC) keyFromFile;

            JwkEC ecKey = JwkEC.builder()
                    .publicKey((ECPublicKey) ec.publicKey())
                    .privateKey((ECPrivateKey) ec.privateKey().get())
                    .algorithm(JwkEC.ALG_ES256)
                    .keyId(keyId)
                    .usage(Jwk.USE_SIGNATURE)
                    .build();

            JwkKeys keys = JwkKeys.builder()
                    .addKey(ecKey)
                    .build();

            keys.forKeyId(keyId).ifPresentOrElse(key -> {
                assertThat(key.algorithm(), is(JwkEC.ALG_ES256));
                assertThat(key.keyType(), is(Jwk.KEY_TYPE_EC));
                assertThat(key.usage(), is(Optional.of(Jwk.USE_SIGNATURE)));
                assertThat(key.keyId(), is(keyId));
                assertThat(key.operations(), is(Optional.empty()));

                assertThat(key, instanceOf(JwkPki.class));
                assertThat(key, instanceOf(JwkEC.class));

                JwkPki pki = (JwkPki) key;
                assertThat(pki.certificateChain(), is(Optional.empty()));
                assertThat(pki.privateKey(), not(Optional.empty()));
                assertThat(pki.publicKey(), not(Optional.empty()));
                assertThat(pki.sha1Thumbprint(), is(Optional.empty()));
                assertThat(pki.sha256Thumbprint(), is(Optional.empty()));

                // now test sign/verify
                byte[] bytes = "someTextToSign 3232".getBytes(StandardCharsets.UTF_8);
                byte[] sig = key.sign(bytes);

                assertThat(sig, notNullValue());
                assertThat(sig.length, not(0));

                assertThat(key.verifySignature(bytes, sig), is(true));
            }, () -> fail("The key should be present in built keys"));
        }, () -> fail("Key \"" + fileKeyid + "\" should be present in jwk_data.json"));
    }
}
