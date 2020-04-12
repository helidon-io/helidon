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
package io.helidon.webserver.cors;

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
import java.util.logging.Logger;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.webserver.cors.LogHelper.Headers;

import static io.helidon.common.http.Http.Header.HOST;
import static io.helidon.common.http.Http.Header.ORIGIN;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_EXPOSE_HEADERS;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_MAX_AGE;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.webserver.cors.CrossOriginConfig.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.webserver.cors.LogHelper.DECISION_LEVEL;

/**
 * <em>Not for use by developers.</em>
 *
 * Centralizes internal logic common to both SE and MP CORS support for processing requests and preparing responses.
 *
 * <p>This class is reserved for internal Helidon use. Do not use it from your applications. It might change or vanish at
 *  any time.</p>
 *  <p>
 * To serve both masters, several methods here accept adapters for requests and responses. Both of these are minimal and very
 * specific to the needs of CORS support.
 * </p>
 */
class CrossOriginHelper {

    /**
     * Key for the node within the CORS config indicating whether CORS support is enabled.
     */
    static final String CORS_ENABLED_CONFIG_KEY = "enabled";

    static final String ORIGIN_DENIED = "CORS origin is denied";
    static final String ORIGIN_NOT_IN_ALLOWED_LIST = "CORS origin is not in allowed list";
    static final String METHOD_NOT_IN_ALLOWED_LIST = "CORS method is not in allowed list";
    static final String HEADERS_NOT_IN_ALLOWED_LIST = "CORS headers not in allowed list";

    static final Logger LOGGER = Logger.getLogger(CrossOriginHelper.class.getName());

    private static final Supplier<Optional<CrossOriginConfig>> EMPTY_SECONDARY_SUPPLIER = Optional::empty;

    /**
     * Trim leading or trailing slashes of a path.
     *
     * @param path The path.
     * @return Normalized path.
     */
    public static String normalize(String path) {
        int length = path.length();
        int beginIndex = path.charAt(0) == '/' ? 1 : 0;
        int endIndex = path.charAt(length - 1) == '/' ? length - 1 : length;
        return (endIndex <= beginIndex) ? "" : path.substring(beginIndex, endIndex);
    }

