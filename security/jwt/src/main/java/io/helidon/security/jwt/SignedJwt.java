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

package io.helidon.security.jwt;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReaderFactory;

import io.helidon.common.Errors;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.jwt.jwk.JwkKeys;

/**
 * The JWT used to transfer content across network - e.g. the base64 parts concatenated
 * with a dot.
 */
public final class SignedJwt {
    private static final Pattern JWT_PATTERN = Pattern
            .compile("([a-zA-Z0-9/=+]+)\\.([a-zA-Z0-9/=+]+)\\.([a-zA-Z0-9_\\-/=+]*)");
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder();
    private static final JsonReaderFactory JSON = Json.createReaderFactory(Collections.emptyMap());

    private final String tokenContent;
    private final JsonObject headerJson;
    private final JsonObject payloadJson;
    private final byte[] signedBytes;
    private final byte[] signature;

    private SignedJwt(String tokenContent, JsonObject headerJson, JsonObject payloadJson, byte[] signedBytes, byte[] signature) {
        this.tokenContent = tokenContent;
        this.headerJson = headerJson;
        this.payloadJson = payloadJson;
        this.signedBytes = signedBytes;
        this.signature = signature;
    }

    /**
     * Sign a jwt using a key obtained based on kid from {@link JwkKeys}.
     * In case the kid is not provided and alg is none, {@link Jwk#ALG_NONE}
     * is used - e.g. no signature is generated.
     *
     * @param jwt  jwt to sign
     * @param jwks keys to find the correct key to sign
     * @return a new instance of this class with signature
     * @throws JwtException in case the algorithm is missing,
     *                      the algorithms of JWK and JWT do not match, or in case of other mis-matches
     */
    public static SignedJwt sign(Jwt jwt, JwkKeys jwks) throws JwtException {
        return jwt.algorithm()
                .map(alg -> sign(jwt, jwks, alg))
                .orElseGet(() -> {
                    // If key id is present, but no algorithm is defined, try to use the default alg of the jwk
                    return jwt.keyId()
                            // key id is defined
                            .map(kid -> jwks.forKeyId(kid)
                                    .map(jwk -> sign(jwt, jwk))
                                    .orElseThrow(() -> new JwtException("Could not find JWK based on key id. JWT: " + jwt
                                                                                + ", kid: " + kid)))
                            .orElseGet(() -> sign(jwt, Jwk.NONE_JWK));
                });
    }

    /**
     * Sign a jwt using an explicit jwk.
     *
     * @param jwt jwt to sign
     * @param jwk key used to sign the JWT
     * @return a new instance of this class with signature
     * @throws JwtException in case the algorithm is missing,
     *                      the algorithms of JWK and JWT do not match, or in case of other mis-matches
     */
    public static SignedJwt sign(Jwt jwt, Jwk jwk) throws JwtException {
        JsonObject headerJson = jwt.headerJson();
        JsonObject payloadJson = jwt.payloadJson();

        // now serialize to string
        String headerJsonString = headerJson.toString();
        String payloadJsonString = payloadJson.toString();

        String headerBase64 = encode(headerJsonString);
        String payloadBase64 = encode(payloadJsonString);

        String signedString = headerBase64 + '.' + payloadBase64;
        byte[] signedBytes = signedString.getBytes(StandardCharsets.UTF_8);

        byte[] signature = jwk.sign(signedBytes);
        String signatureBase64 = encode(signature);

        String tokenContent = signedString + '.' + signatureBase64;

        return new SignedJwt(tokenContent, headerJson, payloadJson, signedBytes, signature);
    }

    private static SignedJwt sign(Jwt jwt, JwkKeys jwks, String alg) {
        Jwk jwk = jwt.keyId()
                // if key id is defined, find it from keys
                .map(kid -> jwks.forKeyId(kid).orElseThrow(() -> new JwtException("Could not find JWK for kid: " + kid)))
                // else check that alg is none, if so, use none
                .orElseGet(() -> {
                    if (Jwk.ALG_NONE.equals(alg)) {
                        return Jwk.NONE_JWK;
                    } else {
                        throw new JwtException("JWT defined with signature algorithm " + alg + ", yet no key id (kid): " + jwt);
                    }
                });

        return sign(jwt, jwk);
    }

