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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

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
 *     To serve both masters, several methods here accept an adapter for requests and a factory for responses. Both of these
 *     are minimal and very specific to the needs of CORS support.
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
     * Minimal abstraction of an HTTP request.
     */
    public interface RequestAdapter {

        /**
         * Retrieves the first value for the specified header as a String.
         *
         * @param key header name to retrieve
         * @return the first header value for the key
         */
        Optional<String> firstHeader(String key);

        /**
         * Reports whether the specified header exists.
         *
         * @param key header name to check for
         * @return whether the header exists among the request's headers
         */
        boolean headerContainsKey(String key);

        /**
         * Retrieves all header values for a given key as Strings.
         *
         * @param key header name to retrieve
         * @return header values for the header; empty list if none
         */
        List<String> allHeaders(String key);

        /**
         * Reports the method name for the request.
         *
         * @return the method name
         */
        String method();
    }

    /**
     * Minimal abstraction of an HTTP response factory.
     *
     * @param <T> the type of the response created by the factory
     */
    public interface ResponseFactory<T> {

        /**
         * Arranges to add the specified header and value to the eventual response.
         *
         * @param key header name to add
         * @param value header value to add
         * @return the factory
         */
        ResponseFactory<T> addHeader(String key, String value);

        /**
         * Arranges to add the specified header and value to the eventual response.
         *
         * @param key header name to add
         * @param value header value to add
         * @return the factory
         */
        ResponseFactory<T> addHeader(String key, Object value);


        /**
         * Returns an instance of the response type with the forbidden status and the specified error mesage.
         *
         * @param message error message to use in setting the response status
         * @return the factory
         */
        T forbidden(String message);

        /**
         * Returns an instance of the response type with headers set and status set to OK.
         *
         * @return new response instance
         */
        T build();
    }

    /**
     * Analyzes the method and headers to determine the type of request, from the CORS perspective.
     *
     * @param request request adatper
     * @return RequestType the CORS request type of the request
     */
    public static RequestType requestType(RequestAdapter request) {
        Optional<String> origin = request.firstHeader(ORIGIN);
        Optional<String> host = request.firstHeader(HOST);
        if (origin.isEmpty() || origin.get().contains("://" + host)) {
            return RequestType.NORMAL;
        }

        // Is this a pre-flight request?
        if (request.method().equalsIgnoreCase("OPTIONS")
                && request.headerContainsKey(ACCESS_CONTROL_REQUEST_METHOD)) {
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
    static Optional<CrossOrigin> lookupCrossOrigin(String path, List<CrossOriginConfig> crossOriginConfigs,
            Supplier<Optional<CrossOrigin>> secondaryLookup) {
        for (CrossOriginConfig config : crossOriginConfigs) {
            String pathPrefix = normalize(config.pathPrefix());
            String uriPath = normalize(path);
            if (uriPath.startsWith(pathPrefix)) {
                return Optional.of(config);
            }
        }

        return secondaryLookup.get();
    }

    /**
     * Validates information about an incoming request as a CORS request.
     *
     * @param path possibly-unnormalized path from the request
     * @param crossOriginConfigs config information for CORS
     * @param secondaryCrossOriginLookup locates {@code CrossOrigin} from other than config (e.g., annotations for MP)
     * @param request abstraction of a request
     * @param responseFactory factory for creating a response and managing its attributes (e.g., headers)
     * @param <T> the type for the HTTP response as returned from the responseSetter
     * @return Optional of an error response (returned by the responseSetter) if the request was an invalid CORS request;
     *         Optional.empty() if it was a valid CORS request
     */
    public static <T> Optional<T> processRequest(String path,
            List<CrossOriginConfig> crossOriginConfigs,
            Supplier<Optional<CrossOrigin>> secondaryCrossOriginLookup,
            RequestAdapter request,
            ResponseFactory<T> responseFactory) {
        Optional<String> origin = request.firstHeader(ORIGIN);
        Optional<CrossOrigin> crossOrigin = lookupCrossOrigin(path, crossOriginConfigs, secondaryCrossOriginLookup);
        if (crossOrigin.isEmpty()) {
            return Optional.of(responseFactory.forbidden(ORIGIN_DENIED));
        }

        // If enabled but not whitelisted, deny request
        List<String> allowedOrigins = Arrays.asList(crossOrigin.get().value());
        if (!allowedOrigins.contains("*") && !contains(origin, allowedOrigins, String::equals)) {
            return Optional.of(responseFactory.forbidden(ORIGIN_NOT_IN_ALLOWED_LIST));
        }

        // Successful processing of request
        return Optional.empty();
    }

    /**
     * Prepares a CORS response.
     *
     * @param path the possibly non-normalized path from the request
     * @param crossOriginConfigs config information for CORS
     * @param secondaryCrossOriginLookup locates {@code CrossOrigin} from other than config (e.g., annotations for MP)
     * @param request request
     * @param responseFactory response factory
     */
    public static <T> T prepareResponse(String path, List<CrossOriginConfig> crossOriginConfigs,
            Supplier<Optional<CrossOrigin>> secondaryCrossOriginLookup,
            RequestAdapter request,
            ResponseFactory<T> responseFactory) {
        CrossOrigin crossOrigin = lookupCrossOrigin(path, crossOriginConfigs, secondaryCrossOriginLookup)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Could not locate expected CORS information while preparing response to request " + request));

        // Add Access-Control-Allow-Origin and Access-Control-Allow-Credentials
        Optional<String> originOpt = request.firstHeader(ORIGIN);
        if (originOpt.isEmpty()) {
            return responseFactory.forbidden(noRequiredHeader(ORIGIN));
        }
        String origin = originOpt.get();
        if (crossOrigin.allowCredentials()) {
            responseFactory.addHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                           .addHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        } else {
            List<String> allowedOrigins = Arrays.asList(crossOrigin.value());
            responseFactory.addHeader(ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigins.contains("*") ? "*" : origin);
        }

        // Add Access-Control-Expose-Headers if non-empty
        formatHeader(crossOrigin.exposeHeaders()).ifPresent(
                h -> responseFactory.addHeader(ACCESS_CONTROL_EXPOSE_HEADERS, h));

        return responseFactory.build();
    }

    /**
     * Processes a pre-flight request.
     *
     * @param path possibly non-normalized path from the request
     * @param crossOriginConfigs config information for CORS
     * @param secondaryCrossOriginLookup locates {@code CrossOrigin} from other than config (e.g., annotations for MP)
     * @param request the request
     * @param responseFactory factory for preparing and creating the response
     * @param <T> the type for the returned HTTP response (as returned from the response setter functions)
     * @return the T returned by the responseStatusSetter with CORS-related headers set via headerAdder (for a successful
     */
    public static <T> T processPreFlight(String path,
            List<CrossOriginConfig> crossOriginConfigs,
            Supplier<Optional<CrossOrigin>> secondaryCrossOriginLookup,
            RequestAdapter request,
            ResponseFactory<T> responseFactory) {

        Optional<String> originOpt = request.firstHeader(ORIGIN);
        Optional<CrossOrigin> crossOriginOpt = lookupCrossOrigin(path, crossOriginConfigs, secondaryCrossOriginLookup);

        // If CORS not enabled, deny request
        if (crossOriginOpt.isEmpty()) {
            return responseFactory.forbidden(ORIGIN_DENIED);
        }
        if (originOpt.isEmpty()) {
            return responseFactory.forbidden(noRequiredHeader(ORIGIN));
        }

        CrossOrigin crossOrigin = crossOriginOpt.get();

        // If enabled but not whitelisted, deny request
        List<String> allowedOrigins = Arrays.asList(crossOrigin.value());
        if (!allowedOrigins.contains("*") && !contains(originOpt, allowedOrigins, String::equals)) {
            return responseFactory.forbidden(ORIGIN_NOT_IN_ALLOWED_LIST);
        }

        // Check if method is allowed
        Optional<String> method = request.firstHeader(ACCESS_CONTROL_REQUEST_METHOD);
        List<String> allowedMethods = Arrays.asList(crossOrigin.allowMethods());
        if (!allowedMethods.contains("*") && !contains(method, allowedMethods, String::equals)) {
            return responseFactory.forbidden(METHOD_NOT_IN_ALLOWED_LIST);
        }

        // Check if headers are allowed
        Set<String> requestHeaders = parseHeader(request.allHeaders(ACCESS_CONTROL_REQUEST_HEADERS));
        List<String> allowedHeaders = Arrays.asList(crossOrigin.allowHeaders());
        if (!allowedHeaders.contains("*") && !contains(requestHeaders, allowedHeaders)) {
            return responseFactory.forbidden(HEADERS_NOT_IN_ALLOWED_LIST);
        }

        // Build successful response

        responseFactory.addHeader(ACCESS_CONTROL_ALLOW_ORIGIN, originOpt.get());
        if (crossOrigin.allowCredentials()) {
            responseFactory.addHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
        responseFactory.addHeader(ACCESS_CONTROL_ALLOW_METHODS,
                method.orElseThrow(noRequiredHeaderExcFactory(ACCESS_CONTROL_REQUEST_METHOD)));
        formatHeader(requestHeaders.toArray()).ifPresent(
                h -> responseFactory.addHeader(ACCESS_CONTROL_ALLOW_HEADERS, h));
        long maxAge = crossOrigin.maxAge();
        if (maxAge > 0) {
            responseFactory.addHeader(ACCESS_CONTROL_MAX_AGE, maxAge);
        }
        return responseFactory.build();
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
     * Checks containment in a {@code Collection<String>}.
     *
     * @param item The string.
     * @param collection The collection.
     * @param eq Equality function.
     * @return Outcome of test.
     */
    static boolean contains(Optional<String> item, Collection<String> collection, BiFunction<String, String, Boolean> eq) {
        return item.isPresent() ? contains(item.get(), collection, eq) : false;
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

    private static Supplier<IllegalArgumentException> noRequiredHeaderExcFactory(String header) {
        return () -> new IllegalArgumentException(noRequiredHeader(header));
    }

    private static String noRequiredHeader(String header) {
        return "CORS request does not have required header " + header;
    }
}
