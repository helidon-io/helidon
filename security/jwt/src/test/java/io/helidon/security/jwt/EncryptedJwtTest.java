/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

package io.helidon.security.jwt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.common.Errors;
import io.helidon.common.configurable.Resource;
import io.helidon.security.jwt.EncryptedJwt.SupportedAlgorithm;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.jwt.jwk.JwkRSA;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.security.jwt.EncryptedJwt.SupportedEncryption;
import static io.helidon.security.jwt.EncryptedJwt.builder;
import static io.helidon.security.jwt.EncryptedJwt.parseToken;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Encrypted JWT tests.
 */
public class EncryptedJwtTest {

    private static final List<CbcHmacParameters> CBC_HMAC_PARAMETERS = List.of(
            new CbcHmacParameters(SupportedEncryption.A128CBC_HS256, "HmacSHA256", 16),
            new CbcHmacParameters(SupportedEncryption.A192CBC_HS384, "HmacSHA384", 24),
            new CbcHmacParameters(SupportedEncryption.A256CBC_HS512, "HmacSHA512", 32));
    private static final String RSA1_5_UNSUPPORTED = "RSA1_5 is not supported for JWE key management.";

    private static JwkKeys jwkKeys;
    private static SignedJwt signedJwt;

    @BeforeAll
    public static void init() {
        jwkKeys = JwkKeys.builder()
                .resource(Resource.create("jwk_data.json"))
                .build();

        Jwt jwt = Jwt.builder()
                .addAudience("test")
                .email("unit@test.example")
                .algorithm("RS256")
                .keyId("cc34c0a0-bd5a-4a3c-a50d-a2a7db7643df")
                .issuer("unit-test")
                .build();

        signedJwt = SignedJwt.sign(jwt, jwkKeys);
    }

    @Test
    public void testDefaultHeaderCreation() {
        String kid = "RS_512";
        EncryptedJwt encryptedJwt = builder(signedJwt).jwks(jwkKeys, kid).build();
        JwtHeaders headers = encryptedJwt.headers();
        assertThat(headers.algorithm(), is(Optional.of(SupportedAlgorithm.RSA_OAEP.toString())));
        assertThat(headers.encryption(), is(Optional.of(SupportedEncryption.A256GCM.toString())));
        assertThat(headers.contentType(), is(Optional.of("JWT")));
        assertThat(headers.keyId(), is(Optional.of(kid)));

        headers = JwtHeaders.parseToken(encryptedJwt.token());
        assertThat(headers.algorithm(), is(Optional.of(SupportedAlgorithm.RSA_OAEP.toString())));
        assertThat(headers.encryption(), is(Optional.of(SupportedEncryption.A256GCM.toString())));
        assertThat(headers.contentType(), is(Optional.of("JWT")));
        assertThat(headers.keyId(), is(Optional.of(kid)));
    }

    @Test
    public void testCustomHeaderCreation() {
        String kid = "RS_512";
        SupportedAlgorithm rsaAlgorithm = SupportedAlgorithm.RSA_OAEP_256;
        SupportedEncryption aesAlgorithm = SupportedEncryption.A256CBC_HS512;
        EncryptedJwt encryptedJwt = builder(signedJwt)
                .jwks(jwkKeys, kid)
                .algorithm(rsaAlgorithm)
                .encryption(aesAlgorithm)
                .build();
        JsonObject headers = encryptedJwt.headers().headerJson();
        assertThat(headers.getString("alg"), is(rsaAlgorithm.toString()));
        assertThat(headers.getString("enc"), is(aesAlgorithm.toString()));
        assertThat(headers.getString("cty"), is("JWT"));
        assertThat(headers.getString("kid"), is(kid));
    }

