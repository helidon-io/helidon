/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.Errors;
import io.helidon.common.GenericType;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Representation of the header section of a JWT.
 * This can be used to partially parse a token to understand what kind of
 * processing should be done further, whether {@link io.helidon.security.jwt.SignedJwt}
 * or {@link io.helidon.security.jwt.EncryptedJwt}.
 *
 * @see #parseToken(String)
 */
public class JwtHeaders extends JwtClaims {

    static final String ALGORITHM = "alg";
    static final String ENCRYPTION = "enc";
    static final String TYPE = "typ";
    static final String CONTENT_TYPE = "cty";
    static final String KEY_ID = "kid";
    static final String JWK_SET_URL = "jku";
    static final String JSON_WEB_KEY = "jwk";
    static final String X509_URL = "x5u";
    static final String X509_CERT_CHAIN = "x5c";
    static final String X509_CERT_SHA1_THUMB = "x5t";
    static final String X509_CERT_SHA256_THUMB = "x5t#S256";
    static final String CRITICAL = "crit";
    static final String COMPRESSION_ALGORITHM = "zip";
    static final String AGREEMENT_PARTYUINFO = "apu";
    static final String AGREEMENT_PARTYVINFO = "apv";
    static final String EPHEMERAL_PUBLIC_KEY = "epk";

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private final Optional<String> algorithm;
    private final Optional<String> encryption;
    private final Optional<String> contentType;
    private final Optional<String> keyId;
    private final Optional<String> type;
    private final Optional<List<String>> critical;
    // intended for replication into header in encrypted JWT
    private final Optional<String> subject;
    private final Optional<String> issuer;
    private final Optional<List<String>> audience;

    private final Map<String, JsonValue> headerClaims;

    private JwtHeaders(Builder builder) {
        this.algorithm = Optional.ofNullable(builder.algorithm);
        this.encryption = Optional.ofNullable(builder.encryption);
        this.contentType = Optional.ofNullable(builder.contentType);
        this.keyId = Optional.ofNullable(builder.keyId);
        this.type = Optional.ofNullable(builder.type);
        this.critical = Optional.ofNullable(builder.critical);
        this.subject = Optional.ofNullable(builder.subject);
        this.issuer = Optional.ofNullable(builder.issuer);
        this.audience = Optional.ofNullable(builder.audience);
        this.headerClaims = new LinkedHashMap<>(builder.claims);
    }

    /**
     * Create a new builder for header claims.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Parse a token to retrieve the JWT header.
     * This method only cares about the first section of the token, and ignores the rest (even if not valid).
     * Text before the first dot is considered to be base64 value of the header JSON.
     *
     * @param token token, expected to be JWT (encrypted or signed)
     * @return header parsed from the token
     * @throws io.helidon.security.jwt.JwtException in case the token is not valid
     */
    public static JwtHeaders parseToken(String token) {
        Errors.Collector collector = Errors.collector();

        int firstDot = token.indexOf('.');
        if (firstDot < 0) {
            throw new JwtException("Not a JWT token: " + token);
        }
        String headerBase64 = token.substring(0, firstDot);
        JwtHeaders jwtHeader = parseBase64(headerBase64, collector);
        collector.collect().checkValid();
        return jwtHeader;
    }

    static JwtHeaders parseBase64(String base64, Errors.Collector collector) {
        String headerJsonString = decode(base64, collector, "JWT header");

        // if failed, do not continue
        if (collector.hasFatal()) {
            return null;
        }

        // this is either a signed JWT or encrypted JWT, first section is always
        // base64 encoded header
        JsonObject headerJson = parseJson(headerJsonString, collector, base64, "JWT header");

        // if failed, do not continue
        if (collector.hasFatal()) {
            return null;
        }

        Builder builder = builder();

        builder.fromJson(headerJson);

        collector.collect().checkValid();

        return builder.build();
    }

    /**
     * Create a JSON header object.
     *
     * @return JsonObject for header
     */
    public JsonObject headerJson() {
        JsonObjectBuilder objectBuilder = JSON.createObjectBuilder();
        headerClaims.forEach(objectBuilder::add);

        return objectBuilder.build();
    }

    /**
     * Get a claim by its name from header.
     *
     * @param claim name of a claim
     * @return claim value if present
     */
    public Optional<JsonValue> headerClaim(String claim) {
        return Optional.ofNullable(headerClaims.get(claim));
    }

