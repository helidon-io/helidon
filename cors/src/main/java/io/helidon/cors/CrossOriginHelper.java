/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.cors;

import io.helidon.common.http.Http;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.helidon.common.http.Http.Header.HOST;
import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_EXPOSE_HEADERS;
import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_MAX_AGE;
import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.cors.CrossOrigin.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.cors.CrossOrigin.ORIGIN;

/**
 * Centralizes common logic to both SE and MP CORS support.
 * <p>
 *     To serve both masters, several methods here accept functions that are intended to operate on items such as headers,
 *     responses, etc. The SE and MP implementations of these items have no common superclasses or interfaces, so the methods
 *     here have to call back to the SE and MP implementations of CORS support to look for or set headers, set response status or
 *     error messages, etc. The JavaDoc explains these functions according to their intended uses, although being functions they
 *     could operate on anything.
 * </p>
 */
public class CrossOriginHelper {

    public static final String CORS_CONFIG_KEY = "cors";

    static final String ORIGIN_DENIED = "CORS origin is denied";
    static final String ORIGIN_NOT_IN_ALLOWED_LIST = "CORS origin is not in allowed list";
    static final String METHOD_NOT_IN_ALLOWED_LIST = "CORS method is not in allowed list";
    static final String HEADERS_NOT_IN_ALLOWED_LIST = "CORS headers not in allowed list";

    /**
     * CORS-related classification of HTTP requests.
     */
    public enum RequestType {
        /**
         * A non-CORS request
         */
        NORMAL,

        /**
         * A CORS request, either a simple one or a non-simple one already preceded by a preflight request
         */
        CORS,

        /**
         * A CORS preflight request
         */
        PREFLIGHT
    }

    /**
     * Analyzes the method and headers to determine the type of request, from the CORS perspective.
     *
     * @param firstHeaderGetter accepts a header name and returns the first value or null if no values exist
     * @param headerContainsKeyChecker sees if a header name exists
     * @param method String containing the HTTP method name
     * @return RequestType
     */
    public static RequestType requestType(Function<String, String> firstHeaderGetter, Function<String, Boolean> headerContainsKeyChecker,
            String method) {
        String origin = firstHeaderGetter.apply(ORIGIN);
        String host = firstHeaderGetter.apply(HOST);
        if (origin == null ||origin.contains("://" + host)) {
            return RequestType.NORMAL;
        }

        // Is this a pre-flight request?
        if (method.equalsIgnoreCase("OPTIONS")
                && headerContainsKeyChecker.apply(ACCESS_CONTROL_REQUEST_METHOD)) {
            return RequestType.PREFLIGHT;
        }

        // A CORS request that is not a pre-flight one
        return RequestType.CORS;
    }

    /**
     * Looks for a matching CORS config entry for the specified path among the provided CORS configuration information, returning
     * an {@code Optional} of the matching {@code CrossOrigin} instance for the path, if any.
     *
     * @param path the possibly unnormalized request path to check
     * @param crossOriginConfigs CORS configuration
     * @return Optional<CrossOrigin> for the matching config, or an empty Optional if none matched
     */
    static Optional<CrossOrigin> lookupCrossOrigin(String path, List<CrossOriginConfig> crossOriginConfigs) {
        for (CrossOriginConfig config : crossOriginConfigs) {
            String pathPrefix = normalize(config.pathPrefix());
            String uriPath = normalize(path);
            if (uriPath.startsWith(pathPrefix)) {
                return Optional.of(config);
            }
        }

        return Optional.empty();
    }

