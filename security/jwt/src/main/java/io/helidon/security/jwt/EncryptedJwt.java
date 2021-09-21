/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.json.Json;
import javax.json.JsonReaderFactory;

import io.helidon.common.Errors;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.jwt.jwk.JwkEC;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.jwt.jwk.JwkRSA;

/**
 * The JWT used to transfer content across network - e.g. the base64 parts concatenated with a dot.
 *
 * The content of the transferred JWT is encrypted by one of the supported ciphers mentioned here {@link SupportedEncryption}.
 * Key for the content encryption is randomly generated and encrypted by selected {@link SupportedAlgorithm} algorithm.
 *
 * A new key and initialization vector is being generated automatically for each encrypted JWT.
 */
public final class EncryptedJwt {

    private static final Map<SupportedAlgorithm, String> RSA_ALGORITHMS;
    private static final Map<SupportedEncryption, AesAlgorithm> CONTENT_ENCRYPTION;

    private static final Pattern JWE_PATTERN = Pattern
            .compile("(^[\\S]+)\\.([\\S]+)\\.([\\S]+)\\.([\\S]+)\\.([\\S]+$)");
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final JsonReaderFactory JSON = Json.createReaderFactory(Collections.emptyMap());

    static {
        RSA_ALGORITHMS = Map.of(SupportedAlgorithm.RSA_OAEP, "RSA/ECB/OAEPWithSHA-1AndMGF1Padding",
                                SupportedAlgorithm.RSA_OAEP_256, "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
                                SupportedAlgorithm.RSA1_5, "RSA/ECB/PKCS1Padding");

        CONTENT_ENCRYPTION = Map.of(SupportedEncryption.A128GCM, new AesGcmAlgorithm(128),
                                    SupportedEncryption.A192GCM, new AesGcmAlgorithm(192),
                                    SupportedEncryption.A256GCM, new AesGcmAlgorithm(256),
                                    SupportedEncryption.A128CBC_HS256,
                                    new AesAlgorithmWithHmac("AES/CBC/PKCS5Padding", 128, 16, "HmacSHA256"),
                                    SupportedEncryption.A192CBC_HS384,
                                    new AesAlgorithmWithHmac("AES/CBC/PKCS5Padding", 192, 16, "HmacSHA384"),
                                    SupportedEncryption.A256CBC_HS512,
                                    new AesAlgorithmWithHmac("AES/CBC/PKCS5Padding", 256, 16, "HmacSHA512"));
    }

    private final String token;
    private final JwtHeaders header;
    private final byte[] iv;
    private final byte[] encryptedKey;
    private final byte[] authTag;
    private final byte[] encryptedPayload;

    private EncryptedJwt(String token,
                         JwtHeaders header,
                         byte[] iv,
                         byte[] encryptedKey,
                         byte[] authTag,
                         byte[] encryptedPayload) {
        this.token = token;
        this.header = header;
        this.iv = iv;
        this.encryptedKey = encryptedKey;
        this.authTag = authTag;
        this.encryptedPayload = encryptedPayload;
    }

    /**
     * Builder of the Encrypted JWT.
     *
     * @param jwt jwt to be encrypted
     * @return encrypted jwt builder instance
     */
    public static Builder builder(SignedJwt jwt) {
        return new Builder(jwt);
    }

    /**
     * Create new EncryptedJwt.
     * Content is encrypted by {@link SupportedEncryption#A256GCM} and content encryption key is
     * encrypted by {@link SupportedAlgorithm#RSA_OAEP} for transportation.
     *
     * @param jwt jwt to be encrypted
     * @param jwk jwk used for content key encryption
     * @return encrypted jwt instance
     */
    public static EncryptedJwt create(SignedJwt jwt, Jwk jwk) {
        return builder(jwt).jwk(jwk).build();
    }

