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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

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
        if (!allowedOrigins.contains(origin) && !allowedOrigins.contains("*")) {
            return forbidden("CORS origin not in allowed list");
        }

        String method = headers.getFirst(ACCESS_CONTROL_REQUEST_METHOD);
        List<String> allowedMethods = Arrays.asList(crossOrigin.get().allowMethods());
        if (!allowedMethods.contains(method) && !allowedMethods.contains("*")) {
            return forbidden("CORS method not in allowed list");
        }

        // TODO

        return Response.ok().build();
    }

    static Response forbidden(String message) {
        return Response.status(Response.Status.FORBIDDEN).entity(message).build();
    }

    /**
     * Looks up a {@code CrossOrigin} annotation in method first and then class.
     *
     * @param resourceInfo Info about the matched resource.
     * @return Outcome of lookup.
     */
    static Optional<CrossOrigin> lookupAnnotation(ResourceInfo resourceInfo) {
        Method method = resourceInfo.getResourceMethod();
        if (method == null) {
            return Optional.empty();
        }
        CrossOrigin annotation = method.getAnnotation(CrossOrigin.class);
        if (annotation == null) {
            Class<?> beanClass = resourceInfo.getResourceClass();
            annotation = beanClass.getAnnotation(CrossOrigin.class);
            if (annotation == null) {
                annotation = method.getDeclaringClass().getAnnotation(CrossOrigin.class);
            }
        }
        return Optional.ofNullable(annotation);
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
        StringTokenizer tokenizer = new StringTokenizer(header, " ,");
        while (tokenizer.hasMoreTokens()) {
            result.add(tokenizer.nextToken().trim());
        }
        return result;
    }
}
