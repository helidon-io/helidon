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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.spi.JsonProvider;

/**
 * Utilities for JWT and JWK parsing.
 */
public final class JwtUtil {
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    // both are thread safe according to javadoc
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder();
    private static final Pattern LOCALE_PATTERN = Pattern.compile("(\\w+)_(\\w+)");

    // Avoid reloading JSON providers. See https://github.com/eclipse-ee4j/jsonp/issues/154
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonProvider JSON_PROVIDER = JsonProvider.provider();

    private JwtUtil() {
    }

    static String base64Url(byte[] bytesToEncode) {
        return URL_ENCODER.encodeToString(bytesToEncode);
    }

    /**
     * Extract a key value from json object that is base64-url encoded and convert it to big integer.
     *
     * @param json        JsonObject to read key from
     * @param key         key of the value we want to read
     * @param description description of the field for error handling
     * @return BigInteger value
     * @throws JwtException in case the key is not present or is of invalid content
     */
    public static BigInteger asBigInteger(JsonObject json, String key, String description) throws JwtException {
        return getBigInteger(json, key, description)
                .orElseThrow(() -> new JwtException("Key \"" + key + "\" is mandatory for " + description));
    }

    /**
     * Extract a key value from json object that is string.
     *
     * @param json        JsonObject to read key from
     * @param key         key of the value we want to read
     * @param description description of the field for error handling
     * @return String value
     * @throws JwtException in case the key is not present or is of invalid content
     */
    public static String asString(JsonObject json, String key, String description) throws JwtException {
        return getString(json, key)
                .orElseThrow(() -> new JwtException("Key \"" + key + "\" is mandatory for " + description));
    }

    /**
     * Extract a key value from json object that is base64-url encoded and convert it to big integer if present.
     *
     * @param json        JsonObject to read key from
     * @param key         key of the value we want to read
     * @param description description of the field for error handling
     * @return BigInteger value if present
     * @throws JwtException in case the key is of invalid content
     */
    public static Optional<BigInteger> getBigInteger(JsonObject json, String key, String description) throws JwtException {
        return getByteArray(json, key, description)
                .map(byteValue -> {
                    // create BigInteger
                    try {
                        return new BigInteger(1, byteValue);
                    } catch (Exception e) {
                        throw new JwtException("Failed to get a big decimal for: " + description + ", from value of key " + key,
                                               e);
                    }
                });
    }

    /**
     * Extract a key value from json object that is a list of strings if present.
     *
     * @param json JsonObject to read key from
     * @param key  key of the value we want to read
     * @return List of String value if present
     * @throws JwtException in case the key is of invalid content
     */
    public static Optional<List<String>> getStrings(JsonObject json, String key) throws JwtException {
        return Optional.ofNullable(json.getJsonArray(key))
                .map(it -> {
                    try {
                        return it.stream().map(it2 -> ((JsonString) it2).getString()).collect(Collectors.toList());
                    } catch (Exception e) {
                        throw new JwtException("Invalid value. Expecting a string array for key " + key);
                    }
                });
    }

    /**
     * Extract a key value from json object that is string if present.
     *
     * @param json JsonObject to read key from
     * @param key  key of the value we want to read
     * @return String value if present
     * @throws JwtException in case the key is of invalid content
     */
    public static Optional<String> getString(JsonObject json, String key) throws JwtException {
        return Optional.ofNullable(json.getString(key, null));
    }

    /**
     * Extract a key value from json object that is a base64-url encoded byte array, if present.
     *
     * @param json        JsonObject to read key from
     * @param key         key of the value we want to read
     * @param description description of the field for error handling
     * @return byte array value if present
     * @throws JwtException in case the key is of invalid content or not base64 encoded
     */
    public static Optional<byte[]> getByteArray(JsonObject json, String key, String description) throws JwtException {
        return getString(json, key)
                .map(base64 -> {
                    try {
                        return URL_DECODER.decode(base64);
                    } catch (Exception e) {
                        throw new JwtException("Failed to decode base64 from json for: " + description + ", value: \""
                                                       + base64 + '"', e);
                    }
                });
    }