    /**
     * Validates information about an incoming request as a CORS request.
     *
     * @param path possibly-unnormalized path from the request
     * @param crossOriginConfigs config information for CORS
     * @param firstHeaderGetter accepts a header name and returns the first value; null otherwise
     * @param responseSetter accepts an error message and a status code and sets those values in an HTTP response and returns
     *                       that response
     * @param <T> the type for the HTTP response as returned from the responseSetter
     * @return Optional of an error response (returned by the responseSetter) if the request was an invalid CORS request;
     *         Optional.empty() if it was a valid CORS request
     */
    public static <T extends Object> Optional<T> processCorsRequest(String path, List<CrossOriginConfig> crossOriginConfigs,
            Function<String, String> firstHeaderGetter, BiFunction<String, Integer, T> responseSetter) {
        String origin = firstHeaderGetter.apply(ORIGIN);
        Optional<CrossOrigin> crossOrigin = lookupCrossOrigin(path, crossOriginConfigs);
        if (!crossOrigin.isPresent()) {
            return Optional.of(forbidden(ORIGIN_DENIED, responseSetter));
        }

        // If enabled but not whitelisted, deny request
        List<String> allowedOrigins = Arrays.asList(crossOrigin.get().value());
        if (!allowedOrigins.contains("*") && !contains(origin, allowedOrigins, String::equals)) {
            return Optional.of(forbidden(ORIGIN_NOT_IN_ALLOWED_LIST, responseSetter));
        }

        // Successful processing of request
        return Optional.empty();
    }

