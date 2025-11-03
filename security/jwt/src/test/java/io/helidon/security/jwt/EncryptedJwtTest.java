/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Optional;

import io.helidon.common.configurable.Resource;
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
import com.nimbusds.jwt.SignedJWT;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.security.jwt.EncryptedJwt.SupportedEncryption;
import static io.helidon.security.jwt.EncryptedJwt.builder;
import static io.helidon.security.jwt.EncryptedJwt.parseToken;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

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
        SupportedAlgorithm rsaAlgorithm = SupportedAlgorithm.RSA1_5;
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
                .algorithm(SupportedAlgorithm.RSA1_5)
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

        assertThat(decrypted.payloadJson().toString(), is(signedJwt.payloadJson().toString()));
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

        assertThat(signedJWT.getPayload().toString(), is(signedJwt.payloadJson().toString()));
    }

}