    /**
     * Parse list header value as a set.
     *
     * @param header Header value as a list.
     * @return Set of header values.
     */
    public static Set<String> parseHeader(String header) {
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
    public static Set<String> parseHeader(List<String> headers) {
        if (headers == null) {
            return Collections.emptySet();
        }
        return parseHeader(headers.stream().reduce("", (a, b) -> a + "," + b));
    }

    /**
     * <em>Not for use by developers.</em>
     *
     * CORS-related classification of HTTP requests.
     */
    public enum RequestType {
        /**
         * A non-CORS request.
         */
        NORMAL,

        /**
         * A CORS request, either a simple one or a non-simple one already preceded by a preflight request.
         */
        CORS,

        /**
         * A CORS preflight request.
         */
        PREFLIGHT
    }
    static {
        HelidonFeatures.register(HelidonFlavor.SE, "CORS");
    }

    /**
     * Creates a new instance using CORS config in the provided {@link Config}.
     *
     * @param config Config node containing CORS set-up
     * @return new instance based on the config
     */
    public static CrossOriginHelper create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Creates a new instance that is enabled but with no path mappings.
     *
     * @return the new instance
     */
    public static CrossOriginHelper create() {
        return builder().build();
    }

    private final CrossOriginConfigAggregator aggregator;
    private final Supplier<Optional<CrossOriginConfig>> secondaryCrossOriginLookup;

    private CrossOriginHelper() {
        this(builder());
    }

    private CrossOriginHelper(Builder builder) {
        aggregator = builder.aggregator;
        secondaryCrossOriginLookup = builder.secondaryCrossOriginLookup;
    }

    /**
     * Creates a builder for a new {@code CrossOriginHelper}.
     *
     * @return initialized builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for {@code CrossOriginHelper}s.
     */
    public static class Builder implements io.helidon.common.Builder<CrossOriginHelper> {

        private Supplier<Optional<CrossOriginConfig>> secondaryCrossOriginLookup = EMPTY_SECONDARY_SUPPLIER;

        private final CrossOriginConfigAggregator aggregator = CrossOriginConfigAggregator.create();

        /**
         * Sets the supplier for the secondary lookup of CORS information (typically <em>not</em> contained in
         * configuration).
         *
         * @param secondaryLookup the supplier
         * @return updated builder
         */
        public Builder secondaryLookupSupplier(Supplier<Optional<CrossOriginConfig>> secondaryLookup) {
            secondaryCrossOriginLookup = secondaryLookup;
            return this;
        }

        /**
         * Adds cross-origin information via config.
         *
         * @param config config node containing CORS set-up information
         * @return updated builder
         */
        public Builder config(Config config) {
            aggregator.config(config);
            return this;
        }

        /**
         * Creates the {@code CrossOriginHelper}.
         *
         * @return initialized {@code CrossOriginHelper}
         */
        public CrossOriginHelper build() {
            CrossOriginHelper result = new CrossOriginHelper(this);

            LOGGER.config(() -> String.format("CrossOriginHelper configured as: %s", result.toString()));

            return result;
        }

        CrossOriginConfigAggregator aggregator() {
            return aggregator;
        }
    }

    /**
     * Reports whether this helper, due to its set-up, will affect any requests or responses.
     *
     * @return whether the helper will have any effect on requests or responses
     */
    public boolean isActive() {
        return aggregator.isEnabled();
    }

    /**
     * Processes a request according to the CORS rules, returning an {@code Optional} of the response type if
     * the caller should send the response immediately (such as for a preflight response or an error response to a
     * non-preflight CORS request).
     * <p>
     *     If the optional is empty, this processor has either:
     * </p>
     * <ul>
     *     <li>recognized the request as a valid non-preflight CORS request and has set headers in the response adapter, or</li>
     *     <li>recognized the request as a non-CORS request entirely.</li>
     * </ul>
     * <p>
     * In either case of an empty optional return value, the caller should proceed with its own request processing and sends its
     * response at will as long as that processing includes the header settings assigned using the response adapter.
     * </p>
     *
     * @param requestAdapter abstraction of a request
     * @param responseAdapter abstraction of a response
     * @param <T> type for the {@code Request} managed by the requestAdapter
     * @param <U> the type for the HTTP response as returned from the responseSetter
     * @return Optional of an error response if the request was an invalid CORS request; Optional.empty() if it was a
     *         valid CORS request
     */
    public <T, U> Optional<U> processRequest(RequestAdapter<T> requestAdapter, ResponseAdapter<U> responseAdapter) {

        if (!isActive()) {
            LOGGER.log(DECISION_LEVEL, () -> String.format("CORS ignoring request %s; processing is disabled", requestAdapter));
            requestAdapter.next();
            return Optional.empty();
        }

        Optional<CrossOriginConfig> crossOrigin = aggregator.lookupCrossOrigin(requestAdapter.path(), secondaryCrossOriginLookup);

        RequestType requestType = requestType(requestAdapter);

        if (requestType == RequestType.NORMAL) {
            LOGGER.log(DECISION_LEVEL, "passing normal request through unchanged");
            return Optional.empty();
        }

        // If this is a CORS request of some sort and there is no matching CORS configuration, deny the request.
        if (crossOrigin.isEmpty()) {
            return Optional.of(forbid(requestAdapter, responseAdapter, ORIGIN_DENIED,
                    () -> "no matching CORS configuration for path " + requestAdapter.path()));
        }
        return processRequest(requestType, crossOrigin.get(), requestAdapter, responseAdapter);
    }

    @Override
    public String toString() {
        return String.format("CrossOriginHelper{isActive=%s, crossOriginConfigs=%s, secondaryCrossOriginLookup=%s}",
                isActive(), aggregator, secondaryCrossOriginLookup == EMPTY_SECONDARY_SUPPLIER ? "(not set)" : "(set)");
    }

    static <T, U> Optional<U> processRequest(RequestType requestType, CrossOriginConfig crossOrigin,
            RequestAdapter<T> requestAdapter,
            ResponseAdapter<U> responseAdapter) {

        switch (requestType) {
            case PREFLIGHT:
                U result = processCORSPreFlightRequest(crossOrigin, requestAdapter,
                        responseAdapter);
                return Optional.of(result);

            case CORS:
                Optional<U> corsResponse = processCORSRequest(crossOrigin, requestAdapter,
                        responseAdapter);
                if (corsResponse.isEmpty()) {
                    /*
                     * There has been no rejection of the CORS settings, so prep the response headers.
                     */
                    addCORSHeadersToResponse(crossOrigin, requestAdapter, responseAdapter);
                }
                return corsResponse;

            default:
                throw new IllegalArgumentException("Unexpected value for enum RequestType");
        }
    }

    /**
     * Prepares a response with CORS headers, if the supplied request is in fact a CORS request.
     *
     * @param requestAdapter abstraction of a request
     * @param responseAdapter abstraction of a response
     * @param <T> type for the {@code Request} managed by the requestAdapter
     * @param <U> the type for the HTTP response as returned from the responseSetter
     */
    public <T, U> void prepareResponse(RequestAdapter<T> requestAdapter, ResponseAdapter<U> responseAdapter) {

        if (!isActive()) {
            LOGGER.log(DECISION_LEVEL,
                    () -> String.format("CORS ignoring request %s; CORS processing is dieabled", requestAdapter));
            return;
        }

        RequestType requestType = requestType(requestAdapter, true); // silent: already logged during req processing

        if (requestType == RequestType.CORS) {
            CrossOriginConfig crossOrigin = aggregator.lookupCrossOrigin(
                            requestAdapter.path(),
                            secondaryCrossOriginLookup)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Could not locate expected CORS information while preparing response to request " + requestAdapter));
            addCORSHeadersToResponse(crossOrigin, requestAdapter, responseAdapter);
        }
    }

    /**
     * Analyzes the request to determine the type of request, from the CORS perspective.
     *
     * @param requestAdapter request adatper
     * @param <T> type of request wrapped by the adapter
     * @return RequestType the CORS request type of the request
     */
    static <T> RequestType requestType(RequestAdapter<T> requestAdapter, boolean silent) {
        if (isRequestTypeNormal(requestAdapter, silent)) {
            return RequestType.NORMAL;
        }

        return inferCORSRequestType(requestAdapter, silent);
    }

    static <T> RequestType requestType(RequestAdapter<T> requestAdapter) {
        return requestType(requestAdapter, false);
    }

    private static <T> boolean isRequestTypeNormal(RequestAdapter<T> requestAdapter, boolean silent) {
        // If no origin header or same as host, then just normal
        Optional<String> originOpt = requestAdapter.firstHeader(ORIGIN);
        Optional<String> hostOpt = requestAdapter.firstHeader(HOST);

        boolean result = originOpt.isEmpty() || (hostOpt.isPresent() && originOpt.get().contains("://" + hostOpt.get()));
        if (!silent && LOGGER.isLoggable(DECISION_LEVEL)) {
            LogHelper.isRequestTypeNormal(result, requestAdapter, originOpt, hostOpt);
        }
        return result;
    }

    private static <T> RequestType inferCORSRequestType(RequestAdapter<T> requestAdapter, boolean silent) {

        String methodName = requestAdapter.method();
        boolean isMethodOPTION = methodName.equalsIgnoreCase(Http.Method.OPTIONS.name());
        boolean requestContainsAccessControlRequestMethodHeader = requestAdapter.headerContainsKey(ACCESS_CONTROL_REQUEST_METHOD);

        RequestType result = isMethodOPTION && requestContainsAccessControlRequestMethodHeader
                ? RequestType.PREFLIGHT
                : RequestType.CORS;

        if (!silent && !LOGGER.isLoggable(DECISION_LEVEL)) {
            LogHelper.inferCORSRequestType(result, requestAdapter, methodName, requestContainsAccessControlRequestMethodHeader);
        }
        return result;
    }

    /**
     * Validates information about an incoming request as a CORS request and, if anything is wrong with CORS information,
     * returns an {@code Optional} error response reporting the problem.
     *
     * @param crossOriginConfig the CORS settings to apply to this request
     * @param requestAdapter abstraction of a request
     * @param responseAdapter abstraction of a response
     * @param <T> type for the request wrapped by the requestAdapter
     * @param <U> type for the response wrapper by the responseAdapter
     * @return Optional of an error response if the request was an invalid CORS request; Optional.empty() if it was a
     *         valid CORS request
     */
    static <T, U> Optional<U> processCORSRequest(
            CrossOriginConfig crossOriginConfig,
            RequestAdapter<T> requestAdapter,
            ResponseAdapter<U> responseAdapter) {

        // If enabled but not whitelisted, deny request
        List<String> allowedOrigins = Arrays.asList(crossOriginConfig.allowOrigins());
        Optional<String> originOpt = requestAdapter.firstHeader(ORIGIN);
        if (!allowedOrigins.contains("*") && !contains(originOpt, allowedOrigins, String::equals)) {
            return Optional.of(forbid(requestAdapter,
                    responseAdapter,
                    ORIGIN_NOT_IN_ALLOWED_LIST,
                    () -> String.format("actual: %s, allowed: %s", originOpt.orElse("(MISSING)"), allowedOrigins)));
        }

        // Successful processing of request
        return Optional.empty();
    }

    /**
     * Prepares a CORS response by updating the response's headers.
     *
     * @param crossOrigin the CORS settings to apply to the response
     * @param requestAdapter request adapter
     * @param responseAdapter response adapter
     * @param <T> type for the request wrapped by the requestAdapter
     * @param <U> type for the response wrapper by the responseAdapter
     */
    static <T, U> void addCORSHeadersToResponse(CrossOriginConfig crossOrigin,
            RequestAdapter<T> requestAdapter,
            ResponseAdapter<U> responseAdapter) {
        // Add Access-Control-Allow-Origin and Access-Control-Allow-Credentials.
        //
        // Throw an exception if there is no ORIGIN because we should not even be here unless this is a CORS request, which would
        // have required the ORIGIN heading to be present when we determined the request type.
        String origin = requestAdapter.firstHeader(ORIGIN).orElseThrow(noRequiredHeaderExcFactory(ORIGIN));

        if (crossOrigin.allowCredentials()) {
            new Headers()
                    .add(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                    .add(ACCESS_CONTROL_ALLOW_ORIGIN, origin)
                    .add(Http.Header.VARY, ORIGIN)
                    .set(responseAdapter::header, "allow-credentials was set in CORS config");
        } else {
            List<String> allowedOrigins = Arrays.asList(crossOrigin.allowOrigins());
            new Headers()
                    .add(ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigins.contains("*") ? "*" : origin)
                    .add(Http.Header.VARY, ORIGIN)
                    .set(responseAdapter::header, "allow-credentials was not set in CORS config");
        }

        // Add Access-Control-Expose-Headers if non-empty
        Headers headers = new Headers();
        formatHeader(crossOrigin.exposeHeaders()).ifPresent(
                h -> headers.add(ACCESS_CONTROL_EXPOSE_HEADERS, h));
        headers.set(responseAdapter::header, "expose-headers was set in CORS config");
    }

    /**
     * Processes a pre-flight request, returning either a preflight response or an error response if the CORS information was
     * invalid.
     * <p>
     * Having determined that we have a pre-flight request, we will always return either a forbidden or a successful response.
     * </p>
     *
     * @param crossOrigin the CORS settings to apply to this request
     * @param requestAdapter the request adapter
     * @param responseAdapter the response adapter
     * @param <T> type for the request wrapped by the requestAdapter
     * @param <U> type for the response wrapper by the responseAdapter
     * @return the response returned by the response adapter with CORS-related headers set (for a successful CORS preflight)
     */
    static <T, U> U processCORSPreFlightRequest(CrossOriginConfig crossOrigin,
            RequestAdapter<T> requestAdapter,
            ResponseAdapter<U> responseAdapter) {

        Optional<String> originOpt = requestAdapter.firstHeader(ORIGIN);
        if (originOpt.isEmpty()) {
            return forbid(requestAdapter, responseAdapter, noRequiredHeader(ORIGIN));
        }

        // If enabled but not whitelisted, deny request
        List<String> allowedOrigins = Arrays.asList(crossOrigin.allowOrigins());
        if (!allowedOrigins.contains("*") && !contains(originOpt, allowedOrigins, String::equals)) {
            return forbid(requestAdapter,
                    responseAdapter,
                    ORIGIN_NOT_IN_ALLOWED_LIST,
                    () -> "actual origin: " + originOpt.get() + ", allowedOrigins: " + allowedOrigins);
        }

        Optional<String> methodOpt = requestAdapter.firstHeader(ACCESS_CONTROL_REQUEST_METHOD);
        if (methodOpt.isEmpty()) {
            return forbid(requestAdapter,
                    responseAdapter,
                    METHOD_NOT_IN_ALLOWED_LIST,
                    () -> "header " + ACCESS_CONTROL_REQUEST_METHOD + " absent from request");
        }

        // Check if method is allowed
        String method = methodOpt.get();
        List<String> allowedMethods = Arrays.asList(crossOrigin.allowMethods());
        if (!allowedMethods.contains("*")
                && !contains(method, allowedMethods, String::equals)) {
            return forbid(requestAdapter,
                    responseAdapter,
                    METHOD_NOT_IN_ALLOWED_LIST,
                    () -> String.format("header %s had value %s but allowedMethods is %s", ACCESS_CONTROL_REQUEST_METHOD,
                            methodOpt.get(), allowedMethods));
        }

        // Check if headers are allowed
        Set<String> requestHeaders = parseHeader(requestAdapter.allHeaders(ACCESS_CONTROL_REQUEST_HEADERS));
        List<String> allowedHeaders = Arrays.asList(crossOrigin.allowHeaders());
        if (!allowedHeaders.contains("*") && !contains(requestHeaders, allowedHeaders)) {
            return forbid(requestAdapter,
                    responseAdapter,
                    HEADERS_NOT_IN_ALLOWED_LIST,
                    () -> String.format("requested headers %s incompatible with allowed headers %s", requestHeaders,
                            allowedHeaders));
        }

        // Build successful response

        Headers headers = new Headers()
                .add(ACCESS_CONTROL_ALLOW_ORIGIN, originOpt.get());
        if (crossOrigin.allowCredentials()) {
            headers.add(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true", "allowCredentials config was set");
        }
        headers.add(ACCESS_CONTROL_ALLOW_METHODS, method);
        formatHeader(requestHeaders.toArray()).ifPresent(
                h -> headers.add(ACCESS_CONTROL_ALLOW_HEADERS, h));
        long maxAge = crossOrigin.maxAge();
        if (maxAge > 0) {
            headers.add(ACCESS_CONTROL_MAX_AGE, maxAge, "maxAge > 0");
        }
        headers.set(responseAdapter::header, "headers set on preflight request");
        return responseAdapter.ok();
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
     * Checks containment in a {@code Collection<String>}.
     *
     * @param item Optional string, typically an Optional header value.
     * @param collection The collection.
     * @param eq Equality function.
     * @return Outcome of test.
     */
    static boolean contains(Optional<String> item, Collection<String> collection, BiFunction<String, String, Boolean> eq) {
        return item.isPresent() && contains(item.get(), collection, eq);
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

    private static <T, U> U forbid(RequestAdapter<T> requestAdapter, ResponseAdapter<U> responseAdapter,
            String reason) {
        return forbid(requestAdapter, responseAdapter, reason, null);
    }

    private static <T, U> U forbid(RequestAdapter<T> requestAdapter, ResponseAdapter<U> responseAdapter, String publicReason,
            Supplier<String> privateExplanation) {
        LOGGER.log(DECISION_LEVEL, String.format("CORS denying request %s: %s", requestAdapter,
                publicReason + (privateExplanation == null ? "" : "; " + privateExplanation.get())));
        return responseAdapter.forbidden(publicReason);
    }

}
