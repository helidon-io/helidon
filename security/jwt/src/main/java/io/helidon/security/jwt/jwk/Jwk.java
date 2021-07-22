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

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.json.JsonObject;

import io.helidon.security.jwt.JwtException;

import static io.helidon.security.jwt.JwtUtil.asString;
import static io.helidon.security.jwt.JwtUtil.getString;
import static io.helidon.security.jwt.JwtUtil.getStrings;

/**
 * A JWK (JSON Web key) is a representation of data needed to sign, encrypt, verify
 * and /or decrypt data (e.g a public and/or private key; password for symmetric ciphers).
 */
@SuppressWarnings("WeakerAccess") // constants are public
public abstract class Jwk {
    /**
     * Algorithm defining there is no security (e.g. signature) at all.
     */
    public static final String ALG_NONE = "none";
    /**
     * Key type of elliptic curve keys.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.1.
     */
    public static final String KEY_TYPE_EC = "EC";
    /**
     * Key type of RSA keys.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, section 6.1.
     */
    public static final String KEY_TYPE_RSA = "RSA";
    /**
     * Key type of octet keys.
     * See <a href="https://www.rfc-editor.org/rfc/rfc7518.txt">RFC 7518</a>, 6.1.
     */
    public static final String KEY_TYPE_OCT = "oct";

    /**
     * Key can be used for encryption only.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.2">RFC 7517, section 4.2.</a>
     */
    public static final String USE_ENCRYPTION = "enc";
    /**
     * Key can be used for signatures only.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.2">RFC 7517, section 4.2.</a>
     */
    public static final String USE_SIGNATURE = "sig";

    /**
     * Compute digital signature or MAC.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.3">RFC 7517, section 4.3.</a>
     *
     * @see #PARAM_OPERATIONS
     */
    public static final String OPERATION_SIGN = "sign";
    /**
     * Verify digital signature or MAC.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.3">RFC 7517, section 4.3.</a>
     *
     * @see #PARAM_OPERATIONS
     */
    public static final String OPERATION_VERIFY = "verify";
    /**
     * Encrypt content.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.3">RFC 7517, section 4.3.</a>
     *
     * @see #PARAM_OPERATIONS
     */
    public static final String OPERATION_ENCRYPT = "encrypt";
    /**
     * Decrypt content and validate decryption, if applicable.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.3">RFC 7517, section 4.3.</a>
     *
     * @see #PARAM_OPERATIONS
     */
    public static final String OPERATION_DECRYPT = "decrypt";
    /**
     * Encrypt key.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.3">RFC 7517, section 4.3.</a>
     *
     * @see #PARAM_OPERATIONS
     */
    public static final String OPERATION_WRAP_KEY = "wrapKey";
    /**
     * Decrypt key and validate decryption, if applicable.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.3">RFC 7517, section 4.3.</a>
     *
     * @see #PARAM_OPERATIONS
     */
    public static final String OPERATION_UNWRAP_KEY = "unwrapKey";
    /**
     * Derive key.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.3">RFC 7517, section 4.3.</a>
     *
     * @see #PARAM_OPERATIONS
     */
    public static final String OPERATION_DERIVE_KEY = "deriveKey";
    /**
     * Derive bits not to be used as a key.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.3">RFC 7517, section 4.3.</a>
     *
     * @see #PARAM_OPERATIONS
     */
    public static final String OPERATION_DERIVE_BITS = "deriveBits";

    /**
     * JWK parameter for key type.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.1">RFC 7517, section 4.1.</a>
     */
    public static final String PARAM_KEY_TYPE = "kty";

    /**
     * JWK parameter for key id.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.5">RFC 7517, section 4.5.</a>
     */
    public static final String PARAM_KEY_ID = "kid";

    /**
     * JWK parameter for algorithm.
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.4">RFC 7517, section 4.4.</a>
     */
    public static final String PARAM_ALGORITHM = "alg";

    /**
     * JWK parameter for usage.
     * The "use" (public key use) parameter identifies the intended use of
     * the public key.  The "use" parameter is employed to indicate whether
     * a public key is used for encrypting data or verifying the signature
     * on data.
     *
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.2">RFC 7517, section 4.2.</a>
     */
    public static final String PARAM_USE = "use";

    /**
     * JWK parameters for permitted operations.
     * The "key_ops" (key operations) parameter identifies the operation(s)
     * for which the key is intended to be used.  The "key_ops" parameter is
     * intended for use cases in which public, private, or symmetric keys
     * may be present.
     *
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.3">RFC 7517, section 4.3.</a>
     */
    public static final String PARAM_OPERATIONS = "key_ops";