    /**
     * Extract a key value from json object that is a base64-url encoded byte array.
     *
     * @param json        JsonObject to read key from
     * @param key         key of the value we want to read
     * @param description description of the field for error handling
     * @return byte array value
     * @throws JwtException in case the key is not present, is of invalid content or not base64 encoded
     */
    public static byte[] asByteArray(JsonObject json, String key, String description) throws JwtException {
        return getByteArray(json, key, description)
                .orElseThrow(() -> new JwtException("Key \"" + key + "\" is mandatory for " + description));
    }

    /**
     * Create a key factory for algorithm.
     *
     * @param algorithm security algorithm (such as RSA, EC)
     * @return KeyFactory instance
     * @throws JwtException in case the algorithm is invalid
     */
    public static KeyFactory getKeyFactory(String algorithm) throws JwtException {
        try {
            return KeyFactory.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new JwtException("Failed to create key factory for algorithm: \"" + algorithm + "\"", e);
        }
    }

    /**
     * Create a signature for algorithm.
     *
     * @param signatureAlgorithm security signature algorithm (such as "SHA512withRSA")
     * @return Signature instance
     * @throws JwtException in case the algorithm is invalid or not supported by this JVM
     */
    public static Signature getSignature(String signatureAlgorithm) throws JwtException {
        try {
            return Signature.getInstance(signatureAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new JwtException("Failed to get Signature instance for algorithm \"" + signatureAlgorithm + "\"");
        }
    }

    /**
     * Create a MAC for algorithm. Similar to signature for symmetric ciphers (such as "HmacSHA256").
     *
     * @param algorithm security MAC algorithm
     * @return Mac instance
     * @throws JwtException in case the algorithm is invalid or not supported by this JVM
     */
    public static Mac getMac(String algorithm) throws JwtException {
        try {
            return Mac.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new JwtException("Failed to get Mac instance for algorithm \"" + algorithm + "\"");
        }
    }

    /**
     * Transform a map of strings to objects to a map of string to JSON values.
     * Each object is checked for type and if supported, transformed to appropriate
     * JSON value.
     *
     * @param claims map to transform from
     * @return resulting map
     */
    public static Map<String, JsonValue> transformToJson(Map<String, Object> claims) {
        Map<String, JsonValue> result = new HashMap<>();

        claims.forEach((s, o) -> result.put(s, toJson(o)));

        return result;
    }

    private static JsonValue toJson(Object object) {
        if (object instanceof String) {
            return JSON_PROVIDER.createValue((String) object);
        }
        if (object instanceof Integer) {
            return JSON_PROVIDER.createValue((Integer) object);
        }
        if (object instanceof Double) {
            return JSON_PROVIDER.createValue((Double) object);
        }
        if (object instanceof Long) {
            return JSON_PROVIDER.createValue((Long) object);
        }
        if (object instanceof BigDecimal) {
            return JSON_PROVIDER.createValue((BigDecimal) object);
        }
        if (object instanceof BigInteger) {
            return JSON_PROVIDER.createValue((BigInteger) object);
        }
        if (object instanceof Boolean) {
            return ((Boolean) object) ? JsonValue.TRUE : JsonValue.FALSE;
        }
        if (object instanceof Address) {
            return ((Address) object).getJson();
        }
        if (object instanceof Collection) {
            return JSON.createArrayBuilder((Collection) object).build();
        }
        return JSON_PROVIDER.createValue(String.valueOf(object));
    }

    private static Locale toLocale(String locale) {
        Matcher matcher = LOCALE_PATTERN.matcher(locale);
        Locale result;
        if (matcher.matches()) {
            result = new Locale(matcher.group(1), matcher.group(2));
        } else {
            result = Locale.forLanguageTag(locale);
        }
        return result;
    }

    static Optional<Address> toAddress(JsonObject json, String name) {
        return Optional.ofNullable(json.getJsonObject(name))
                .map(Address::new);
    }

    static Optional<List<String>> toScopes(JsonObject json) {
        return getString(json, "scope")
                .map(it -> Arrays.asList(it.split(" ")));
    }

    static Optional<ZoneId> toTimeZone(JsonObject json, String name) {
        return getString(json, name)
                .map(ZoneId::of);
    }

    static Optional<LocalDate> toDate(JsonObject json, String name) {
        return getString(json, name)
                .map(it -> {
                    if (it.length() == 4) {
                        return LocalDate.parse(it, YEAR_FORMAT);
                    } else {
                        return LocalDate.parse(it, DATE_FORMAT);
                    }
                });
    }

    static Optional<URI> toUri(JsonObject json, String name) {
        return getString(json, name)
                .map(URI::create);
    }

    static Optional<Locale> toLocale(JsonObject json, String name) {
        return getString(json, name)
                .map(JwtUtil::toLocale);
    }

    static Optional<Boolean> toBoolean(JsonObject json, String name) {
        if (json.containsKey(name)) {
            return Optional.of(json.getBoolean(name));
        }
        return Optional.empty();
    }

    static Optional<Instant> toInstant(JsonObject json, String name) {
        return Optional.ofNullable(json.getJsonNumber(name))
                .map(JsonNumber::longValue)
                .map(Instant::ofEpochSecond);
    }

    static String toDate(LocalDate it) {
        return it.format(JwtUtil.DATE_FORMAT);
    }

    /**
     * Transform from json to object.
     *
     * @param jsonValue json value
     * @return object most correct for the type, or string value if not understood (e.g. json object)
     */
    public static Object toObject(JsonValue jsonValue) {
        switch (jsonValue.getValueType()) {
        case ARRAY:
            return jsonValue.toString();
        case OBJECT:
            return jsonValue.toString();
        case STRING:
            return ((JsonString) jsonValue).getString();
        case NUMBER:
            return ((JsonNumber) jsonValue).numberValue();
        case TRUE:
            return true;
        case FALSE:
            return false;
        case NULL:
            return null;
        default:
            return jsonValue.toString();
        }
    }

    /**
     * Address class representing the JSON object for address.
     */
    public static class Address {
        // Full mailing address, formatted for display or use on a mailing label. This field MAY contain multiple lines,
        // separated by newlines. Newlines can be represented either as a carriage return/line feed pair ("\r\n") or as a
        // single line feed character ("\n").
        private final Optional<String> formatted;
        // street_address
        private final Optional<String> streetAddress;
        // locality (City or locality)
        private final Optional<String> locality;
        // State, province, prefecture, or region component.
        private final Optional<String> region;
        // postal_code
        private final Optional<String> postalCode;
        private final Optional<String> country;

        /**
         * Create an address object from json representation.
         *
         * @param jsonObject object with expected keys
         */
        public Address(JsonObject jsonObject) {
            this.formatted = getString(jsonObject, "formatted");
            this.streetAddress = getString(jsonObject, "street_address");
            this.locality = getString(jsonObject, "locality");
            this.region = getString(jsonObject, "region");
            this.postalCode = getString(jsonObject, "postal_code");
            this.country = getString(jsonObject, "country");
        }

        public Optional<String> getFormatted() {
            return formatted;
        }

        public Optional<String> getStreetAddress() {
            return streetAddress;
        }

        public Optional<String> getLocality() {
            return locality;
        }

        public Optional<String> getRegion() {
            return region;
        }

        public Optional<String> getPostalCode() {
            return postalCode;
        }

        public Optional<String> getCountry() {
            return country;
        }

        /**
         * Create a json representation of this address.
         *
         * @return Address as a Json object
         */
        public JsonObject getJson() {
            JsonObjectBuilder objectBuilder = JSON.createObjectBuilder();

            formatted.ifPresent(it -> objectBuilder.add("formatted", it));
            streetAddress.ifPresent(it -> objectBuilder.add("street_address", it));
            locality.ifPresent(it -> objectBuilder.add("locality", it));
            region.ifPresent(it -> objectBuilder.add("region", it));
            postalCode.ifPresent(it -> objectBuilder.add("postal_code", it));
            country.ifPresent(it -> objectBuilder.add("country", it));

            return objectBuilder.build();
        }
    }
}