    /**
     * Prepares a CORS response.
     *
     * @param path the possibly non-normalized path from the request
     * @param crossOriginConfigs config information for CORS
     * @param firstHeaderGetter function which accepts a header name and returns the first value; null otherwise
     * @param headerAdder bi-consumer that accepts a header name and value and (presumably) adds it as a header to an HTTP response
     */
    public static void prepareCorsResponse(String path, List<CrossOriginConfig> crossOriginConfigs,
            Function<String, String> firstHeaderGetter, BiConsumer<String, Object> headerAdder) {
        Optional<CrossOrigin> crossOrigin = lookupCrossOrigin(path, crossOriginConfigs);

        // Add Access-Control-Allow-Origin and Access-Control-Allow-Credentials
        String origin = firstHeaderGetter.apply(ORIGIN);
        if (crossOrigin.get().allowCredentials()) {
            headerAdder.accept(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            headerAdder.accept(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        } else {
            List<String> allowedOrigins = Arrays.asList(crossOrigin.get().value());
            headerAdder.accept(ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigins.contains("*") ? "*" : origin);
        }

        // Add Access-Control-Expose-Headers if non-empty
        formatHeader(crossOrigin.get().exposeHeaders()).ifPresent(
                h -> headerAdder.accept(ACCESS_CONTROL_EXPOSE_HEADERS, h));
    }

    /**
     * Processes a pre-flight request.
     *
     * @param path possibly non-normalized path from the request
     * @param crossOriginConfigs config information for CORS
     * @param firstHeaderGetter accepts a header name and returns the first value, if any; null otherwise
     * @param allHeaderGetter accepts a key and returns a list of values, if any, associated with that key; empty list otherwise
     * @param responseSetter accepts an error message and a status code and sets those values in an HTTP response and returns
     *                       that response
     * @param responseStatusSetter accepts an Integer status code and returns a response with that status set
     * @param headerAdder accepts a header name and value and adds it as a header to an HTTP response
     * @param <T> the type for the returned HTTP response (as returned from the response setter functions)
     * @return the T returned by the responseStatusSetter with CORS-related headers set via headerAdder (for a successful
     */
    public static <T> T processPreFlight(String path,
            List<CrossOriginConfig> crossOriginConfigs, Function<String, String> firstHeaderGetter,
            Function<String, List<String>> allHeaderGetter,
            BiFunction<String, Integer, T> responseSetter, Function<Integer, T> responseStatusSetter,
            BiConsumer<String, Object> headerAdder) {

        String origin = firstHeaderGetter.apply(ORIGIN);
        Optional<CrossOrigin> crossOrigin = lookupCrossOrigin(path, crossOriginConfigs);

        // If CORS not enabled, deny request
        if (!crossOrigin.isPresent()) {
            return forbidden(ORIGIN_DENIED, responseSetter);
        }

        // If enabled but not whitelisted, deny request
        List<String> allowedOrigins = Arrays.asList(crossOrigin.get().value());
        if (!allowedOrigins.contains("*") && !contains(origin, allowedOrigins, String::equals)) {
            return forbidden(ORIGIN_NOT_IN_ALLOWED_LIST, responseSetter);
        }

        // Check if method is allowed
        String method = firstHeaderGetter.apply(ACCESS_CONTROL_REQUEST_METHOD);
        List<String> allowedMethods = Arrays.asList(crossOrigin.get().allowMethods());
        if (!allowedMethods.contains("*") && !contains(method, allowedMethods, String::equals)) {
            return forbidden(METHOD_NOT_IN_ALLOWED_LIST, responseSetter);
        }

        // Check if headers are allowed
        Set<String> requestHeaders = parseHeader(allHeaderGetter.apply(ACCESS_CONTROL_REQUEST_HEADERS));
        List<String> allowedHeaders = Arrays.asList(crossOrigin.get().allowHeaders());
        if (!allowedHeaders.contains("*") && !contains(requestHeaders, allowedHeaders)) {
            return forbidden(HEADERS_NOT_IN_ALLOWED_LIST, responseSetter);
        }

        // Build successful response
        T response = responseStatusSetter.apply(Http.Status.OK_200.code());
        headerAdder.accept(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        if (crossOrigin.get().allowCredentials()) {
            headerAdder.accept(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
        headerAdder.accept(ACCESS_CONTROL_ALLOW_METHODS, method);
        formatHeader(requestHeaders.toArray()).ifPresent(
                h -> headerAdder.accept(ACCESS_CONTROL_ALLOW_HEADERS, h));
        long maxAge = crossOrigin.get().maxAge();
        if (maxAge > 0) {
            headerAdder.accept(ACCESS_CONTROL_MAX_AGE, maxAge);
        }
        return response;
    }

    /**
     * Formats an array as a comma-separate list without brackets.
     *
     * @param array The array.
     * @param <T> Type of elements in array.
     * @return Formatted array as an {@code Optional}.
     */
    static <T> Optional<String> formatHeader(T[] array) {
        if (array == null || array.length == 0) {
            return Optional.empty();
        }
        int i = 0;
        StringBuilder builder = new StringBuilder();
        do {
            builder.append(array[i++].toString());
            if (i == array.length) {
                break;
            }
            builder.append(", ");
        } while (true);
        return Optional.of(builder.toString());
    }

    /**
     * Parse list header value as a set.
     *
     * @param header Header value as a list.
     * @return Set of header values.
     */
    static Set<String> parseHeader(String header) {
        if (header == null) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        StringTokenizer tokenizer = new StringTokenizer(header, ",");
        while (tokenizer.hasMoreTokens()) {
            String value = tokenizer.nextToken().trim();
            if (value.length() > 0) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * Parse a list of list of headers as a set.
     *
     * @param headers Header value as a list, each a potential list.
     * @return Set of header values.
     */
    static Set<String> parseHeader(List<String> headers) {
        if (headers == null) {
            return Collections.emptySet();
        }
        return parseHeader(headers.stream().reduce("", (a, b) -> a + "," + b));
    }

    /**
     * Trim leading or trailing slashes of a path.
     *
     * @param path The path.
     * @return Normalized path.
     */
    static String normalize(String path) {
        int length = path.length();
        int beginIndex = path.charAt(0) == '/' ? 1 : 0;
        int endIndex = path.charAt(length - 1) == '/' ? length - 1 : length;
        return path.substring(beginIndex, endIndex);
    }

    /**
     * Returns response with forbidden status and entity created from message.
     *
     * @param message Message in entity.
     * @return A {@code Response} instance.
     */
    static <T extends Object> T forbidden(String message, BiFunction<String, Integer, T> responseSetter) {
        return responseSetter.apply(message, Http.Status.FORBIDDEN_403.code());
    }

    /**
     * Checks containment in a {@code Collection<String>}.
     *
     * @param item The string.
     * @param collection The collection.
     * @param eq Equality function.
     * @return Outcome of test.
     */
    static boolean contains(String item, Collection<String> collection, BiFunction<String, String, Boolean> eq) {
        for (String s : collection) {
            if (eq.apply(item, s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks containment in two collections, case insensitively.
     *
     * @param left First collection.
     * @param right Second collection.
     * @return Outcome of test.
     */
    static boolean contains(Collection<String> left, Collection<String> right) {
        for (String s : left) {
            if (!contains(s, right, String::equalsIgnoreCase)) {
                return false;
            }
        }
        return true;
    }
}