    @Test
    public void testDefaultEncryptAndDecrypt() {
        EncryptedJwt encryptedOne = builder(signedJwt).jwks(jwkKeys, "RS_512").build();
        EncryptedJwt encryptedSecond = builder(signedJwt).jwks(jwkKeys, "RS_512").build();
        assertThat(encryptedOne.token(), not(encryptedSecond.token()));

        EncryptedJwt encryptedJwt = parseToken(encryptedOne.token());
        SignedJwt decryptedOne = encryptedJwt.decrypt(jwkKeys);
        EncryptedJwt encryptedJwt2 = parseToken(encryptedSecond.token());
        SignedJwt decryptedTwo = encryptedJwt2.decrypt(jwkKeys);
        assertThat(decryptedOne.headerJson(), is(decryptedTwo.headerJson()));
    }

    @Test
    public void testCustomEncryptAndDecrypt() {
        EncryptedJwt encryptedOne = builder(signedJwt)
                .jwks(jwkKeys, "RS_512")
                .algorithm(SupportedAlgorithm.RSA_OAEP)
                .encryption(SupportedEncryption.A256CBC_HS512)
                .build();
        EncryptedJwt encryptedSecond = builder(signedJwt)
                .jwks(jwkKeys, "RS_512")
                .algorithm(SupportedAlgorithm.RSA_OAEP_256)
                .encryption(SupportedEncryption.A128CBC_HS256)
                .build();
        assertThat(encryptedOne.token(), not(encryptedSecond.token()));

        EncryptedJwt encryptedJwt = parseToken(encryptedOne.token());
        SignedJwt decryptedOne = encryptedJwt.decrypt(jwkKeys);
        EncryptedJwt encryptedJwt2 = parseToken(encryptedSecond.token());
        SignedJwt decryptedTwo = encryptedJwt2.decrypt(jwkKeys);

        assertThat(decryptedOne.headerJson(), is(decryptedTwo.headerJson()));
    }

    @Test
    public void testPreparsedHeadersFromToken() {
        for (SupportedEncryption encryption : SupportedEncryption.values()) {
            EncryptedJwt encryptedJwt = builder(signedJwt)
                    .jwks(jwkKeys, "RS_512")
                    .encryption(encryption)
                    .build();
            String token = encryptedJwt.token();
            JwtHeaders headers = JwtHeaders.parseToken(token);

            EncryptedJwt parsed = parseToken(headers, token);
            assertThat(parsed.decrypt(jwkKeys).tokenContent(), is(signedJwt.tokenContent()));
        }
    }

    @Test
    public void testPreparsedHeadersAllowReorderedClaims() {
        for (SupportedEncryption encryption : SupportedEncryption.values()) {
            EncryptedJwt encryptedJwt = builder(signedJwt)
                    .jwks(jwkKeys, "RS_512")
                    .encryption(encryption)
                    .build();
            JwtHeaders suppliedHeaders = JwtHeaders.builder()
                    .keyId("RS_512")
                    .algorithm(SupportedAlgorithm.RSA_OAEP.toString())
                    .encryption(encryption.toString())
                    .contentType("JWT")
                    .build();

            assertThat(suppliedHeaders.headerJson(), is(encryptedJwt.headers().headerJson()));
            assertThat(suppliedHeaders.headerJson().toString(), not(encryptedJwt.headers().headerJson().toString()));

            EncryptedJwt parsed = parseToken(suppliedHeaders, encryptedJwt.token());
            assertThat(parsed.headers().headerJson().toString(), is(encryptedJwt.headers().headerJson().toString()));
            assertThat(parsed.decrypt(jwkKeys).tokenContent(), is(signedJwt.tokenContent()));
        }
    }

    @Test
    public void testPreparsedHeadersCannotChangeContentType() {
        for (SupportedEncryption encryption : SupportedEncryption.values()) {
            EncryptedJwt encryptedJwt = builder(signedJwt)
                    .jwks(jwkKeys, "RS_512")
                    .encryption(encryption)
                    .build();
            JwtHeaders headers = JwtHeaders.builder()
                    .algorithm(SupportedAlgorithm.RSA_OAEP.toString())
                    .encryption(encryption.toString())
                    .keyId("RS_512")
                    .contentType("text/plain")
                    .build();

            JwtException exception = assertThrows(JwtException.class,
                                                  () -> parseToken(headers, encryptedJwt.token()).decrypt(jwkKeys),
                                                  encryption.toString());
            assertThat(exception.getMessage(), is("Provided JWT header does not match the token protected header"));
        }
    }

