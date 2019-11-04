/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.cors;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;

import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import static io.helidon.microprofile.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.helidon.microprofile.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.microprofile.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.microprofile.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.microprofile.cors.CrossOrigin.ACCESS_CONTROL_MAX_AGE;
import static io.helidon.microprofile.cors.CrossOrigin.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.microprofile.cors.CrossOrigin.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.microprofile.cors.CrossOrigin.ORIGIN;

/**
 * Class CrossOriginHelper.
 */
class CrossOriginHelper {

    enum RequestType {
        NORMAL,
        CORS,
        PREFLIGHT
    }

    private CrossOriginHelper() {
    }

    /**
     * Determines the type of a request for CORS processing.
     *
     * @param requestContext The request context.
     * @return The type of request.
     */
    static RequestType findRequestType(ContainerRequestContext requestContext) {
        MultivaluedMap<String, String> headers = requestContext.getHeaders();

        // If no origin header or same as host, then just normal
        String origin = headers.getFirst(ORIGIN);
        String host = headers.getFirst(HttpHeaders.HOST);
        if (origin == null || origin.contains("://" + host)) {
            return RequestType.NORMAL;
        }

        // Is this a pre-flight request?
        if (requestContext.getMethod().equalsIgnoreCase("OPTIONS")
                && headers.containsKey(ACCESS_CONTROL_REQUEST_METHOD)) {
            return RequestType.PREFLIGHT;
        }

        // A CORS request that is not a pre-flight one
        return RequestType.CORS;
    }

    /**
     * Process an actual CORS request.
     *
     * @param requestContext The request context.
     * @param resourceInfo Info about the matched resource.
     * @return A response to send back to the client.
     */
    static Optional<Response> processCorsRequest(ContainerRequestContext requestContext, ResourceInfo resourceInfo) {
        MultivaluedMap<String, String> headers = requestContext.getHeaders();
        String origin = headers.getFirst(ORIGIN);
        Optional<CrossOrigin> crossOrigin = lookupAnnotation(resourceInfo);

        // Annotation must be present for actual request
        if (!crossOrigin.isPresent()) {
            return Optional.of(forbidden("CORS origin is denied"));
        }

        // If enabled but not whitelisted, deny request
        List<String> allowedOrigins = Arrays.asList(crossOrigin.get().value());
        if (!allowedOrigins.contains("*") && !contains(origin, allowedOrigins)) {
            return Optional.of(forbidden("CORS origin not in allowed list"));
        }

        // Succesful processing of request
        return Optional.empty();
    }