    /**
     * Algorithm claim.
     *
     * @return algorithm or empty if claim is not defined
     */
    public Optional<String> algorithm() {
        return algorithm;
    }

    /**
     * Encryption algorithm claim.
     *
     * @return algorithm or empty if not encrypted
     */
    public Optional<String> encryption() {
        return encryption;
    }

    /**
     * Content type claim.
     *
     * @return content type or empty if claim is not defined
     */
    public Optional<String> contentType() {
        return contentType;
    }

    /**
     * Key id claim.
     *
     * @return key id or empty if claim is not defined
     */
    public Optional<String> keyId() {
        return keyId;
    }

    /**
     * Type claim.
     *
     * @return type or empty if claim is not defined
     */
    public Optional<String> type() {
        return type;
    }

    /**
     * Subject claim.
     *
     * @return subject or empty if claim is not defined
     */
    public Optional<String> subject() {
        return subject;
    }

    /**
     * Issuer claim.
     *
     * @return Issuer or empty if claim is not defined
     */
    public Optional<String> issuer() {
        return issuer;
    }

    /**
     * Audience claim.
     *
     * @return audience or empty optional if claim is not defined; list would be empty if the audience claim is defined as
     *                      an empty array
     */
    public Optional<List<String>> audience() {
        return audience;
    }

    /**
     * Critical claim.
     *
     * @return critical claims or empty optional if not defined; list would be empty if the critical claim is defined as
     *                      an empty array
     */
    public Optional<List<String>> critical() {
        return critical;
    }

    /**
     * Return map of all header claims.
     *
     * @return header claims
     */
    public Map<String, JsonValue> headerClaims() {
        return Collections.unmodifiableMap(headerClaims);
    }

    /**
     * Fluent API builder to create JWT Header.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, JwtHeaders> {
        private static final GenericType<List<String>> STRING_LIST_TYPE = new GenericType<>() { };

        private static final Map<String, KnownField<?>> KNOWN_HEADER_CLAIMS;
        private static final KnownField<String> TYPE_FIELD = KnownField.create(TYPE, Builder::type);
        private static final KnownField<String> ALG_FIELD = KnownField.create(ALGORITHM, Builder::algorithm);
        private static final KnownField<String> ENC_FIELD = KnownField.create(ENCRYPTION, Builder::encryption);
        private static final KnownField<String> CTY_FIELD = KnownField.create(CONTENT_TYPE, Builder::contentType);
        private static final KnownField<String> KID_FIELD = KnownField.create(KEY_ID, Builder::keyId);
        private static final KnownField<String> SUB_FIELD = KnownField.create(Jwt.SUBJECT, Builder::headerSubject);
        private static final KnownField<String> ISS_FIELD = KnownField.create("iss", Builder::headerIssuer);
        private static final KnownField<List<String>> CRIT_FIELD = new KnownField<>(CRITICAL,
                                                                                    STRING_LIST_TYPE,
                                                                                    Builder::headerCritical,
                                                                                    Builder::jsonToStringList);
        private static final KnownField<List<String>> AUD_FIELD = new KnownField<>("aud",
                                                                                   STRING_LIST_TYPE,
                                                                                   Builder::headerAudience,
                                                                                   Builder::jsonToStringList);

        static {
            Map<String, KnownField<?>> map = new HashMap<>();

            addKnownField(map, TYPE_FIELD);
            addKnownField(map, ALG_FIELD);
            addKnownField(map, ENC_FIELD);
            addKnownField(map, CTY_FIELD);
            addKnownField(map, KID_FIELD);
            addKnownField(map, SUB_FIELD);
            addKnownField(map, ISS_FIELD);
            addKnownField(map, AUD_FIELD);
            addKnownField(map, CRIT_FIELD);

            KNOWN_HEADER_CLAIMS = Map.copyOf(map);
        }

        private final Map<String, JsonValue> claims = new LinkedHashMap<>();

        private String type;
        private String algorithm;
        private String encryption;
        private String contentType;
        private String keyId;
        private String subject;
        private String issuer;
        private List<String> audience;
        private List<String> critical;

        private Builder() {
        }

        @Override
        public JwtHeaders build() {
            if (audience != null) {
                // this may be changing throughout the build
                AUD_FIELD.set(claims, audience);
            }
            if (critical != null) {
                CRIT_FIELD.set(claims, critical);
            }
            return new JwtHeaders(this);
        }

        /**
         * Add a header claim.
         *
         * @param claim name of the claim
         * @param value claim value, must be of expected type
         * @return updated builder
         * @throws java.lang.IllegalArgumentException if a known header (such as {@code iss}, {@code aud}) is set to a non-string
         *  type
         */
        public Builder addHeaderClaim(String claim, Object value) {
            setFromGeneric(claim, value);
            this.claims.put(claim, JwtUtil.toJson(value));
            return this;
        }