    @Test
    public void testPreparsedHeadersCannotAddClaims() {
        EncryptedJwt encryptedJwt = builder(signedJwt).jwks(jwkKeys, "RS_512").build();
        JwtHeaders headers = JwtHeaders.builder()
                .algorithm(SupportedAlgorithm.RSA_OAEP.toString())
                .encryption(SupportedEncryption.A256GCM.toString())
                .keyId("RS_512")
                .contentType("JWT")
                .addHeaderClaim("custom", "unauthenticated")
                .build();

        JwtException exception = assertThrows(JwtException.class,
                                              () -> parseToken(headers, encryptedJwt.token()).decrypt(jwkKeys));
        assertThat(exception.getMessage(), is("Provided JWT header does not match the token protected header"));
    }

    @Test
    public void testCbcHmacRfc7518Construction() throws Exception {
        JwkRSA jwk = (JwkRSA) jwkKeys.forKeyId("RS_512").orElseThrow();
        for (CbcHmacParameters parameters : CBC_HMAC_PARAMETERS) {
            EncryptedJwt encryptedJwt = builder(signedJwt)
                    .jwk(jwk)
                    .algorithm(SupportedAlgorithm.RSA_OAEP)
                    .encryption(parameters.encryption())
                    .build();
            String[] tokenParts = encryptedJwt.token().split("\\.", -1);

            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            rsaCipher.init(Cipher.DECRYPT_MODE, jwk.privateKey().orElseThrow());
            byte[] contentEncryptionKey = rsaCipher.doFinal(encryptedJwt.encryptedKey());
            assertThat(contentEncryptionKey.length, is(parameters.keyLength() * 2));

            byte[] macKey = Arrays.copyOfRange(contentEncryptionKey, 0, parameters.keyLength());
            byte[] encKey = Arrays.copyOfRange(contentEncryptionKey,
                                               parameters.keyLength(),
                                               contentEncryptionKey.length);
            byte[] aad = tokenParts[0].getBytes(StandardCharsets.US_ASCII);
            byte[] al = ByteBuffer.allocate(Long.BYTES)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putLong(aad.length * 8L)
                    .array();

            Mac mac = Mac.getInstance(parameters.hmac());
            mac.init(new SecretKeySpec(macKey, parameters.hmac()));
            mac.update(aad);
            mac.update(encryptedJwt.iv());
            mac.update(encryptedJwt.encryptedPayload());
            mac.update(al);
            byte[] expectedTag = Arrays.copyOf(mac.doFinal(), parameters.keyLength());
            assertThat(encryptedJwt.authTag(), is(expectedTag));

            Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            aesCipher.init(Cipher.DECRYPT_MODE,
                           new SecretKeySpec(encKey, "AES"),
                           new IvParameterSpec(encryptedJwt.iv()));
            String decryptedPayload = new String(aesCipher.doFinal(encryptedJwt.encryptedPayload()),
                                                 StandardCharsets.UTF_8);
            assertThat(decryptedPayload, is(signedJwt.tokenContent()));
        }
    }

    @Test
    public void testCbcHmacRejectsModifiedIv() {
        Base64.Decoder decoder = Base64.getUrlDecoder();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        for (CbcHmacParameters parameters : CBC_HMAC_PARAMETERS) {
            EncryptedJwt encryptedJwt = builder(signedJwt)
                    .jwks(jwkKeys, "RS_512")
                    .algorithm(SupportedAlgorithm.RSA_OAEP)
                    .encryption(parameters.encryption())
                    .build();
            String[] tokenParts = encryptedJwt.token().split("\\.", -1);
            byte[] iv = decoder.decode(tokenParts[2]);
            iv[0] ^= 1;
            tokenParts[2] = encoder.encodeToString(iv);

            JwtException exception = assertThrows(JwtException.class,
                                                   () -> parseToken(String.join(".", tokenParts)).decrypt(jwkKeys),
                                                   parameters.encryption().toString());
            assertThat(exception.getMessage(), containsString("HMAC signature does not match"));
        }
    }