    /**
     * Parse a token received over network. The expected content is
     * {@code header_base64.payload_base64.signature_base64} where base64 is
     * base64 URL encoding.
     * This method does NO validation of content at all, only validates that
     * the content is correctly formatted:
     * <ul>
     * <li>correct format of string (e.g. base64.base64.base64)</li>
     * <li>each base64 part is actually base64 URL encoded</li>
     * <li>header and payload are JSON objects</li>
     * </ul>
     *
     * @param tokenContent String with the token
     * @return a signed JWT instance that can be used to obtain the {@link #getJwt() instance}
     *         and to {@link #verifySignature(JwkKeys)} verify} the signature
     * @throws RuntimeException in case of invalid content, see {@link Errors.ErrorMessagesException}
     */
    public static SignedJwt parseToken(String tokenContent) {
        Errors.Collector collector = Errors.collector();

        Matcher matcher = JWT_PATTERN.matcher(tokenContent);
        if (matcher.matches()) {
            String headerBase64 = matcher.group(1);
            String payloadBase64 = matcher.group(2);
            String signatureBase64 = matcher.group(3);

            // these all can fail
            String headerJsonString = decode(headerBase64, collector, "JWT header");
            String payloadJsonString = decode(payloadBase64, collector, "JWT payload");
            byte[] signatureBytes = decodeBytes(signatureBase64, collector, "JWT signature");

            // if failed, do not continue
            collector.collect().checkValid();

            String signedContent = headerBase64 + '.' + payloadBase64;

            JsonObject headerJson = parseJson(headerJsonString, collector, headerBase64, "JWT header");
            JsonObject contentJson = parseJson(payloadJsonString, collector, payloadBase64, "JWT payload");

            collector.collect().checkValid();

            return new SignedJwt(
                    tokenContent,
                    headerJson,
                    contentJson,
                    signedContent.getBytes(StandardCharsets.UTF_8),
                    signatureBytes);
        } else {
            throw new JwtException("Not a JWT token: " + tokenContent);
        }
    }

    private static JsonObject parseJson(String jsonString, Errors.Collector collector, String base64, String description) {
        try {
            return JSON.createReader(new StringReader(jsonString)).readObject();
        } catch (Exception e) {
            collector.fatal(base64, description + " is not a valid JSON object (value is base64 encoded)");
            return null;
        }
    }

    private static String encode(String string) {
        return encode(string.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] bytes) {
        return URL_ENCODER.encodeToString(bytes);
    }

    private static String decode(String base64, Errors.Collector collector, String description) {
        try {
            return new String(URL_DECODER.decode(base64), StandardCharsets.UTF_8);
        } catch (Exception e) {
            collector.fatal(base64, description + " is not a base64 encoded string.");
            return null;
        }
    }

    private static byte[] decodeBytes(String base64, Errors.Collector collector, String description) {
        try {
            return URL_DECODER.decode(base64);
        } catch (Exception e) {
            collector.fatal(base64, description + " is not a base64 encoded string.");
            return null;
        }
    }

    /**
     * The full token (header, payload, signature).
     *
     * @return token content
     */
    public String tokenContent() {
        return tokenContent;
    }

    /**
     * Header JSON.
     *
     * @return header json
     */
    JsonObject headerJson() {
        return headerJson;
    }

    /**
     * Payload JSON.
     *
     * @return payload JSON
     */
    JsonObject payloadJson() {
        return payloadJson;
    }

    /**
     * The bytes that were signed (payload bytes).
     *
     * @return signed bytes
     */
    public byte[] getSignedBytes() {
        return Arrays.copyOf(signedBytes, signedBytes.length);
    }

    /**
     * Signature bytes.
     *
     * @return bytes of the signature
     */
    public byte[] getSignature() {
        return Arrays.copyOf(signature, signature.length);
    }

    /**
     * Return a Jwt instance from this signed JWT.
     *
     * @return Jwt instance
     * @throws RuntimeException in case one of the fields has invalid content (e.g. timestamp is invalid)
     */
    public Jwt getJwt() {
        return new Jwt(headerJson, payloadJson);
    }

