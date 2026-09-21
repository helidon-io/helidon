/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.http3;

import java.util.List;
import java.util.Optional;

import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriPathSegment;
import io.helidon.common.uri.UriQuery;
import io.helidon.common.uri.UriValidator;
import io.helidon.http.DirectHandler;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RequestException;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3ProtocolException;
import io.helidon.webserver.SniRequestSupport;
import io.helidon.webserver.TransportBindingContext;
import io.helidon.webserver.http.DirectTransportRequest;
import io.helidon.webserver.http.HttpRouting;

final class Http3RoutingHandler implements Http3Handler {
    private final TransportBindingContext listenerContext;
    private final HttpRouting routing;
    private final long maxBufferedEntitySize;
    private final boolean validatePath;

    Http3RoutingHandler(TransportBindingContext listenerContext,
                        long maxBufferedEntitySize,
                        boolean validatePath) {
        this.listenerContext = listenerContext;
        this.routing = listenerContext.router().routing(HttpRouting.class, HttpRouting.empty());
        this.maxBufferedEntitySize = maxBufferedEntitySize;
        this.validatePath = validatePath;
    }

    @Override
    public Optional<BufferedResponse> handle(Http3ServerStream stream) {
        Http3Protocol.DecodedRequestHead request = stream.request();
        Http3ServerRequest serverRequest;
        try {
            Http3ConnectionContext ctx = stream.context();
            ServerRequestHeaders requestHeaders = Http3Headers.requestHeaders(request.authority(),
                                                                             request.headers(),
                                                                             ctx.tlsCommonName());
            Method method = Method.create(request.method());
            HttpPrologue prologue = createPrologue(request, method);
            if (Method.CONNECT.equals(method)) {
                throw RequestException.builder()
                        .type(DirectHandler.EventType.OTHER)
                        .status(Status.NOT_IMPLEMENTED_501)
                        .request(DirectTransportRequest.create(prologue, requestHeaders))
                        .message("CONNECT is not supported over HTTP/3")
                        .safeMessage(true)
                        .build();
            }
            ctx.sniContext().ifPresent(sniContext ->
                    SniRequestSupport.validateAuthority(sniContext,
                                                        prologue,
                                                        requestHeaders,
                                                        request.parsedAuthority()));
            long contentLength = stream.contentLength().orElse(-1);
            if (contentLength >= 0 && maxPayloadExceeded(contentLength)) {
                throw RequestException.builder()
                        .type(DirectHandler.EventType.PAYLOAD_TOO_LARGE)
                        .status(Status.REQUEST_ENTITY_TOO_LARGE_413)
                        .request(DirectTransportRequest.create(prologue, requestHeaders))
                        .message("Request Entity Too Large")
                        .build();
            }

            ContentDecoder decoder = requestDecoder(requestHeaders);
            if (decoder == null) {
                throw RequestException.builder()
                        .type(DirectHandler.EventType.OTHER)
                        .status(Status.UNSUPPORTED_MEDIA_TYPE_415)
                        .request(DirectTransportRequest.create(prologue, requestHeaders))
                        .message("Unsupported content encoding")
                        .build();
            }

            long maxPayloadSize = listenerContext.listenerContext().config().maxPayloadSize();
            serverRequest = Http3ServerRequest.create(ctx,
                                                      routing.security(),
                                                      prologue,
                                                      requestHeaders,
                                                      request.authority(),
                                                      decoder,
                                                      new Http3ServerRequest.RequestMeta(
                                                              stream.requestId(),
                                                              stream.hasRequestBody(),
                                                              stream.requestBodyReader(maxPayloadSize),
                                                              stream::closeReader,
                                                              stream::cancelRequestInput,
                                                              stream.requestLimitOutcome(),
                                                              new Http3ServerRequest.EntityLimits(maxPayloadSize,
                                                                                                  maxBufferedEntitySize)));
            return route(ctx, serverRequest, stream);
        } catch (IllegalArgumentException e) {
            return Optional.of(BufferedResponse.text(Status.BAD_REQUEST_400.code(), "Bad Request"));
        }
    }

    private static void consumeRequest(Http3ServerRequest serverRequest, Http3ServerStream stream) {
        try {
            serverRequest.content().consume();
        } catch (RuntimeException e) {
            if (stream.receiveErrorCode() != Http3ErrorCode.REQUEST_CANCELLED.code()) {
                throw e;
            }
        }
    }