    /**
     * Parse a token received over network. The expected content is
     * {@code jwe_header_base64.encrypted_content_key_base64.iv_base64.content_base64.authentication_tag_base64} where base64 is
     * base64 URL encoding.
     * Use this method if you have previous knowledge of this being an encrypted token.
     * Use {@link #parseToken(JwtHeaders, String)} if header had to be parsed separately to identify token type.
     *
     * This method does NO validation of content at all, only validates that the content is correctly formatted:
     * <ul>
     * <li>correct format of string (e.g. base64.base64.base64.base64.base64)</li>
     * <li>each base64 part is actually base64 URL encoded</li>
     * <li>header is JSON object</li>
     * </ul>
     *
     * @param token String with the token
     * @return Encrypted jwt parts
     * @throws RuntimeException in case of invalid content, see {@link Errors.ErrorMessagesException}
     */
    public static EncryptedJwt parseToken(String token) {
        Errors.Collector collector = Errors.collector();

        Matcher matcher = JWE_PATTERN.matcher(token);
        if (matcher.matches()) {
            String headerBase64 = matcher.group(1);
            String encryptedKeyBase64 = matcher.group(2);
            String ivBase64 = matcher.group(3);
            String payloadBase64 = matcher.group(4);
            String authTagBase64 = matcher.group(5);

            // these all can fail
            JwtHeaders header = JwtHeaders.parseBase64(headerBase64, collector);
            return parse(token, collector, header, encryptedKeyBase64, ivBase64, payloadBase64, authTagBase64);
        } else {
            throw new JwtException("Not a JWE token: " + token);
        }
    }

    /**
     * Parse a token received over network. The expected content is
     * {@code jwe_header_base64.encrypted_content_key_base64.iv_base64.content_base64.authentication_tag_base64} where base64 is
     * base64 URL encoding.
     * Use this method if you have pre-parsed header, otherwise use {@link #parseToken(String)}.
     *
     * This method does NO validation of content at all, only validates that the content is correctly formatted:
     * <ul>
     * <li>correct format of string (e.g. base64.base64.base64.base64.base64)</li>
     * <li>each base64 part is actually base64 URL encoded</li>
     * <li>header is JSON object</li>
     * </ul>
     *
     * @param header parsed JWT header
     * @param token String with the token
     * @return Encrypted jwt parts
     * @throws RuntimeException in case of invalid content, see {@link Errors.ErrorMessagesException}
     */
    public static EncryptedJwt parseToken(JwtHeaders header, String token) {
        Errors.Collector collector = Errors.collector();

        Matcher matcher = JWE_PATTERN.matcher(token);
        if (matcher.matches()) {
            String encryptedKeyBase64 = matcher.group(2);
            String ivBase64 = matcher.group(3);
            String payloadBase64 = matcher.group(4);
            String authTagBase64 = matcher.group(5);

            return parse(token, collector, header, encryptedKeyBase64, ivBase64, payloadBase64, authTagBase64);
        } else {
            throw new JwtException("Not a JWE token: " + token);
        }
    }

    private static EncryptedJwt parse(String token,
                                      Errors.Collector collector,
                                      JwtHeaders header,
                                      String encryptedKeyBase64,
                                      String ivBase64,
                                      String payloadBase64,
                                      String authTagBase64) {
        byte[] encryptedKey = decodeBytes(encryptedKeyBase64, collector, "JWE encrypted key");
        byte[] iv = decodeBytes(ivBase64, collector, "JWE initialization vector");
        byte[] encryptedPayload = decodeBytes(payloadBase64, collector, "JWE payload");
        byte[] authTag = decodeBytes(authTagBase64, collector, "JWE authentication tag");

        // if failed, do not continue
        collector.collect().checkValid();

        return new EncryptedJwt(token, header, iv, encryptedKey, authTag, encryptedPayload);
    }

    private static byte[] encryptRsa(String algorithm, PublicKey publicKey, byte[] unencryptedKey) {
        try {
            Cipher rsaCipher = Cipher.getInstance(algorithm);
            rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return rsaCipher.doFinal(unencryptedKey);
        } catch (Exception e) {
            throw new JwtException("Exception during aes key decryption occurred.", e);
        }
    }

