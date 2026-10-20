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

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Cipher;

import io.helidon.common.Errors;
import io.helidon.common.configurable.Resource;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.security.jwt.EncryptedJwt.SupportedAlgorithm;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.jwt.jwk.JwkRSA;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.SignedJWT;
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
        JsonObject headers = encryptedJwt.headers().headerJsonObject();
        assertThat(headers.stringValue("alg"), is(Optional.of(rsaAlgorithm.toString())));
        assertThat(headers.stringValue("enc"), is(Optional.of(aesAlgorithm.toString())));
        assertThat(headers.stringValue("cty"), is(Optional.of("JWT")));
        assertThat(headers.stringValue("kid"), is(Optional.of(kid)));
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
        assertThat(decryptedOne.getJwt().headerJsonObject(), is(decryptedTwo.getJwt().headerJsonObject()));
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

        assertThat(decryptedOne.getJwt().headerJsonObject(), is(decryptedTwo.getJwt().headerJsonObject()));
    }

    @Test
    void testCbcHmacRejectsModifiedProtectedHeaderRepresentation() {
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
    void testNimbusProtectedHeaderEncodingIsPreserved() throws ParseException, JOSEException {
        JwkRSA jwk = (JwkRSA) jwkKeys.forKeyId("RS_512").orElseThrow();
        RSAPublicKey publicKey = (RSAPublicKey) jwk.publicKey();
        Payload payload = new Payload(SignedJWT.parse(signedJwt.tokenContent()));
        for (SupportedEncryption encryption : SupportedEncryption.values()) {
            String headerJson = " {\"enc\":\"" + encryption
                    + "\", \"alg\":\"RSA-OAEP-256\", \"kid\":\"RS_512\", \"cty\":\"JWT\"} ";
            Base64URL encodedHeader = Base64URL.encode(headerJson);
            JWEObject jweObject = new JWEObject(JWEHeader.parse(encodedHeader), payload);
            jweObject.encrypt(new RSAEncrypter(publicKey));
            String token = jweObject.serialize();
            assertThat(token.split("\\.", -1)[0], is(encodedHeader.toString()));

            SignedJwt decrypted = parseToken(token).decrypt(jwk);
            assertThat(encryption.toString(), decrypted.tokenContent(), is(signedJwt.tokenContent()));

            JwtHeaders headers = JwtHeaders.parseToken(token);
            SignedJwt decryptedWithHeaders = parseToken(headers, token).decrypt(jwk);
            assertThat(encryption.toString(), decryptedWithHeaders.tokenContent(), is(signedJwt.tokenContent()));
        }
    }

    @Test
    void testPreparsedHeadersAllowReorderedAudience() throws ParseException, JOSEException {
        JwkRSA jwk = (JwkRSA) jwkKeys.forKeyId("RS_512").orElseThrow();
        RSAPublicKey publicKey = (RSAPublicKey) jwk.publicKey();
        Payload payload = new Payload(SignedJWT.parse(signedJwt.tokenContent()));
        for (SupportedEncryption encryption : SupportedEncryption.values()) {
            JwtHeaders headers = JwtHeaders.builder()
                    .addHeaderClaim("aud", List.of("client"))
                    .algorithm(SupportedAlgorithm.RSA_OAEP_256.toString())
                    .encryption(encryption.toString())
                    .contentType("JWT")
                    .keyId("RS_512")
                    .build();
            Base64URL encodedHeader = Base64URL.encode(headers.headerJsonObject().toString());
            JWEObject jweObject = new JWEObject(JWEHeader.parse(encodedHeader), payload);
            jweObject.encrypt(new RSAEncrypter(publicKey));
            String token = jweObject.serialize();

            SignedJwt decrypted = parseToken(headers, token).decrypt(jwk);
            assertThat(encryption.toString(), decrypted.tokenContent(), is(signedJwt.tokenContent()));
        }
    }

    @Test
    void testPreparsedHeadersCannotChangeContentType() {
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
    void testPreparsedHeadersCannotAddClaims() {
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
    void testCbcHmacRejectsInvalidContentEncryptionKeyLengths() throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        JwkRSA jwk = (JwkRSA) jwkKeys.forKeyId("RS_512").orElseThrow();
        Map<SupportedEncryption, Integer> keyLengths = Map.of(SupportedEncryption.A128CBC_HS256, 32,
                                                              SupportedEncryption.A192CBC_HS384, 48,
                                                              SupportedEncryption.A256CBC_HS512, 64);
        for (Map.Entry<SupportedEncryption, Integer> entry : keyLengths.entrySet()) {
            EncryptedJwt encryptedJwt = builder(signedJwt)
                    .jwk(jwk)
                    .algorithm(SupportedAlgorithm.RSA_OAEP)
                    .encryption(entry.getKey())
                    .build();
            String[] tokenParts = encryptedJwt.token().split("\\.", -1);
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            rsaCipher.init(Cipher.DECRYPT_MODE, jwk.privateKey().orElseThrow());
            byte[] contentEncryptionKey = rsaCipher.doFinal(encryptedJwt.encryptedKey());
            int expectedKeyLength = entry.getValue();

            for (int keyLength : new int[] {expectedKeyLength - 1, expectedKeyLength + 1}) {
                Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, jwk.publicKey());
                tokenParts[1] = encoder.encodeToString(cipher.doFinal(Arrays.copyOf(contentEncryptionKey, keyLength)));

                JwtException exception = assertThrows(JwtException.class,
                                                      () -> parseToken(String.join(".", tokenParts)).decrypt(jwk),
                                                      keyLength + "-byte content encryption key");
                assertThat(exception.getMessage(), containsString("Invalid content encryption key length"));
            }
        }
    }

    @Test
    void testUnsupportedRsaKeyManagementAlgorithmIsRejected() {
        String rsaPkcs1 = "RSA1_5";
        String headerBase64 = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(JwtHeaders.builder()
                                        .algorithm(rsaPkcs1)
                                        .encryption(SupportedEncryption.A256GCM.toString())
                                        .contentType("JWT")
                                        .keyId("RS_512")
                                        .build()
                                        .headerJsonObject()
                                        .toString()
                                        .getBytes(StandardCharsets.UTF_8));
        EncryptedJwt encryptedJwt = parseToken(headerBase64 + ".AA.AA.AA.AA");

        assertThat(encryptedJwt.headers().algorithm(), is(Optional.of(rsaPkcs1)));
        Errors.ErrorMessagesException exception =
                assertThrows(Errors.ErrorMessagesException.class, () -> encryptedJwt.decrypt(jwkKeys));
        assertThat(exception.getMessage(), containsString("Value of the claim alg not supported. alg: RSA1_5"));
    }

    @Test
    @SuppressWarnings("removal")
    void testUnsupportedRsaKeyManagementAlgorithmCannotBeUsedForEncryption() {
        JwtException exception = assertThrows(JwtException.class, () -> builder(signedJwt)
                .jwks(jwkKeys, "RS_512")
                .algorithm(SupportedAlgorithm.RSA1_5)
                .build());
        assertThat(exception.getMessage(), containsString("JWE key encryption algorithm is not supported: RSA1_5"));
    }

    @Test
    void testInvalidJweFormatDoesNotRevealToken() {
        String token = "opaque-secret-access-token";

        JwtException exception = assertThrows(JwtException.class, () -> EncryptedJwt.parseToken(token));
        assertThat(exception.getMessage(), is("Not a JWE token"));
        assertThat(exception.getMessage(), not(containsString(token)));

        JwtHeaders headers = JwtHeaders.builder()
                .algorithm(SupportedAlgorithm.RSA_OAEP.toString())
                .encryption(SupportedEncryption.A256GCM.toString())
                .build();
        exception = assertThrows(JwtException.class, () -> EncryptedJwt.parseToken(headers, token));
        assertThat(exception.getMessage(), is("Not a JWE token"));
        assertThat(exception.getMessage(), not(containsString(token)));
    }

    @Test
    void testInvalidJweSegmentsDoNotRevealTokenParts() {
        String validHeader = base64Url(JwtHeaders.builder()
                                             .algorithm(SupportedAlgorithm.RSA_OAEP.toString())
                                             .encryption(SupportedEncryption.A256GCM.toString())
                                             .build()
                                             .headerJsonObject()
                                             .toString());
        String validPart = "AA";
        String header = "secret+jweHeader";
        String encryptedKey = "secret+jweEncryptedKey";
        String iv = "secret+jweIv";
        String payload = "secret+jwePayload";
        String authTag = "secret+jweAuthTag";
        String headerToken = header + "." + validPart + "." + validPart + "." + validPart + "." + validPart;

        Errors.ErrorMessagesException exception =
                assertThrows(Errors.ErrorMessagesException.class, () -> EncryptedJwt.parseToken(headerToken));
        assertRedactedError(exception, JwtTokenPart.JWT_HEADER, headerToken, header);

        String encryptedKeyToken = validHeader + "." + encryptedKey + "." + validPart + "." + validPart + "." + validPart;
        exception = assertThrows(Errors.ErrorMessagesException.class, () -> EncryptedJwt.parseToken(encryptedKeyToken));
        assertRedactedError(exception, JwtTokenPart.JWE_ENCRYPTED_KEY, encryptedKeyToken, encryptedKey);

        JwtHeaders headers = JwtHeaders.builder()
                .algorithm(SupportedAlgorithm.RSA_OAEP.toString())
                .encryption(SupportedEncryption.A256GCM.toString())
                .build();
        exception = assertThrows(Errors.ErrorMessagesException.class,
                                 () -> EncryptedJwt.parseToken(headers, encryptedKeyToken));
        assertRedactedError(exception, JwtTokenPart.JWE_ENCRYPTED_KEY, encryptedKeyToken, encryptedKey);

        String ivToken = validHeader + "." + validPart + "." + iv + "." + validPart + "." + validPart;
        exception = assertThrows(Errors.ErrorMessagesException.class, () -> EncryptedJwt.parseToken(ivToken));
        assertRedactedError(exception, JwtTokenPart.JWE_INITIALIZATION_VECTOR, ivToken, iv);

        exception = assertThrows(Errors.ErrorMessagesException.class, () -> EncryptedJwt.parseToken(headers, ivToken));
        assertRedactedError(exception, JwtTokenPart.JWE_INITIALIZATION_VECTOR, ivToken, iv);

        String payloadToken = validHeader + "." + validPart + "." + validPart + "." + payload + "." + validPart;
        exception = assertThrows(Errors.ErrorMessagesException.class, () -> EncryptedJwt.parseToken(payloadToken));
        assertRedactedError(exception, JwtTokenPart.JWE_PAYLOAD, payloadToken, payload);

        exception = assertThrows(Errors.ErrorMessagesException.class, () -> EncryptedJwt.parseToken(headers, payloadToken));
        assertRedactedError(exception, JwtTokenPart.JWE_PAYLOAD, payloadToken, payload);

        String authTagToken = validHeader + "." + validPart + "." + validPart + "." + validPart + "." + authTag;
        exception = assertThrows(Errors.ErrorMessagesException.class, () -> EncryptedJwt.parseToken(authTagToken));
        assertRedactedError(exception, JwtTokenPart.JWE_AUTHENTICATION_TAG, authTagToken, authTag);

        exception = assertThrows(Errors.ErrorMessagesException.class, () -> EncryptedJwt.parseToken(headers, authTagToken));
        assertRedactedError(exception, JwtTokenPart.JWE_AUTHENTICATION_TAG, authTagToken, authTag);
    }

    @Test
    void testNimbusToHelidon() throws ParseException, JOSEException {
        JwkRSA jwk = (JwkRSA) jwkKeys.forKeyId("RS_512").orElseThrow();
        RSAPublicKey publicKey = (RSAPublicKey) jwk.publicKey();

        Payload payload = new Payload(SignedJWT.parse(signedJwt.tokenContent()));
        JWEHeader header = new JWEHeader(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256CBC_HS512);
        JWEObject jweObject = new JWEObject(header, payload);
        jweObject.encrypt(new RSAEncrypter(publicKey));
        String serializedJweFromNimbus = jweObject.serialize();

        EncryptedJwt encryptedJwt = parseToken(serializedJweFromNimbus);
        SignedJwt decrypted = encryptedJwt.decrypt(jwk);

        assertThat(decrypted.getJwt().payloadJsonObject(), is(signedJwt.getJwt().payloadJsonObject()));
    }

    @Test
    void testHelidonToNimbus() throws ParseException, JOSEException {
        JwkRSA jwk = (JwkRSA) jwkKeys.forKeyId("RS_512").orElseThrow();
        RSAPrivateKey privateKey = (RSAPrivateKey) jwk.privateKey().orElseThrow();

        EncryptedJwt encryptedJwt = builder(signedJwt)
                .jwk(jwk)
                .algorithm(SupportedAlgorithm.RSA_OAEP_256)
                .encryption(SupportedEncryption.A256CBC_HS512)
                .build();

        JWEObject jweObject = JWEObject.parse(encryptedJwt.token());
        jweObject.decrypt(new RSADecrypter(privateKey));
        SignedJWT signedJWT = SignedJWT.parse(jweObject.getPayload().toString());

        assertThat(JsonParser.create(signedJWT.getPayload().toString()).readJsonObject(),
                   is(signedJwt.getJwt().payloadJsonObject()));
    }

    private static void assertDoesNotContain(String message, String... values) {
        for (String value : values) {
            assertThat(message, not(containsString(value)));
        }
    }

    private static void assertRedactedError(Errors.ErrorMessagesException exception,
                                            JwtTokenPart tokenPart,
                                            String... values) {
        assertThat(exception.getMessage(), containsString(tokenPart.text()));
        assertThat(exception.getMessages().size(), is(1));
        assertThat(exception.getMessages().get(0).getSource(), is(tokenPart));
        assertDoesNotContain(exception.getMessage(), values);
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

}