        /**
         * The "alg" claim is used to define the signature algorithm.
         * Note that this algorithm should be the same as is supported by
         * the JWK used to sign (or verify) the JWT.
         *
         * @param algorithm algorithm to use, {@link io.helidon.security.jwt.jwk.Jwk#ALG_NONE} for none
         * @return updated builder instance
         */
        public Builder algorithm(String algorithm) {
            ALG_FIELD.set(claims, algorithm);
            this.algorithm = algorithm;
            return this;
        }

        /**
         * Encryption algorithm to use.
         *
         * @param encryption encryption to use
         * @return updated builder
         */
        public Builder encryption(String encryption) {
            ENC_FIELD.set(claims, encryption);
            this.encryption = encryption;
            return this;
        }

        /**
         * This header claim should only be used when nesting or encrypting JWT.
         * See <a href="https://tools.ietf.org/html/rfc7519#section-5.2">RFC 7519, section 5.2</a>.
         *
         * @param contentType content type to use, use "JWT" if nested
         * @return updated builder instance
         */
        public Builder contentType(String contentType) {
            CTY_FIELD.set(claims, contentType);
            this.contentType = contentType;
            return this;
        }

        /**
         * Key id to be used to sign/verify this JWT.
         *
         * @param keyId key id (pointing to a JWK)
         * @return updated builder instance
         */
        public Builder keyId(String keyId) {
            KID_FIELD.set(claims, keyId);
            this.keyId = keyId;
            return this;
        }

        /**
         * Type of this JWT.
         *
         * @param type type definition (JWT, JWE)
         * @return updated builder instance
         */
        public Builder type(String type) {
            TYPE_FIELD.set(claims, type);
            this.type = type;
            return this;
        }

        /**
         * Subject defines the principal this JWT was issued for (e.g. user id).
         * This configures subject in header claims (usually it is part of payload).
         *
         * See <a href="https://tools.ietf.org/html/rfc7519#section-4.1.2">RFC 7519, section 4.1.2</a>.
         *
         * @param subject subject of this JWt
         * @return updated builder instance
         */
        public Builder headerSubject(String subject) {
            SUB_FIELD.set(claims, subject);
            this.subject = subject;
            return this;
        }

        /**
         * The issuer claim identifies the principal that issued the JWT.
         * This configures issuer in header claims (usually it is part of payload).
         *
         * See <a href="https://tools.ietf.org/html/rfc7519#section-4.1.1">RFC 7519, section 4.1.1</a>.
         *
         * @param issuer issuer name or URL
         * @return updated builder instance
         */
        public Builder headerIssuer(String issuer) {
            ISS_FIELD.set(claims, issuer);
            this.issuer = issuer;
            return this;
        }

        /**
         * The critical claim is used to indicate that certain claims are critical and must be understood (optional).
         * If a recipient does not understand or support any of the critical claims, it must reject the token.
         * Multiple critical claims may be added.
         * This configures critical claims in header claims.
         *
         * See <a href="https://tools.ietf.org/html/rfc7519#section-4.1.1">RFC 7519, section 4.1.1</a>.
         *
         * @param critical required critical claim to understand
         * @return updated builder instance
         */
        public Builder addHeaderCritical(String critical) {
            if (this.critical == null) {
                this.critical = new LinkedList<>();
            }
            this.critical.add(critical);
            return this;
        }