    /**
     * Process a CORS response.
     *
     * @param requestContext The request context.
     * @param responseContext The response context.
     * @return A response to send back to the client.
     */
    static void processCorsResponse(ContainerRequestContext requestContext, ContainerResponseContext responseContext,
                                    ResourceInfo resourceInfo) {
        Optional<CrossOrigin> crossOrigin = lookupAnnotation(resourceInfo);
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();

        // Add Origin header
        String origin = requestContext.getHeaders().getFirst(ORIGIN);
        headers.add(ACCESS_CONTROL_ALLOW_ORIGIN, origin);

        // Add Access-Control-Allow-Credentials
        if (crossOrigin.get().allowCredentials()) {
            headers.add(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }

        // Add Access-Control-Allow-Headers if non-empty
        formatHeader(crossOrigin.get().exposeHeaders()).ifPresent(
                h -> headers.add(ACCESS_CONTROL_ALLOW_HEADERS, h));
    }

    /**
     * Process a pre-flight request.
     *
     * @param requestContext The request context.
     * @param resourceInfo Info about the matched resource.
     * @return A response to send back to the client.
     */
    static Response processPreFlight(ContainerRequestContext requestContext, ResourceInfo resourceInfo) {
        MultivaluedMap<String, String> headers = requestContext.getHeaders();

        String origin = headers.getFirst(ORIGIN);
        Optional<CrossOrigin> crossOrigin = lookupAnnotation(resourceInfo);

        // If CORS not enabled, deny request
        if (!crossOrigin.isPresent()) {
            return forbidden("CORS origin is denied");
        }

        // If enabled but not whitelisted, deny request
        List<String> allowedOrigins = Arrays.asList(crossOrigin.get().value());
        if (!allowedOrigins.contains("*") && !contains(origin, allowedOrigins)) {
            return forbidden("CORS origin not in allowed list");
        }

        // Check if method is allowed
        String method = headers.getFirst(ACCESS_CONTROL_REQUEST_METHOD);
        List<String> allowedMethods = Arrays.asList(crossOrigin.get().allowMethods());
        if (!allowedMethods.contains("*") && !contains(method, allowedMethods)) {
            return forbidden("CORS method not in allowed list");
        }

        // Check if headers are allowed
        Set<String> requestHeaders = parseHeader(headers.get(ACCESS_CONTROL_REQUEST_HEADERS));
        List<String> allowedHeaders = Arrays.asList(crossOrigin.get().allowHeaders());
        if (!allowedHeaders.contains("*") && !contains(requestHeaders, allowedHeaders)) {
            return forbidden("CORS headers not in allowed list");
        }

        // Build successful response
        Response.ResponseBuilder builder = Response.ok();
        builder.header(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        if (crossOrigin.get().allowCredentials()) {
            builder.header(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
        builder.header(ACCESS_CONTROL_ALLOW_METHODS, method);
        formatHeader(requestHeaders.toArray()).ifPresent(
                h -> builder.header(ACCESS_CONTROL_ALLOW_HEADERS, h));
        long maxAge = crossOrigin.get().maxAge();
        if (maxAge > 0) {
            builder.header(ACCESS_CONTROL_MAX_AGE, maxAge);
        }
        return builder.build();
    }

    /**
     * Checks containment in a {@code Collection<String>} case insensitively.
     *
     * @param item The string.
     * @param collection The collection.
     * @return Outcome of test.
     */
    static boolean contains(String item, Collection<String> collection) {
        for (String s : collection) {
            if (s.equalsIgnoreCase(item)) {
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
            if (!contains(s, right)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns response with forbidden status and entity created from message.
     *
     * @param message Message in entity.
     * @return A {@code Response} instance.
     */
    static Response forbidden(String message) {
        return Response.status(Response.Status.FORBIDDEN).entity(message).build();
    }

    /**
     * Looks up a {@code CrossOrigin} annotation on the resource method matched first
     * and if not present on a method annotated by {@code OPTIONS} on the same resource.
     *
     * @param resourceInfo Info about the matched resource.
     * @return Outcome of lookup.
     */
    static Optional<CrossOrigin> lookupAnnotation(ResourceInfo resourceInfo) {
        Method resourceMethod = resourceInfo.getResourceMethod();
        Class<?> resourceClass = resourceInfo.getResourceClass();

        CrossOrigin corsAnnot;
        OPTIONS optsAnnot = resourceMethod.getAnnotation(OPTIONS.class);
        if (optsAnnot != null) {
            corsAnnot = resourceMethod.getAnnotation(CrossOrigin.class);
        } else {
            Path pathAnnot = resourceMethod.getAnnotation(Path.class);
            Optional<Method> optionsMethod = Arrays.stream(resourceClass.getDeclaredMethods())
                    .filter(m -> {
                        OPTIONS optsAnnot2 = m.getAnnotation(OPTIONS.class);
                        if (optsAnnot2 != null) {
                            if (pathAnnot != null) {
                                Path pathAnnot2 = m.getAnnotation(Path.class);
                                return pathAnnot2 != null && pathAnnot.value().equals(pathAnnot2.value());
                            }
                            return true;
                        }
                        return false;
                    }).findFirst();
            corsAnnot = optionsMethod.map(m -> m.getAnnotation(CrossOrigin.class)).orElse(null);
        }
        return Optional.ofNullable(corsAnnot);
    }

    /**
     * Formats an array as a comma-separate list without brackets.
     *
     * @param array The array.
     * @param <T> Type of elements in array.
     * @return Formatted array as an {@code Optional}.
     */
    static <T> Optional<String> formatHeader(T[] array) {
        if (array.length == 0) {
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
}