    private static byte[] decryptRsa(String algorithm, PrivateKey privateKey, byte[] encryptedKey) {
        try {
            Cipher rsaCipher = Cipher.getInstance(algorithm);
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
            return rsaCipher.doFinal(encryptedKey);
        } catch (Exception e) {
            throw new JwtException("Exception during aes key decryption occurred.", e);
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
     * Decrypt {@link SignedJwt} from the content of the encrypted jwt.
     * Encrypted JWT needs to have "kid" header specified to be able to determine {@link Jwk} from the
     * {@link JwkKeys} instance.
     *
     * Selected {@link Jwk} needs to have private key set.
     *
     * @param jwkKeys   jwk keys
     * @param collector error collector
     * @return empty optional if any error has occurred or SignedJwt instance if the decryption and validation was successful
     */
    public Optional<SignedJwt> decrypt(JwkKeys jwkKeys, Errors.Collector collector) {
        return decrypt(jwkKeys, null, collector);
    }

    /**
     * Decrypt {@link SignedJwt} from the content of the encrypted jwt.
     * Provided jwk will be used for content key decryption.
     *
     * Provided {@link Jwk} needs to have private key set.
     *
     * @param jwk       jwk keys
     * @param collector error collector
     * @return empty optional if any error has occurred or SignedJwt instance if the decryption and validation was successful
     */
    public Optional<SignedJwt> decrypt(Jwk jwk, Errors.Collector collector) {
        return decrypt(null, jwk, collector);
    }

    /**
     * Decrypt {@link SignedJwt} from the content of the encrypted jwt.
     * If the kid header is specified among encrypted JWT headers, it will be used to match corresponding key
     * from the jwkKeys. If no kid is specified, provided default Jwk is used.
     *
     * Used {@link Jwk} needs to have private key set.
     *
     * @param jwkKeys    jwk keys
     * @param defaultJwk default jwk
     * @param collector  error collector
     * @return empty optional if any error has occurred or SignedJwt instance if the decryption and validation was successful
     */
    public Optional<SignedJwt> decrypt(JwkKeys jwkKeys, Jwk defaultJwk, Errors.Collector collector) {
        String headerBase64 = encode(header.headerJson().toString().getBytes(StandardCharsets.UTF_8));
        String alg = header.algorithm().orElse(null);
        String kid = header.keyId().orElse(null);
        String enc = header.encryption().orElse(null);
        Jwk jwk = null;
        String algorithm = null;
        if (kid != null) {
            if (jwkKeys != null) {
                jwk = jwkKeys.forKeyId(kid).orElse(null);
            } else if (kid.equals(defaultJwk.keyId())) {
                jwk = defaultJwk;
            } else {
                collector.fatal("Could not find JWK for kid: " + kid);
            }
        } else {
            jwk = defaultJwk;
            if (jwk == null) {
                collector.fatal("Could not find any suitable JWK.");
            }
        }

        if (enc == null) {
            collector.fatal("Content encryption algorithm not set.");
        }

        if (alg != null) {
            try {
                SupportedAlgorithm supportedAlgorithm = SupportedAlgorithm.getValue(alg);
                algorithm = RSA_ALGORITHMS.get(supportedAlgorithm);
            } catch (IllegalArgumentException e) {
                collector.fatal("Value of the claim alg not supported! alg: " + alg);
            }
        } else {
            collector.fatal("No alg header was present among JWE headers");
        }

        PrivateKey privateKey = null;
        Jwk finalJwk = jwk;
        if (jwk instanceof JwkRSA) {
            privateKey = ((JwkRSA) jwk).privateKey().orElseGet(() -> {
                collector.fatal("No private key present in RSA JWK kid: " + finalJwk.keyId());
                return null;
            });
        } else if (jwk instanceof JwkEC) {
            privateKey = ((JwkEC) jwk).privateKey().orElseGet(() -> {
                collector.fatal("No private key present in EC JWK kid: " + finalJwk.keyId());
                return null;
            });
        } else if (jwk != null) {
            collector.fatal("Not supported JWK type: " + jwk.keyType() + ", JWK class: " + jwk.getClass().getName());
        }

        if (collector.hasFatal()) {
            return Optional.empty();
        }

        byte[] decryptedKey = decryptRsa(algorithm, privateKey, encryptedKey);
        //Base64 headers are used as an aad. This aad has to be in US_ASCII encoding.
        EncryptionParts encryptionParts = new EncryptionParts(decryptedKey,
                                                              iv,
                                                              headerBase64.getBytes(StandardCharsets.US_ASCII),
                                                              encryptedPayload,
                                                              authTag);
        AesAlgorithm aesAlgorithm;
        try {
            SupportedEncryption supportedEncryption = SupportedEncryption.getValue(enc);
            aesAlgorithm = CONTENT_ENCRYPTION.get(supportedEncryption);
        } catch (IllegalArgumentException e) {
            throw new JwtException("Unsupported content encryption: " + enc);
        }
        String decryptedPayload = new String(aesAlgorithm.decrypt(encryptionParts), StandardCharsets.UTF_8);
        return Optional.of(SignedJwt.parseToken(decryptedPayload));
    }

    /**
     * Encrypted JWT headers.
     *
     * @return headers of the encrypted JWT
     */
    public JwtHeaders headers() {
        return header;
    }

    /**
     * Encrypted JWT as token.
     *
     * @return encrypted jwt token
     */
    public String token() {
        return token;
    }

    /**
     * Initialization vector for the encrypted content.
     *
     * @return initialization vector
     */
    public byte[] iv() {
        return Arrays.copyOf(iv, iv.length);
    }

    /**
     * Encrypted content encryption key.
     *
     * @return content encryption key
     */
    public byte[] encryptedKey() {
        return Arrays.copyOf(encryptedKey, encryptedKey.length);
    }

    /**
     * Authentication tag of the encrypted content.
     *
     * @return authentication tag
     */
    public byte[] authTag() {
        return Arrays.copyOf(authTag, authTag.length);
    }

    /**
     * Encrypted content.
     *
     * @return encrypted content
     */
    public byte[] encryptedPayload() {
        return Arrays.copyOf(encryptedPayload, encryptedPayload.length);
    }

    /**
     * Encrypted JWT builder.
     */
    public static class Builder implements io.helidon.common.Builder<EncryptedJwt> {

        private final SignedJwt jwt;
        private final JwtHeaders.Builder headersBuilder = JwtHeaders.builder();
        private Jwk jwk;
        private SupportedAlgorithm algorithm = SupportedAlgorithm.RSA_OAEP;
        private SupportedEncryption encryption = SupportedEncryption.A256GCM;
        private JwkKeys jwks;
        private String kid;

        private Builder(SignedJwt jwt) {
            this.jwt = Objects.requireNonNull(jwt);
        }

        /**
         * {@link JwkKeys} which should be searched for key with specific kid.
         * This key will be used for content key encryption.
         *
         * Selected {@link Jwk} is required to have private key specified otherwise encryption of the content
         * encryption key will not be possible.
         *
         * @param jwkKeys jwk keys
         * @param kid     searched kid
         * @return updated builder instance
         */
        public Builder jwks(JwkKeys jwkKeys, String kid) {
            this.jwks = Objects.requireNonNull(jwkKeys);
            this.kid = Objects.requireNonNull(kid);
            return this;
        }

        /**
         * Specific {@link Jwk} which should be used for content key encryption.
         *
         * Specific {@link Jwk} is required to have private key specified otherwise encryption of the content
         * encryption key will not be possible.
         *
         * @param jwk specific jwk
         * @return updated builder instance
         */
        public Builder jwk(Jwk jwk) {
            this.jwk = Objects.requireNonNull(jwk);
            return this;
        }

        /**
         * Algorithm which should be used as content key encryption.
         *
         * @param algorithm content key encryption
         * @return updated builder instance
         */
        public Builder algorithm(SupportedAlgorithm algorithm) {
            this.algorithm = Objects.requireNonNull(algorithm);
            return this;
        }

        /**
         * Encryption which should be used for content encryption.
         *
         * @param encryption content encryption
         * @return updated builder instance
         */
        public Builder encryption(SupportedEncryption encryption) {
            this.encryption = Objects.requireNonNull(encryption);
            return this;
        }

        @Override
        public EncryptedJwt build() {
            headersBuilder.algorithm(algorithm.toString());
            headersBuilder.encryption(encryption.toString());
            headersBuilder.contentType("JWT");
            PublicKey publicKey;
            if (jwk == null && jwks != null) {
                jwk = jwks.forKeyId(kid)
                        .orElseThrow(() -> new JwtException("Could not determine which JWK should be used for encryption."));
                headersBuilder.keyId(kid);
            }
            if (jwk == null) {
                throw new JwtException("No JWK specified for encrypted JWT creation.");
            }
            if (jwk instanceof JwkRSA) {
                publicKey = ((JwkRSA) jwk).publicKey();
            } else if (jwk instanceof JwkEC) {
                publicKey = ((JwkEC) jwk).publicKey();
            } else {
                throw new JwtException("Unsupported JWK type: " + jwk.keyType());
            }
            JwtHeaders headers = headersBuilder.build();
            StringBuilder tokenBuilder = new StringBuilder();
            String headersBase64 = encode(headers.headerJson().toString());
            String rsaCipherType = RSA_ALGORITHMS.get(algorithm);
            AesAlgorithm contentEncryption = CONTENT_ENCRYPTION.get(encryption);
            //Base64 headers are used as an aad. This aad has to be in US_ASCII encoding.
            EncryptionParts encryptionParts = contentEncryption.encrypt(jwt.tokenContent().getBytes(StandardCharsets.UTF_8),
                                                                        headersBase64.getBytes(StandardCharsets.US_ASCII));
            byte[] aesKey = encryptionParts.key();

            byte[] encryptedAesKey = encryptRsa(rsaCipherType, publicKey, aesKey);
            String token = tokenBuilder.append(headersBase64).append(".")
                    .append(encode(encryptedAesKey)).append(".")
                    .append(encode(encryptionParts.iv())).append(".")
                    .append(encode(encryptionParts.encryptedContent())).append(".")
                    .append(encode(encryptionParts.authTag())).toString();
            return new EncryptedJwt(token,
                                    headers,
                                    encryptionParts.iv,
                                    encryptedAesKey,
                                    encryptionParts.authTag(),
                                    encryptionParts.encryptedContent());
        }

    }

    /**
     * Supported RSA cipher for content key encryption.
     *
     * This cipher is using private key to decrypt encrypted content key with it.
     */
    public enum SupportedAlgorithm {

        /**
         * RSA-OAEP declares that RSA/ECB/OAEPWithSHA-1AndMGF1Padding cipher will be used for content key encryption.
         */
        RSA_OAEP("RSA-OAEP"),
        /**
         * RSA-OAEP-256 declares that RSA/ECB/OAEPWithSHA-256AndMGF1Padding cipher will be used for content key encryption.
         */
        RSA_OAEP_256("RSA-OAEP-256"),
        /**
         * RSA1_5 declares that RSA/ECB/PKCS1Padding cipher will be used for content key encryption.
         */
        RSA1_5("RSA1_5");

        private final String algorithmName;

        SupportedAlgorithm(String algorithmName) {
            this.algorithmName = algorithmName;
        }

        @Override
        public String toString() {
            return algorithmName;
        }

        static SupportedAlgorithm getValue(String value) {
            for (SupportedAlgorithm v : values()) {
                if (v.algorithmName.equalsIgnoreCase(value)) {
                    return v;
                }
            }
            throw new IllegalArgumentException();
        }
    }

    /**
     * Supported AES cipher for content encryption.
     */
    public enum SupportedEncryption {

        /**
         * Cipher AES/GCM/NoPadding will be used for content encryption and 128 bit key will be generated.
         */
        A128GCM("A128GCM"),
        /**
         * Cipher AES/GCM/NoPadding will be used for content encryption and 192 bit key will be generated.
         */
        A192GCM("A192GCM"),
        /**
         * Cipher AES/GCM/NoPadding will be used for content encryption and 256 bit key will be generated.
         */
        A256GCM("A256GCM"),
        /**
         * Cipher AES/CBC/PKCS5Padding will be used for content encryption and 128 bit key will be generated.
         * Authentication tag is generated by using HmacSHA256.
         */
        A128CBC_HS256("A128CBC-HS256"),
        /**
         * Cipher AES/CBC/PKCS5Padding will be used for content encryption and 192 bit key will be generated.
         * Authentication tag is generated by using HmacSHA384.
         */
        A192CBC_HS384("A192CBC-HS384"),
        /**
         * Cipher AES/CBC/PKCS5Padding will be used for content encryption and 256 bit key will be generated.
         * Authentication tag is generated by using HmacSHA512.
         */
        A256CBC_HS512("A256CBC-HS512");

        private final String encryptionName;

        SupportedEncryption(String encryptionName) {
            this.encryptionName = encryptionName;
        }

        @Override
        public String toString() {
            return encryptionName;
        }

        static SupportedEncryption getValue(String value) {
            for (SupportedEncryption v : values()) {
                if (v.encryptionName.equalsIgnoreCase(value)) {
                    return v;
                }
            }
            throw new IllegalArgumentException();
        }
    }

    private static class AesAlgorithm {

        private static final SecureRandom RANDOM = new SecureRandom();

        private final String cipher;
        private final int keySize;
        private final int ivSize;

        private AesAlgorithm(String cipher, int keySize, int ivSize) {
            this.cipher = cipher;
            this.keySize = keySize;
            this.ivSize = ivSize;
        }

        EncryptionParts encrypt(byte[] plainContent, byte[] aad) {
            try {
                KeyGenerator kgen = KeyGenerator.getInstance("AES");
                kgen.init(keySize, RANDOM);
                SecretKey secretKey = kgen.generateKey();
                byte[] iv = new byte[ivSize];
                RANDOM.nextBytes(iv);
                EncryptionParts encryptionParts = new EncryptionParts(secretKey.getEncoded(), iv, aad, null, null);
                Cipher cipher = Cipher.getInstance(this.cipher);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, createParameterSpec(encryptionParts));
                postCipherConstruct(cipher, encryptionParts);
                byte[] encryptedContent = cipher.doFinal(plainContent);
                return new EncryptionParts(secretKey.getEncoded(), iv, aad, encryptedContent, null);
            } catch (Exception e) {
                throw new JwtException("Exception during content encryption", e);
            }
        }

        byte[] decrypt(EncryptionParts encryptionParts) {
            try {
                byte[] key = encryptionParts.key();
                Cipher cipher = Cipher.getInstance(this.cipher);
                SecretKey secretKey = new SecretKeySpec(key, "AES");
                cipher.init(Cipher.DECRYPT_MODE, secretKey, createParameterSpec(encryptionParts));
                postCipherConstruct(cipher, encryptionParts);
                byte[] encryptedContent = encryptionParts.encryptedContent();
                return cipher.doFinal(encryptedContent);
            } catch (Exception e) {
                throw new JwtException("Exception during content decryption.", e);
            }
        }

        protected void postCipherConstruct(Cipher cipher, EncryptionParts encryptionParts) {
        }

        protected AlgorithmParameterSpec createParameterSpec(EncryptionParts encryptionParts) {
            return new IvParameterSpec(encryptionParts.iv());
        }

    }

    private static class AesAlgorithmWithHmac extends AesAlgorithm {

        private final String hmac;

        private AesAlgorithmWithHmac(String cipher, int keySize, int ivSize, String hmac) {
            super(cipher, keySize, ivSize);
            this.hmac = hmac;
        }

        @Override
        public EncryptionParts encrypt(byte[] plainContent, byte[] aad) {
            EncryptionParts encryptionParts = super.encrypt(plainContent, aad);
            byte[] authTag = sign(encryptionParts);
            return new EncryptionParts(encryptionParts.key(),
                                       encryptionParts.iv(),
                                       encryptionParts.aad(),
                                       encryptionParts.encryptedContent(),
                                       authTag);
        }

        private byte[] sign(EncryptionParts parts) {
            try {
                Mac mac = macInstance();
                mac.init(new SecretKeySpec(parts.key(), "AES"));
                mac.update(parts.aad());
                mac.update(parts.encryptedContent());
                return mac.doFinal();
            } catch (InvalidKeyException e) {
                throw new JwtException("Exception occurred while HMAC signature");
            }
        }

        @Override
        public byte[] decrypt(EncryptionParts encryptionParts) {
            if (!verifySignature(encryptionParts)) {
                throw new JwtException("HMAC signature does not match");
            }
            return super.decrypt(encryptionParts);
        }

        private boolean verifySignature(EncryptionParts encryptionParts) {
            try {
                Mac mac = macInstance();
                mac.init(new SecretKeySpec(encryptionParts.key(), "AES"));
                mac.update(encryptionParts.aad());
                mac.update(encryptionParts.encryptedContent());
                byte[] authKey = mac.doFinal();
                return Arrays.equals(authKey, encryptionParts.authTag());
            } catch (InvalidKeyException e) {
                throw new JwtException("Exception occurred while HMAC signature.");
            }
        }

        private Mac macInstance() {
            try {
                return Mac.getInstance(hmac);
            } catch (NoSuchAlgorithmException e) {
                throw new JwtException("Could not find MAC instance: " + hmac);
            }
        }
    }

    private static class AesGcmAlgorithm extends AesAlgorithm {

        private AesGcmAlgorithm(int keySize) {
            super("AES/GCM/NoPadding", keySize, 12);
        }

        @Override
        public EncryptionParts encrypt(byte[] plainContent, byte[] aad) {
            EncryptionParts encryptionParts = super.encrypt(plainContent, aad);
            byte[] wholeEncryptedContent = encryptionParts.encryptedContent();
            int length = wholeEncryptedContent.length - 16; //16 is a size of auth tag
            byte[] encryptedContent = new byte[length];
            byte[] authTag = new byte[16];
            System.arraycopy(wholeEncryptedContent, 0, encryptedContent, 0, encryptedContent.length);
            System.arraycopy(wholeEncryptedContent, length, authTag, 0, authTag.length);
            return new EncryptionParts(encryptionParts.key(),
                                       encryptionParts.iv(),
                                       encryptionParts.aad(),
                                       encryptedContent,
                                       authTag);
        }

        @Override
        byte[] decrypt(EncryptionParts encryptionParts) {
            byte[] encryptedPayload = encryptionParts.encryptedContent();
            byte[] authTag = encryptionParts.authTag();
            int epl = encryptedPayload.length;
            int al = authTag.length;
            byte[] result = new byte[epl + al];
            System.arraycopy(encryptedPayload, 0, result, 0, epl);
            System.arraycopy(authTag, 0, result, epl, al);
            EncryptionParts newEncParts = new EncryptionParts(encryptionParts.key(),
                                                              encryptionParts.iv(),
                                                              encryptionParts.aad(),
                                                              result,
                                                              authTag);
            return super.decrypt(newEncParts);
        }

        @Override
        protected AlgorithmParameterSpec createParameterSpec(EncryptionParts encryptionParts) {
            return new GCMParameterSpec(128, encryptionParts.iv());
        }

        @Override
        protected void postCipherConstruct(Cipher cipher, EncryptionParts encryptionParts) {
            cipher.updateAAD(encryptionParts.aad());
        }
    }

    private static final class EncryptionParts {

        private final byte[] key;
        private final byte[] iv;
        private final byte[] aad;
        private final byte[] encryptedContent;
        private final byte[] authTag;

        private EncryptionParts(byte[] key, byte[] iv, byte[] aad, byte[] encryptedContent, byte[] authTag) {
            this.key = key;
            this.iv = iv;
            this.aad = aad;
            this.encryptedContent = encryptedContent;
            this.authTag = authTag;
        }

        public byte[] key() {
            return key;
        }

        public byte[] iv() {
            return iv;
        }

        public byte[] aad() {
            return aad;
        }

        public byte[] encryptedContent() {
            return encryptedContent;
        }

        public byte[] authTag() {
            return authTag;
        }
    }

}