        /**
         * Audience identifies the expected recipients of this JWT (optional).
         * Multiple audience may be added.
         * This configures audience in header claims, usually this is defined in payload.
         *
         * See <a href="https://datatracker.ietf.org/doc/html/rfc7515#section-4.1.11">RFC 7515, section 4.1.11</a>.
         *
         * @param audience audience of this JWT
         * @return updated builder instance
         * @see #headerAudience(java.util.List)
         */
        public Builder addHeaderAudience(String audience) {
            if (this.audience == null) {
                this.audience = new LinkedList<>();
            }
            this.audience.add(audience);
            return this;
        }

        /**
         * Audience identifies the expected recipients of this JWT (optional).
         * Replaces existing configured audiences.
         * This configures audience in header claims, usually this is defined in payload.
         *
         * See <a href="https://tools.ietf.org/html/rfc7519#section-4.1.3">RFC 7519, section 4.1.3</a>.
         *
         * @param audience audience of this JWT
         * @return updated builder instance
         */
        public Builder headerAudience(List<String> audience) {
            this.audience = new LinkedList<>(audience);
            return this;
        }

        /**
         * The critical claim is used to indicate that certain claims are critical and must be understood (optional).
         * If a recipient does not understand or support any of the critical claims, it must reject the token.
         * Replaces existing configured critical claims.
         * This configures critical claims in header claims.
         *
         * See <a href="https://datatracker.ietf.org/doc/html/rfc7515#section-4.1.11">RFC 7515, section 4.1.11</a>.
         *
         * @param critical required critical claims to understand
         * @return updated builder instance
         */
        public Builder headerCritical(List<String> critical) {
            this.critical = new ArrayList<>(critical);
            return this;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void setFromGeneric(String claim, Object value) {
            KnownField knownField = KNOWN_HEADER_CLAIMS.get(claim);
            if (knownField == null) {
                return;
            }
            if (knownField.supports(value)) {
                knownField.valueConsumer().accept(this, value);
            } else {
                throw new IllegalArgumentException("Claim \"" + claim
                                                           + " is expected to be of type " + knownField.type
                                                           + ", but is " + value.getClass().getName());
            }
        }

        private static List<String> jsonToStringList(JsonValue jsonValue) {
            if (jsonValue instanceof JsonString) {
                return List.of(((JsonString) jsonValue).getString());
            }
            if (jsonValue instanceof JsonArray) {
                return ((JsonArray) jsonValue)
                        .stream()
                        .map(KnownField::jsonToString)
                        .collect(Collectors.toList());
            }
            throw new JwtException("Json value should have been a String or an array of Strings, but is " + jsonValue);
        }

        private static <T> void addKnownField(Map<String, KnownField<?>> map, KnownField<T> field) {
            map.put(field.name, field);
        }

        void fromJson(JsonObject headerJson) {
            headerJson.forEach((claim, value) -> {
                KnownField<?> knownField = KNOWN_HEADER_CLAIMS.get(claim);
                if (knownField == null) {
                    addHeaderClaim(claim, value);
                } else {
                    knownField.set(this, value);
                }
            });
        }
    }

    private static final class KnownField<T> {
        private final String name;
        private final GenericType<T> type;
        private final BiConsumer<JwtHeaders.Builder, T> valueConsumer;
        private final Function<JsonValue, T> fromJson;

        private KnownField(String name,
                           GenericType<T> type,
                           BiConsumer<Builder, T> valueConsumer,
                           Function<JsonValue, T> fromJson) {
            this.name = name;
            this.type = type;
            this.valueConsumer = valueConsumer;
            this.fromJson = fromJson;
        }

        static KnownField<String> create(String name, BiConsumer<Builder, String> valueConsumer) {
            return new KnownField<>(name, GenericType.STRING, valueConsumer, KnownField::jsonToString);
        }

        private static String jsonToString(JsonValue jsonValue) {
            if (jsonValue instanceof JsonString) {
                return ((JsonString) jsonValue).getString();
            }
            throw new JwtException("Json value should have been a String, but is " + jsonValue);
        }

        BiConsumer<Builder, T> valueConsumer() {
            return valueConsumer;
        }

        void set(Map<String, JsonValue> claims, T value) {
            claims.put(name, JwtUtil.toJson(value));
        }

        void set(Builder builder, JsonValue value) {
            valueConsumer.accept(builder, fromJson.apply(value));
        }

        public boolean supports(Object value) {
            return type.rawType().isAssignableFrom(value.getClass());
        }
    }
}
