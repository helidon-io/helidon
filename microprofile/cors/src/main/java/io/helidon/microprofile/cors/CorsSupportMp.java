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
package io.helidon.microprofile.cors;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import io.helidon.webserver.cors.CorsSupportBase;
import io.helidon.webserver.cors.CrossOriginConfig;

/**
 * MP implementation of {@link CorsSupportBase}.
 */
class CorsSupportMp extends CorsSupportBase<ContainerRequestContext, Response, CorsSupportMp, CorsSupportMp.Builder> {

    /**
     *
     * @return a new builder of CorsSupportMp
     */
    static Builder builder() {
        return new Builder();
    }

    private CorsSupportMp(Builder builder) {
        super(builder);
    }

    /**
     * <em>Not for developer use.</em> Submits a request adapter and response adapter for CORS processing.
     *
     * @param requestAdapter wrapper around the request
     * @param responseAdapter wrapper around the response
     * @return Optional of {@code Response}; present if the response should be returned, empty if request processing should
     * continue
     */
    @Override
    protected Optional<Response> processRequest(RequestAdapter<ContainerRequestContext> requestAdapter,
            ResponseAdapter<Response> responseAdapter) {
        return super.processRequest(requestAdapter, responseAdapter);
    }

    /**
     * <em>Not for developer user.</em> Gets a response ready to participate in the CORS protocol.
     *
     * @param requestAdapter wrapper around the request
     * @param responseAdapter wrapper around the response
     */
    @Override
    protected void prepareResponse(RequestAdapter<ContainerRequestContext> requestAdapter,
            ResponseAdapter<Response> responseAdapter) {
        super.prepareResponse(requestAdapter, responseAdapter);
    }

    @Override
    public String toString() {
        return String.format("CorsSupportMp[%s]{%s}", name(), describe());
    }

    static class Builder extends CorsSupportBase.Builder<ContainerRequestContext, Response, CorsSupportMp, Builder> {

        private static int builderCount = 0; // To help distinguish otherwise-unnamed CorsSupport instances in log messages

        Builder() {
            name("MP " + builderCount++); // Initial name. Overridable by a subsequent setting.
        }
        @Override
        public CorsSupportMp build() {
            return new CorsSupportMp(this);
        }

        @Override
        protected Builder me() {
            return this;
        }

        @Override
        protected Builder secondaryLookupSupplier(
                Supplier<Optional<CrossOriginConfig>> secondaryLookupSupplier) {
            super.secondaryLookupSupplier(secondaryLookupSupplier);
            return this;
        }
    }

    static class RequestAdapterMp implements RequestAdapter<ContainerRequestContext> {

        private final ContainerRequestContext requestContext;

        RequestAdapterMp(ContainerRequestContext requestContext) {
            this.requestContext = requestContext;
        }

        @Override
        public String path() {
            String path = requestContext.getUriInfo().getPath();
            return path.startsWith("/") ? path : '/' + path;
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

        @Override
        public void next() {
        }

        @Override
        public String toString() {
            return String.format("RequestAdapterMp{path=%s, method=%s, headers=%s}", path(), method(),
                    requestContext.getHeaders());
        }
    }

    static class ResponseAdapterMp implements ResponseAdapter<Response> {

        private final int status;
        private final MultivaluedMap<String, Object> headers;

        ResponseAdapterMp(ContainerResponseContext responseContext) {
            headers = responseContext.getHeaders();
            status = responseContext.getStatus();
        }

        ResponseAdapterMp() {
            headers = new MultivaluedHashMap<>();
            status = Response.Status.OK.getStatusCode();
        }

        @Override
        public ResponseAdapter<Response> header(String key, String value) {
            headers.add(key, value);
            return this;
        }

        @Override
        public ResponseAdapter<Response> header(String key, Object value) {
            headers.add(key, value);
            return this;
        }

        @Override
        public Response forbidden(String message) {
            return Response.status(Response.Status.FORBIDDEN).entity(message).build();
        }

        @Override
        public Response ok() {
            Response.ResponseBuilder builder = Response.ok();
            /*
             * The Helidon CORS support code invokes ok() only for creating a CORS preflight response. In these cases no user
             * code will have a chance to set headers in the response. That means we can use replaceAll here because the only
             * headers needed in the response are the ones set using this adapter.
             */
            builder.replaceAll(headers);
            return builder.build();
        }

        @Override
        public int status() {
            return status;
        }
    }
}