    private HttpPrologue createPrologue(Http3Protocol.DecodedRequestHead request, Method method) {
        try {
            if (Method.CONNECT.equals(method)) {
                return HttpPrologue.create("HTTP/3",
                                           "HTTP",
                                           "3",
                                           method,
                                           new AuthorityFormPath(request.authority()),
                                           UriQuery.empty(),
                                           UriFragment.empty());
            }

            String requestTarget = request.path()
                    .orElseThrow(() -> new IllegalArgumentException("HTTP/3 request is missing :path"));
            HttpPrologue prologue;
            if (validateRequestTarget(method, requestTarget)) {
                prologue = HttpPrologue.create("HTTP/3",
                                               "HTTP",
                                               "3",
                                               method,
                                               UriPath.createRelative(UriPath.root(), requestTarget),
                                               UriQuery.empty(),
                                               UriFragment.empty());
            } else {
                prologue = HttpPrologue.create("HTTP/3",
                                               "HTTP",
                                               "3",
                                               method,
                                               requestTarget,
                                               validatePath);
            }
            if (validatePath && prologue.hasQuery()) {
                UriValidator.validateQuery(prologue.query().rawValue());
            }
            return prologue;
        } catch (IllegalArgumentException e) {
            throw Http3ProtocolException.streamError(Http3ErrorCode.MESSAGE_ERROR,
                                                     "Invalid HTTP/3 request-target",
                                                     e);
        }
    }

    private boolean validateRequestTarget(Method method, String requestTarget) {
        boolean specialRequestTarget = Method.OPTIONS.equals(method) && "*".equals(requestTarget);
        if (!validatePath) {
            return specialRequestTarget;
        }
        if (specialRequestTarget) {
            return true;
        }
        if (!requestTarget.isEmpty()
                && requestTarget.charAt(0) == '/'
                && requestTarget.indexOf('#') == -1) {
            return false;
        }
        throw new IllegalArgumentException("Invalid HTTP/3 request-target form");
    }

    private Optional<BufferedResponse> route(Http3ConnectionContext ctx,
                                             Http3ServerRequest serverRequest,
                                             Http3ServerStream stream) {
        Http3ServerResponse response = stream.response(serverRequest);
        serverRequest.continueHandler(response::write100Continue);
        boolean routeCompleted = false;
        try {
            routing.route(ctx, serverRequest, response);
            response.commit();
            if (!response.isSent()) {
                throw new IllegalStateException("HTTP/3 response was not sent");
            }
            routeCompleted = true;
            return Optional.empty();
        } finally {
            if (routeCompleted) {
                if (serverRequest.expectsContinue() && !serverRequest.continueSent()) {
                    serverRequest.reset();
                } else {
                    consumeRequest(serverRequest, stream);
                }
            }
        }
    }

    private ContentDecoder requestDecoder(Headers headers) {
        ContentEncodingContext contentEncodingContext = listenerContext.listenerContext().contentEncodingContext();
        if (!contentEncodingContext.contentDecodingEnabled()) {
            return ContentDecoder.NO_OP;
        }
        String contentEncoding = headers.first(HeaderNames.CONTENT_ENCODING).orElse(null);
        if (contentEncoding == null) {
            return ContentDecoder.NO_OP;
        }
        return contentEncodingContext.contentDecodingSupported(contentEncoding)
                ? contentEncodingContext.decoder(contentEncoding)
                : null;
    }

    private boolean maxPayloadExceeded(long entitySize) {
        long maxPayloadSize = listenerContext.listenerContext().config().maxPayloadSize();
        return maxPayloadSize > -1 && entitySize > maxPayloadSize;
    }

    private record AuthorityFormPath(String rawPath) implements UriPath {
        private static final Parameters EMPTY_PARAMETERS = Parameters.empty("uri/authority");

        @Override
        public String rawPathNoParams() {
            return rawPath;
        }

        @Override
        public String path() {
            return "";
        }

        @Override
        public Parameters matrixParameters() {
            return EMPTY_PARAMETERS;
        }

        @Override
        public UriPath absolute() {
            return this;
        }

        @Override
        public List<UriPathSegment> segments() {
            return List.of();
        }

        @Override
        public void validate() {
        }

        @Override
        public String toString() {
            return rawPath;
        }
    }
}
