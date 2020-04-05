/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Priority;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import io.helidon.config.Config;
import io.helidon.cors.CrossOrigin;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.cors.CrossOriginHelper;
import io.helidon.cors.CrossOriginHelper.RequestAdapter;
import io.helidon.cors.CrossOriginHelper.ResponseAdapter;

import org.eclipse.microprofile.config.ConfigProvider;

import static io.helidon.cors.CrossOriginConfig.CrossOriginConfigMapper;
import static io.helidon.cors.CrossOriginHelper.RequestType.CORS;
import static io.helidon.cors.CrossOriginHelper.RequestType.NORMAL;
import static io.helidon.cors.CrossOriginHelper.RequestType.PREFLIGHT;
import static io.helidon.cors.CrossOriginHelper.prepareResponse;
import static io.helidon.cors.CrossOriginHelper.processPreFlight;
import static io.helidon.cors.CrossOriginHelper.processRequest;
import static io.helidon.cors.CrossOriginHelper.requestType;

/**
 * Class CrossOriginFilter.
 */
@Priority(Priorities.HEADER_DECORATOR)
class CrossOriginFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Context
    private ResourceInfo resourceInfo;

    private List<CrossOriginConfig> crossOriginConfigs;

    CrossOriginFilter() {
        Config config = (Config) ConfigProvider.getConfig();
        crossOriginConfigs = config.get("cors").as(new CrossOriginConfigMapper()).get();
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        RequestAdapter requestAdapter = new MPRequestAdapter(requestContext);
        CrossOriginHelper.RequestType type = requestType(requestAdapter);
        if (type != NORMAL) {
            if (type == PREFLIGHT) {
                Response response = processPreFlight(
                        crossOriginConfigs,
                        crossOriginFromAnnotationFinder(resourceInfo),
                        requestAdapter,
                        new MPResponseAdapter());

                requestContext.abortWith(response);
            } else if (type == CORS) {
                Optional<Response> response = processRequest(
                        crossOriginConfigs,
                        crossOriginFromAnnotationFinder(resourceInfo),
                        requestAdapter,
                        new MPResponseAdapter());
                response.ifPresent(requestContext::abortWith);
            } else {
                throw new IllegalStateException("Invalid value for enum RequestType");
            }
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        RequestAdapter requestAdapter = new MPRequestAdapter(requestContext);
        CrossOriginHelper.RequestType type = requestType(requestAdapter);
        if (type == CORS) {
            prepareResponse(crossOriginConfigs,
                    crossOriginFromAnnotationFinder(resourceInfo),
                    requestAdapter,
                    new MPResponseAdapter(responseContext));
        }
    }

    static class MPRequestAdapter implements RequestAdapter<ContainerRequestContext> {

        private final ContainerRequestContext requestContext;

        MPRequestAdapter(ContainerRequestContext requestContext) {
            this.requestContext = requestContext;
        }

        @Override
        public String path() {
            return requestContext.getUriInfo().getPath();
        }

        @Override
        public Optional<String> firstHeader(String s) {
            return Optional.ofNullable(requestContext.getHeaders().getFirst(s));
        }

        @Override
        public boolean headerContainsKey(String s) {
            return requestContext.getHeaders().containsKey(s);
        }

        @Override
        public List<String> allHeaders(String s) {
            return requestContext.getHeaders().get(s);
        }

        @Override
        public String method() {
            return requestContext.getMethod();
        }

        @Override
        public ContainerRequestContext request() {
            return requestContext;
        }
    }

    static class MPResponseAdapter implements ResponseAdapter<Response> {

        private final MultivaluedMap<String, Object> headers;

        MPResponseAdapter(ContainerResponseContext responseContext) {
            headers = responseContext.getHeaders();
        }

        MPResponseAdapter() {
            headers = new MultivaluedHashMap<>();
        }

        @Override
        public ResponseAdapter<Response> addHeader(String key, String value) {
            headers.add(key, value);
            return this;
        }

        @Override
        public ResponseAdapter<Response> addHeader(String key, Object value) {
            headers.add(key, value);
            return this;
        }

        @Override
        public Response forbidden(String message) {
            return Response.status(Response.Status.FORBIDDEN).entity(message).build();
        }

        @Override
        public Response response() {
            Response.ResponseBuilder builder = Response.ok();
            builder.replaceAll(headers);
            return builder.build();
        }

    }

    static Supplier<Optional<CrossOrigin>> crossOriginFromAnnotationFinder(ResourceInfo resourceInfo) {

        return () -> {
            // If not found, inspect resource matched
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
                                    return pathAnnot2 != null && pathAnnot.value()
                                            .equals(pathAnnot2.value());
                                }
                                return true;
                            }
                            return false;
                        })
                        .findFirst();
                corsAnnot = optionsMethod.map(m -> m.getAnnotation(CrossOrigin.class))
                        .orElse(null);
            }
            return Optional.ofNullable(corsAnnot);
        };
    }
}
