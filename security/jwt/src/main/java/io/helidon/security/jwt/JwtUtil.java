/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.crypto.Mac;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;

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
        return json.arrayValue(key)
                .map(it -> {
                    try {
                        return it.values().stream().map(JsonValue::asString).map(JsonString::value).collect(Collectors.toList());
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
        return json.value(key)
                .filter(JsonString.class::isInstance)
                .map(JsonValue::asString)
                .map(JsonString::value);
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
    public static Map<String, JsonValue> transformToJsonValue(Map<String, Object> claims) {
        Map<String, JsonValue> result = new HashMap<>();

        claims.forEach((s, o) -> result.put(s, toJsonValue(o)));

        return result;
    }

    /**
     * Create a {@link io.helidon.json.JsonValue} from an object.
     * This will use correct types for known primitives, {@link io.helidon.security.jwt.JwtUtil.Address}
     * otherwise it uses String value.
     *
     * @param object object to create json value from
     * @return json value
     */
    public static JsonValue toJsonValue(Object object) {
        JsonValue simpleValue = knownJsonValue(object);
        if (simpleValue != null) {
            return simpleValue;
        }
        if (object instanceof Collection) {
            return toJsonArray((Collection<?>) object);
        }
        return JsonString.create(String.valueOf(object));
    }

    private static JsonValue knownJsonValue(Object object) {
        if (object instanceof JsonValue) {
            return (JsonValue) object;
        }
        if (object instanceof String) {
            return JsonString.create((String) object);
        }
        if (object instanceof Integer) {
            return JsonNumber.create(new BigDecimal((Integer) object));
        }
        if (object instanceof Double) {
            return JsonNumber.create(BigDecimal.valueOf((Double) object));
        }
        if (object instanceof Long) {
            return JsonNumber.create(new BigDecimal((Long) object));
        }
        if (object instanceof BigDecimal) {
            return JsonNumber.create((BigDecimal) object);
        }
        if (object instanceof BigInteger) {
            return JsonNumber.create(new BigDecimal((BigInteger) object));
        }
        if (object instanceof Boolean) {
            return JsonBoolean.create((Boolean) object);
        }
        if (object instanceof Address) {
            return ((Address) object).jsonObject();
        }
        return null;
    }

    private static JsonArray toJsonArray(Collection<?> values) {
        List<JsonValue> jsonValues = new ArrayList<>(values.size());

        values.forEach(value -> addJsonArrayValue(jsonValues, value));

        return JsonArray.create(jsonValues);
    }

    private static JsonArray toJsonArray(Object[] values) {
        return toJsonArray(Arrays.asList(values));
    }

    private static JsonArray toJsonArray(int[] values) {
        List<JsonValue> jsonValues = new ArrayList<>(values.length);
        for (int value : values) {
            jsonValues.add(JsonNumber.create((long) value));
        }
        return JsonArray.create(jsonValues);
    }

    private static JsonArray toJsonArray(long[] values) {
        List<JsonValue> jsonValues = new ArrayList<>(values.length);
        for (long value : values) {
            jsonValues.add(JsonNumber.create(value));
        }
        return JsonArray.create(jsonValues);
    }

    private static JsonArray toJsonArray(double[] values) {
        List<JsonValue> jsonValues = new ArrayList<>(values.length);
        for (double value : values) {
            jsonValues.add(JsonNumber.create(value));
        }
        return JsonArray.create(jsonValues);
    }

    private static JsonArray toJsonArray(boolean[] values) {
        List<JsonValue> jsonValues = new ArrayList<>(values.length);
        for (boolean value : values) {
            jsonValues.add(JsonBoolean.create(value));
        }
        return JsonArray.create(jsonValues);
    }

    private static JsonArray toJsonArray(char[] values) {
        List<JsonValue> jsonValues = new ArrayList<>(values.length);
        for (char value : values) {
            jsonValues.add(JsonNumber.create((long) value));
        }
        return JsonArray.create(jsonValues);
    }

    private static JsonArray toJsonArray(float[] values) {
        List<JsonValue> jsonValues = new ArrayList<>(values.length);
        for (float value : values) {
            jsonValues.add(JsonNumber.create((double) value));
        }
        return JsonArray.create(jsonValues);
    }

    private static JsonArray toJsonArray(byte[] values) {
        List<JsonValue> jsonValues = new ArrayList<>(values.length);
        for (byte value : values) {
            jsonValues.add(JsonNumber.create((long) value));
        }
        return JsonArray.create(jsonValues);
    }

    private static JsonArray toJsonArray(short[] values) {
        List<JsonValue> jsonValues = new ArrayList<>(values.length);
        for (short value : values) {
            jsonValues.add(JsonNumber.create((long) value));
        }
        return JsonArray.create(jsonValues);
    }

    private static JsonObject toJsonObject(Map<?, ?> values) {
        JsonObject.Builder builder = JsonObject.builder();

        values.forEach((key, value) -> addJsonObjectValue(builder, (String) key, value));

        return builder.build();
    }

    private static void addJsonArrayValue(List<JsonValue> jsonValues, Object value) {
        if (value instanceof Optional<?> optionalValue) {
            optionalValue.ifPresent(it -> jsonValues.add(toNestedJsonValue(it)));
            return;
        }

        jsonValues.add(toNestedJsonValue(value));
    }

    private static void addJsonObjectValue(JsonObject.Builder builder, String key, Object value) {
        if (value instanceof Optional<?> optionalValue) {
            optionalValue.ifPresent(it -> builder.set(key, toNestedJsonValue(it)));
            return;
        }

        builder.set(key, toNestedJsonValue(value));
    }

    private static JsonValue toNestedJsonValue(Object object) {
        if (object == null) {
            return JsonNull.instance();
        }

        JsonValue simpleValue = knownJsonValue(object);
        if (simpleValue != null) {
            return simpleValue;
        }
        if (object instanceof Collection) {
            return toJsonArray((Collection<?>) object);
        }
        if (object instanceof Map) {
            return toJsonObject((Map<?, ?>) object);
        }
        if (object instanceof Object[]) {
            return toJsonArray((Object[]) object);
        }
        if (object instanceof int[]) {
            return toJsonArray((int[]) object);
        }
        if (object instanceof long[]) {
            return toJsonArray((long[]) object);
        }
        if (object instanceof double[]) {
            return toJsonArray((double[]) object);
        }
        if (object instanceof boolean[]) {
            return toJsonArray((boolean[]) object);
        }
        if (object instanceof char[]) {
            return toJsonArray((char[]) object);
        }
        if (object instanceof float[]) {
            return toJsonArray((float[]) object);
        }
        if (object instanceof byte[]) {
            return toJsonArray((byte[]) object);
        }
        if (object instanceof short[]) {
            return toJsonArray((short[]) object);
        }
        throw new IllegalArgumentException(String.format("Type %s is not supported.", object.getClass()));
    }

    private static Locale toLocale(String locale) {
        Matcher matcher = LOCALE_PATTERN.matcher(locale);
        Locale result;
        if (matcher.matches()) {
            result = Locale.of(matcher.group(1), matcher.group(2));
        } else {
            result = Locale.forLanguageTag(locale);
        }
        return result;
    }

    static Optional<Address> toAddress(JsonObject json, String name) {
        return json.objectValue(name)
                .map(Address::new);
    }

    static Optional<List<String>> toScopes(JsonObject json) {
        if (json.value(Jwt.SCOPE).filter(it -> it.type() == io.helidon.json.JsonValueType.ARRAY).isPresent()) {
            return getStrings(json, Jwt.SCOPE);
        } else {
            return getString(json, Jwt.SCOPE)
                    .map(it -> Arrays.asList(it.split(" ")));
        }
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
            return Optional.of(json.booleanValue(name, false));
        }
        return Optional.empty();
    }

    static Optional<Instant> toInstant(JsonObject json, String name) {
        return json.numberValue(name)
                .map(BigDecimal::longValue)
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
        switch (jsonValue.type()) {
        case ARRAY:
            return jsonValue.toString();
        case OBJECT:
            return jsonValue.toString();
        case STRING:
            return jsonValue.asString().value();
        case NUMBER:
            return jsonValue.asNumber().bigDecimalValue();
        case BOOLEAN:
            return jsonValue.asBoolean().value();
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
        public JsonObject jsonObject() {
            JsonObject.Builder objectBuilder = JsonObject.builder();

            formatted.ifPresent(it -> objectBuilder.set("formatted", it));
            streetAddress.ifPresent(it -> objectBuilder.set("street_address", it));
            locality.ifPresent(it -> objectBuilder.set("locality", it));
            region.ifPresent(it -> objectBuilder.set("region", it));
            postalCode.ifPresent(it -> objectBuilder.set("postal_code", it));
            country.ifPresent(it -> objectBuilder.set("country", it));

            return objectBuilder.build();
        }

    }
}