    /**
     * Verify signature against the provided keys (the kid of this
     * JWT should be present in the {@link JwkKeys} provided).
     *
     * @param keys JwkKeys to obtain a key to verify signature
     * @return Errors with collected messages, see {@link Errors#isValid()} and {@link Errors#checkValid()}
     */
    public Errors verifySignature(JwkKeys keys) {
        return verifySignature(keys, null);
    }

    /**
     * Verify signature against the provided keys (the kid of thisPrincipal
     * JWT should be present in the {@link JwkKeys} provided).
     *
     * @param keys       JwkKeys to obtain a key to verify signature
     * @param defaultJwk Default value of JWK
     * @return Errors with collected messages, see {@link Errors#isValid()} and {@link Errors#checkValid()}
     */
    public Errors verifySignature(JwkKeys keys, Jwk defaultJwk) {
        Errors.Collector collector = Errors.collector();

        String alg = JwtUtil.getString(headerJson, "alg").orElse(null);
        String kid = JwtUtil.getString(headerJson, "kid").orElse(null);

        Jwk jwk = null;
        boolean jwtWithoutKidAndNoneAlg = false;

        // TODO support multiple JWK unders same kid if different alg (see if spec allows this)
        if (null == alg) {
            if (null == kid) {
                if (defaultJwk == null) {
                    jwtWithoutKidAndNoneAlg = true;
                    jwk = Jwk.NONE_JWK;
                } else {
                    jwk = defaultJwk;
                }
                alg = jwk.algorithm();
            } else {
                //null alg, non-null kid - will use alg of jwk
                jwk = keys.forKeyId(kid).orElse(null);
                if (null == jwk) {
                    if (null == defaultJwk) {
                        collector.fatal(keys, "Key for key id: " + kid + " not found");
                    } else {
                        jwk = defaultJwk;
                    }
                }
                if (null != jwk) {
                    alg = jwk.algorithm();
                }
            }
        } else {
            //alg not null
            if (null == kid) {
                if (Jwk.ALG_NONE.equals(alg)) {
                    if (null != defaultJwk) {
                        if (defaultJwk.algorithm().equals(alg)) {
                            // yes, we expect none algorithm
                        } else {
                            collector.fatal("Algorithm is " + alg + ", default jwk requires " + defaultJwk.algorithm());
                        }
                    } else {
                        jwk = Jwk.NONE_JWK;
                        jwtWithoutKidAndNoneAlg = true;
                    }
                } else {
                    jwk = defaultJwk;
                    if (null == jwk) {
                        collector.fatal("Algorithm is " + alg + ", yet no kid is defined in JWT header, cannot validate");
                    }
                }
            } else {
                //both not null
                jwk = keys.forKeyId(kid).orElse(null);
                if (null == jwk) {
                    if ((null != defaultJwk) && alg.equals(defaultJwk.algorithm())) {
                        jwk = defaultJwk;
                    }
                    if (null == jwk) {
                        collector.fatal(keys, "Key for key id: " + kid + " not found");
                    }
                }
            }
        }

        if (null == jwk) {
            return collector.collect();
        }

        if (jwtWithoutKidAndNoneAlg) {
            collector.fatal(jwk, "None algorithm not allowed, unless specified as the default JWK");
        }

        // now if jwk algorithm is none, alg may be
        if (jwk.algorithm().equals(alg)) {
            try {
                if (!jwk.verifySignature(signedBytes, signature)) {
                    collector.fatal(jwk, "Signature of JWT token is not valid, based on alg: " + alg + ", kid: " + kid);
                }
            } catch (Exception e) {
                collector.fatal(jwk,
                                "Failed to verify signature due to an exception: " + e.getClass().getName() + ": " + e
                                        .getMessage());
            }
        } else {
            collector.fatal(jwk,
                            "Algorithm of JWK (" + jwk
                                    .algorithm() + ") does not match algorithm of this JWT (" + alg + ") for kid: " + kid);
        }

        return collector.collect();
    }
}