    /**
     * A jwk with no fields filled and {@link #ALG_NONE} algorithm.
     */
    public static final Jwk NONE_JWK = new NoneJwk();

    static final byte[] EMPTY_BYTES = new byte[0];

    private final String keyType;
    private final String keyId;
    private final String algorithm;
    private final Optional<String> usage;
    private final Optional<List<String>> operations;

    Jwk(Builder<?> builder, String defaultAlgorithm) {
        this.keyId = builder.keyId;
        this.algorithm = Optional.ofNullable(builder.algorithm).orElse(defaultAlgorithm);
        this.keyType = builder.keyType;
        this.usage = Optional.ofNullable(builder.usage);
        this.operations = Optional.ofNullable(builder.operations);
    }

    /**
     * Create an instance from Json object.
     *
     * @param json with definition of a web key (any key type)
     * @return new instance of a descendant of this class constructed from json, based on key type
     */
    public static Jwk create(JsonObject json) {
        String keyType = asString(json, PARAM_KEY_TYPE, "JWK Key type");
        // gather key type specific values
        switch (keyType) {
        case KEY_TYPE_EC:
            return JwkEC.create(json);
        case KEY_TYPE_RSA:
            return JwkRSA.create(json);
        case KEY_TYPE_OCT:
            return JwkOctet.create(json);
        default:
            throw new JwtException("Unknown JWK type: " + keyType);
        }
    }

    /**
     * The key type (kty) of this JWK.
     *
     * @return the key type
     * @see #PARAM_KEY_TYPE
     * @see #KEY_TYPE_EC
     * @see #KEY_TYPE_RSA
     * @see #KEY_TYPE_EC
     */
    public String keyType() {
        return keyType;
    }

    /**
     * The key id (kid) of this JWK.
     * The key id is used to reference a key in configuration (e.g. a JWT comes with
     * a signature and key id; we should have a key from a JWK keys with that key id configured
     * and use it to verify the signature).
     *
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.5">RFC 7517, section 4.5.</a>
     *
     * @return key id of this JWK
     * @see #PARAM_KEY_ID
     */
    public String keyId() {
        return keyId;
    }

    /**
     * The algorithm used when signing/encrypting this key.
     *
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.4">RFC 7517, section 4.4.</a>
     *
     * @return algorithm if present (some types have defaults).
     * @see #PARAM_ALGORITHM
     */
    public String algorithm() {
        return algorithm;
    }

    /**
     * Permitted usage of this JWK.
     *
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.2">RFC 7517, section 4.2.</a>
     *
     * @return usage of this JWK or empty if not defined.
     * @see #PARAM_USE
     * @see #USE_ENCRYPTION
     * @see #USE_SIGNATURE
     */
    public Optional<String> usage() {
        return usage;
    }

    /**
     * Permitted operations of this JWK.
     *
     * See <a href="https://tools.ietf.org/html/rfc7517#section-4.3">RFC 7517, section 4.3.</a>
     *
     * @return list of operations allowed, or empty if not defined
     */
    public Optional<List<String>> operations() {
        return operations;
    }

    /**
     * Verify that the signature is indeed for the signed bytes based on this JWK type
     * and algorithm.
     *
     * @param signedBytes bytes that are signed (e.g. content of a JWT, raw bytes)
     * @param signature   signature bytes (raw bytes)
     * @return true if signature is valid, false otherwise
     */
    public final boolean verifySignature(byte[] signedBytes, byte[] signature) {
        if (supports(USE_SIGNATURE, OPERATION_VERIFY)) {
            return doVerify(signedBytes, signature);
        } else {
            throw new JwtException("This key (" + this + ") does not support verification of requests");
        }
    }

    abstract boolean doVerify(byte[] signedBytes, byte[] signature);

    /**
     * Sign the bytes to sign using this JWK type and algorithm.
     *
     * @param bytesToSign byte to be signed (e.g. content of a JWT, raw bytes)
     * @return signature bytes (raw bytes)
     */
    public final byte[] sign(byte[] bytesToSign) {
        if (supports(USE_SIGNATURE, OPERATION_SIGN)) {
            return doSign(bytesToSign);
        } else {
            throw new JwtException("This key (" + this + ") does not support signing of requests");
        }
    }

    abstract byte[] doSign(byte[] bytesToSign);