    @Test
    public void testCbcHmacRejectsModifiedProtectedHeaderRepresentation() {
        Base64.Decoder decoder = Base64.getUrlDecoder();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        EncryptedJwt encryptedJwt = builder(signedJwt)
                .jwks(jwkKeys, "RS_512")
                .algorithm(SupportedAlgorithm.RSA_OAEP)
                .encryption(SupportedEncryption.A128CBC_HS256)
                .build();
        String[] tokenParts = encryptedJwt.token().split("\\.", -1);
        String protectedHeader = new String(decoder.decode(tokenParts[0]), StandardCharsets.UTF_8);
        tokenParts[0] = encoder.encodeToString((" " + protectedHeader).getBytes(StandardCharsets.UTF_8));

        JwtException exception = assertThrows(JwtException.class,
                                               () -> parseToken(String.join(".", tokenParts)).decrypt(jwkKeys));
        assertThat(exception.getMessage(), containsString("HMAC signature does not match"));
    }

    @Test
    public void testCbcHmacRejectsInvalidContentEncryptionKeyLengths() throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        JwkRSA jwk = (JwkRSA) jwkKeys.forKeyId("RS_512").orElseThrow();
        for (CbcHmacParameters parameters : CBC_HMAC_PARAMETERS) {
            EncryptedJwt encryptedJwt = builder(signedJwt)
                    .jwk(jwk)
                    .algorithm(SupportedAlgorithm.RSA_OAEP)
                    .encryption(parameters.encryption())
                    .build();
            String[] tokenParts = encryptedJwt.token().split("\\.", -1);
            int expectedKeyLength = parameters.keyLength() * 2;

            for (int keyLength : new int[] {expectedKeyLength - 1, expectedKeyLength + 1}) {
                Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, jwk.publicKey());
                tokenParts[1] = encoder.encodeToString(cipher.doFinal(new byte[keyLength]));

                JwtException exception = assertThrows(JwtException.class,
                                                       () -> parseToken(String.join(".", tokenParts)).decrypt(jwk),
                                                       keyLength + "-byte content encryption key");
                assertThat(exception.getMessage(), containsString("Invalid content encryption key length"));
            }
        }
    }

    @Test
    public void testRsaPkcs1KeyManagementDecryptRejected() {
        Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
        String headerJson = "{\"alg\":\"RSA1_5\",\"enc\":\"A128GCM\",\"cty\":\"JWT\",\"kid\":\"RS_512\"}";
        String headerBase64 = urlEncoder.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String token = headerBase64 + ".AA.AA.AA.AA";
        EncryptedJwt encryptedJwt = parseToken(token);

        Errors.ErrorMessagesException exception = assertThrows(Errors.ErrorMessagesException.class,
                                                               () -> encryptedJwt.decrypt(jwkKeys));
        assertThat(exception.getMessage(), containsString(RSA1_5_UNSUPPORTED));
    }

    @Test
    @SuppressWarnings("removal")
    public void testRsaPkcs1KeyManagementBuildRejected() {
        JwtException exception = assertThrows(JwtException.class, () -> builder(signedJwt)
                .jwks(jwkKeys, "RS_512")
                .algorithm(SupportedAlgorithm.RSA1_5)
                .build());
        assertThat(exception.getMessage(), containsString(RSA1_5_UNSUPPORTED));
    }

    private record CbcHmacParameters(SupportedEncryption encryption, String hmac, int keyLength) {
    }

}