    boolean supports(String use, String operation) {
        Boolean result = operations.map(ops -> ops.contains(operation))
                .or(() -> usage.map(usage -> usage.equals(use)))
                .orElse(true);

        if (!result && "verify".equals(operation)) {
            // when we want to verify signature and this is a signature key, we attempt to use it
            return supports(use, "sign");
        }

        return result;
    }

    // used from other descendants - if alg is set to "none", the signature must be
    // an empty string and we must support it for current kid
    boolean verifyNoneAlg(byte[] signatureToVerify) {
        return signatureToVerify.length == 0 && ALG_NONE.equals(algorithm);
    }

    @Override
    public String toString() {
        return keyId + "(" + algorithm + ")";
    }

    // this builder is not public, as a specific key type must be built
    abstract static class Builder<T extends Builder<T>> {
        private final T myInstance;
        private String keyType;
        private String keyId;
        private String algorithm;
        private String usage;
        private List<String> operations;

        @SuppressWarnings("unchecked")
        Builder() {
            this.myInstance = (T) this;
        }

        /**
         * Key type of the key being built.
         *
         * @param keyType one of supported key types
         * @return updated builder instance
         * @see #KEY_TYPE_EC
         * @see #KEY_TYPE_RSA
         * @see #KEY_TYPE_OCT
         */
        public T keyType(String keyType) {
            this.keyType = keyType;
            return myInstance;
        }

        /**
         * Key id of the key being built.
         * Note that within one set of keys {@link JwkKeys} this must be unique.
         *
         * @param keyId key id to map from a signed entity (such as JWT) to JWK definition
         * @return updated builder instance
         */
        public T keyId(String keyId) {
            this.keyId = keyId;
            return myInstance;
        }

        /**
         * Algorithm of the key being built.
         * Algorithm is optional (each type may have a reasonable default).
         *
         * @param algorithm see each key type for supported algorithms
         * @return updated builder instance
         * @see JwkEC
         * @see JwkOctet
         * @see JwkRSA
         */
        public T algorithm(String algorithm) {
            this.algorithm = algorithm;
            return myInstance;
        }

        /**
         * Intended usage of this JWK.
         * You may configure usage, {@link #operations} or neither (never both).
         *
         * @param usage usage of this JWK
         * @return updated builder instance
         * @see #USE_ENCRYPTION
         * @see #USE_SIGNATURE
         */
        public T usage(String usage) {
            this.usage = usage;
            return myInstance;
        }

        /**
         * Intended operations of this JWK.
         * You may configure operations, {@link #usage} or neither (never both).
         *
         * @param operations operations to use, replaces existing operations
         * @return updated builder instance
         */
        public T operations(List<String> operations) {
            if (null == this.operations) {
                this.operations = new LinkedList<>();
            } else {
                this.operations.clear();
            }
            this.operations.addAll(operations);

            return myInstance;
        }

        /**
         * Add intended operation of this JWK.
         * You may configure operations, {@link #usage} or neither (never both).
         *
         * @param operation operation to add to list of operations
         * @return updated builder instance
         */
        public T addOperation(String operation) {
            if (null == operations) {
                operations = new LinkedList<>();
            }
            this.operations.add(operation);
            return myInstance;
        }

        T fromJson(JsonObject json) {
            // key type agnostic values
            keyType(asString(json, PARAM_KEY_TYPE, "JWK Key type"));
            keyId(asString(json, PARAM_KEY_ID, "JWK Key id"));
            getString(json, PARAM_ALGORITHM).ifPresent(this::algorithm);
            /*
             sig - signatures or MAC
             enc - encryption
            */
            getString(json, PARAM_USE).ifPresent(this::usage);
            /*
              sign - compute digital signature or MAC
              verify - verify digital signature
              encrypt - encrypt content
              decrypt - decrypt content
              wrapKey - encrypt key
              unwrapKey - decrypt key
              deriveKey - derive key
              deriveBits - derive bits not to be used as a key
             */
            getStrings(json, PARAM_OPERATIONS).ifPresent(this::operations);

            return myInstance;
        }
    }

    private static class NoneJwk extends Jwk {
        NoneJwk() {
            super(new Builder().algorithm(ALG_NONE), ALG_NONE);
        }

        @Override
        public boolean doVerify(byte[] signedBytes, byte[] signature) {
            return signature.length == 0;
        }

        @Override
        public byte[] doSign(byte[] bytesToSign) {
            return EMPTY_BYTES;
        }

        private static class Builder extends Jwk.Builder<Builder> {

        }
    }
}
