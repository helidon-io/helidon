/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.UnixDomainSocketAddress;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.LogFormatter;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.InstanceWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.webclient.api.ClientRequestBase;
import io.helidon.webclient.api.ClientRequestOrigin;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.EntityWriterPreflight;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.ProxyRoute;
import io.helidon.webclient.api.RedirectSecurityState;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

class Http1ClientRequestImpl extends ClientRequestBase<Http1ClientRequest, Http1ClientResponse>
        implements Http1ClientRequest {
    private static final System.Logger LOGGER = System.getLogger(Http1ClientRequestImpl.class.getName());
    private static final HeaderName AUTHORITY = HeaderNames.create(":authority");

    private final Http1ClientImpl http1Client;
    private final FullClientRequest<?> delegate;

    private boolean outputStreamRedirect;
    private CompletableFuture<WebClientServiceRequest> redirectedWhenSent;
    private ClientRequestHeaders redirectHeadersAfterServices;
    private RequestEntity requestEntity;

    Http1ClientRequestImpl(Http1ClientImpl http1Client,
                           Method method,
                           ClientUri clientUri,
                           Map<String, String> properties) {
        this(http1Client,
             null,
             method,
             clientUri,
             null,
             properties,
             null,
             RedirectSecurityState.initial());
    }

    Http1ClientRequestImpl(Http1ClientImpl http1Client,
                           FullClientRequest<?> delegate,
                           Method method,
                           ClientUri clientUri,
                           Boolean sendExpectContinue,
                           Map<String, String> properties) {
        this(http1Client,
             delegate,
             method,
             clientUri,
             sendExpectContinue,
             properties,
             null,
             delegate == null ? RedirectSecurityState.initial() : delegate.redirectSecurityState());
    }

    private Http1ClientRequestImpl(Http1ClientImpl http1Client,
                                   FullClientRequest<?> delegate,
                                   Method method,
                                   ClientUri clientUri,
                                   Boolean sendExpectContinue,
                                   Map<String, String> properties,
                                   ClientUri redirectSourceUri,
                                   RedirectSecurityState redirectSecurityState) {
        super(http1Client.clientConfig(),
              http1Client.webClient().cookieManager(),
              Http1Client.PROTOCOL_ID,
              method,
              clientUri,
              sendExpectContinue,
              properties,
              redirectSourceUri);
        this.http1Client = http1Client;
        this.delegate = delegate;
        super.redirectSecurityState(redirectSecurityState);
    }

    //Copy constructor for redirection purposes
    Http1ClientRequestImpl(Http1ClientRequestImpl request,
                           Method method,
                           ClientUri clientUri,
                           Map<String, String> properties,
                           ClientUri redirectSourceUri,
                           boolean preserveEntity,
                           boolean replayingEntity) {
        this(request.http1Client,
             null,
             method,
             clientUri,
             null,
             properties,
             redirectSourceUri,
             request.redirectSecurityState().forRedirect(replayingEntity));

        followRedirects(request.followRedirects());
        maxRedirects(request.maxRedirects());
        readTimeout(request.readTimeout());
        readContinueTimeout(request.readContinueTimeout());
        request.sendExpectContinue().ifPresent(this::sendExpectContinue);
        keepAlive(request.keepAlive());
        proxy(request.proxy());
        tls(request.tls());
        tlsGeneration(request.tlsGeneration());
        request.sni().ifPresent(this::sni);
        headers().clear();
        headers(request.redirectSourceHeaders());
        if (request.requestEntity != null) {
            requestEntity = request.requestEntity.redirect(headers(), preserveEntity);
        }
        if (!preserveEntity) {
            headers().remove(HeaderNames.CONTENT_TYPE);
            headers().remove(HeaderNames.CONTENT_ENCODING);
            headers().remove(HeaderNames.CONTENT_LANGUAGE);
            headers().remove(HeaderNames.CONTENT_LOCATION);
        }
        headers().remove(HeaderNames.CONTENT_LENGTH);
        headers().remove(HeaderNames.TRANSFER_ENCODING);
        headers().remove(HeaderNames.EXPECT);
        if (requestEntity != null) {
            requestEntity.applyContentLength(headers());
        }
        boolean retainRouting = canRetainRouting(request.resolvedUri(),
                                                 request.headers(),
                                                 redirectSourceUri,
                                                 clientUri,
                                                 request.redirectSecurityState());
        if (retainRouting) {
            ClientRequestOrigin targetOrigin = ClientRequestOrigin.create(clientUri, headers());
            request.address().ifPresent(value -> {
                var inheritedOrigin = request.inheritedAddressOrigin();
                if (inheritedOrigin.isEmpty()) {
                    address(value);
                } else if (inheritedOrigin.get().equals(targetOrigin)) {
                    inheritedAddress(value, inheritedOrigin.get());
                }
            });
            request.selectedProxyRoute().ifPresent(value -> {
                var inheritedOrigin = request.inheritedSelectedProxyRouteOrigin();
                if (inheritedOrigin.isEmpty()) {
                    selectedProxyRoute(value);
                } else if (inheritedOrigin.get().equals(targetOrigin)) {
                    inheritedSelectedProxyRoute(value, inheritedOrigin.get());
                }
            });
        } else {
            headers().remove(HeaderNames.HOST);
            headers().remove(AUTHORITY);
        }
        this.redirectedWhenSent = request.redirectedWhenSent;
    }

    @Override
    public void selectedProxyRoute(ProxyRoute proxyRoute) {
        super.selectedProxyRoute(proxyRoute);
        if (delegate != null) {
            delegate.selectedProxyRoute(proxyRoute);
        }
    }

    @Override
    public void clearSelectedProxyRoute() {
        super.clearSelectedProxyRoute();
        if (delegate != null) {
            delegate.clearSelectedProxyRoute();
        }
    }

    @Override
    public Http1ClientResponse doSubmit(Object entity) {
        if (entity == BufferData.EMPTY_BYTES) {
            return invokePreparedEntity(BufferData.EMPTY_BYTES);
        }
        if (entity instanceof byte[] buffer) {
            return invokePreparedEntity(buffer);
        }

        RequestEntity preparedEntity = RequestEntity.create(this, entity);
        requestEntity = preparedEntity;
        try {
            if (preparedEntity.bytes != null) {
                return invokePreparedEntity(preparedEntity.bytes);
            }
            return doOutputStream(preparedEntity::writeTo);
        } finally {
            preparedEntity.cancelIfUnattached();
            if (requestEntity == preparedEntity) {
                requestEntity = null;
            }
        }
    }

    @Override
    public Http1ClientResponse doOutputStream(OutputStreamHandler streamHandler) {
        return doOutputStream(streamHandler, new AtomicBoolean(), 0, false);
    }

    @Override
    public Http1ClientResponse outputStream(OutputStreamHandler streamHandler, int followedRedirects) {
        if (followedRedirects < 0) {
            throw new IllegalArgumentException("Followed redirect count must not be negative: " + followedRedirects);
        }
        return doOutputStream(streamHandler, new AtomicBoolean(), followedRedirects, false);
    }

    private Http1ClientResponseImpl doOutputStream(OutputStreamHandler streamHandler,
                                                   AtomicBoolean handlerClaimed,
                                                   int followedRedirects,
                                                   boolean replayable) {
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        OutputStreamHandler claimedHandler = replayable
                ? streamHandler
                : outputStream -> {
                    if (!handlerClaimed.compareAndSet(false, true)) {
                        throw new IllegalStateException("HTTP/1 request entity is one-shot and has already been consumed");
                    }
                    streamHandler.handle(outputStream);
                };
        Http1CallOutputStreamChain callChain = new Http1CallOutputStreamChain(http1Client,
                                                                               this,
                                                                               whenSent,
                                                                               whenComplete,
                                                                               claimedHandler,
                                                                               followedRedirects);

        Http1ClientResponseImpl response = invokeWithServices(callChain, whenSent, whenComplete);
        if (!followRedirects() || !RedirectionProcessor.redirectionStatusCode(response.status())) {
            return response;
        }

        Status redirectStatus = response.status();
        ClientUri sourceUri = response.lastEndpointUri();
        String location;
        int totalFollowedRedirects = callChain.followedRedirects();
        try (response) {
            if (responseCookiesDeferred()) {
                recordResponseCookies(responseCookieUri(redirectSecurityState(), response.lastEndpointUri()),
                                      response.headers());
            }
            if (totalFollowedRedirects >= maxRedirects()) {
                throw RedirectionProcessor.maxRedirectsReached(maxRedirects());
            }
            if (!response.headers().contains(HeaderNames.LOCATION)) {
                throw new IllegalStateException("There is no " + HeaderNames.LOCATION
                                                        + " header present in the response! "
                                                        + "It is not clear where to redirect.");
            }
            location = response.headers().get(HeaderNames.LOCATION).get();
        }

        ClientUri redirectUri = resolveRedirectUri(sourceUri, location);
        boolean keepsEntity = RedirectionProcessor.keepsMethodAndEntity(method(), redirectStatus);
        boolean requestEntitySent = callChain.requestEntitySent();
        if (keepsEntity && handlerClaimed.get() && requestEntitySent) {
            throw new IllegalStateException("Cannot replay a one-shot request body after redirect status "
                                                    + redirectStatus.code() + ".");
        }
        Http1ClientRequestImpl redirectRequest = new Http1ClientRequestImpl(this,
                                                                            keepsEntity ? method() : Method.GET,
                                                                            redirectUri,
                                                                            properties(),
                                                                            sourceUri,
                                                                            keepsEntity,
                                                                            keepsEntity
                                                                                    && (!handlerClaimed.get()
                                                                                            || requestEntitySent));
        if (responseCookiesDeferred()) {
            redirectRequest.deferResponseCookies();
        }
        if (callChain.rawServiceResponse() == null
                && canRetainRouting(resolvedUri(), headers(), sourceUri, redirectUri, redirectSecurityState())) {
            ClientRequestOrigin targetOrigin = ClientRequestOrigin.create(redirectUri, redirectRequest.headers());
            connection().ifPresent(value -> {
                var inheritedOrigin = inheritedConnectionOrigin();
                if (inheritedOrigin.isEmpty()) {
                    redirectRequest.connection(value);
                } else if (inheritedOrigin.get().equals(targetOrigin)) {
                    redirectRequest.inheritedConnection(value, inheritedOrigin.get());
                }
            });
        }
        int nextRedirect = totalFollowedRedirects + 1;
        Http1ClientResponseImpl redirectedResponse;
        if (keepsEntity) {
            if (replayable) {
                redirectedResponse = redirectRequest.doOutputStream(streamHandler,
                                                                     handlerClaimed,
                                                                     nextRedirect,
                                                                     true);
            } else if (handlerClaimed.get()) {
                redirectedResponse = followRedirects(redirectRequest,
                                                     nextRedirect,
                                                     BufferData.EMPTY_BYTES);
            } else {
                redirectedResponse = redirectRequest.doOutputStream(streamHandler,
                                                                     handlerClaimed,
                                                                     nextRedirect,
                                                                     false);
            }
        } else {
            redirectedResponse = followRedirects(redirectRequest,
                                                 nextRedirect,
                                                 BufferData.EMPTY_BYTES);
        }
        redirectSecurityState(redirectRequest.redirectSecurityState());
        return redirectedResponse;
    }

    private static Http1ClientResponseImpl followRedirects(Http1ClientRequestImpl request,
                                                           int followedRedirects,
                                                           byte[] entity) {
        return request.responseCookiesDeferred()
                ? RedirectionProcessor.invokeWithFollowRedirectsDeferringResponseCookies(request,
                                                                                         followedRedirects,
                                                                                         entity)
                : RedirectionProcessor.invokeWithFollowRedirects(request, followedRedirects, entity);
    }

    Http1ClientResponseImpl replayBufferedEntity(byte[] entity, int followedRedirects) {
        return doOutputStream(output -> {
            output.write(entity);
            output.close();
        }, new AtomicBoolean(), followedRedirects, true);
    }

    private static ClientUri responseCookieUri(RedirectSecurityState securityState, ClientUri endpointUri) {
        return securityState.lastEffectiveOrigin()
                .map(origin -> origin.apply(endpointUri))
                .orElseGet(() -> ClientRequestOrigin.create(endpointUri).apply(endpointUri));
    }

    @Override
    public UpgradeResponse upgrade(String protocol) {
        try {
            if (!headers().contains(HeaderNames.UPGRADE)) {
                headers().set(HeaderNames.UPGRADE, protocol);
            }
            Header requestedUpgrade = headers().get(HeaderNames.UPGRADE);
            Http1ClientResponseImpl response;

            if (followRedirects()) {
                response = RedirectionProcessor.invokeWithFollowRedirects(this, BufferData.EMPTY_BYTES);
            } else {
                response = invokeRequestWithEntity(BufferData.EMPTY_BYTES);
            }

            if (response.status() == Status.SWITCHING_PROTOCOLS_101) {
                // is the upgrade request successful?
                if (upgradeSuccessful(requestedUpgrade, response.headers().get(HeaderNames.UPGRADE))) {
                    if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                        response.connection()
                                .helidonSocket().log(LOGGER,
                                                     System.Logger.Level.TRACE,
                                                     "Upgrading to %s",
                                                     LogFormatter.escape(requestedUpgrade.get()));
                    }
                    // upgrade was a success
                    return UpgradeResponse.success(response, response.connection());
                } else {
                    if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                        response.connection().helidonSocket().log(LOGGER,
                                                                  System.Logger.Level.TRACE,
                                                                  "Upgrade failed. Expected upgrade: %s, got headers: %s",
                                                                  LogFormatter.escape(requestedUpgrade.get()),
                                                                  http1Client.logFormatter().format(response.headers()));
                    }
                }
            } else {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    response.connection().helidonSocket().log(LOGGER,
                                                              System.Logger.Level.TRACE,
                                                              "Upgrade failed. Tried upgrading to %s, got status: %s",
                                                              LogFormatter.escape(requestedUpgrade.get()),
                                                              response.status().codeText());
                }
            }

            return UpgradeResponse.failure(response);
        } finally {
            clearSelectedProxyRoute();
        }
    }

    @Override
    protected MediaContext mediaContext() {
        return super.mediaContext();
    }

    Proxy effectiveProxy() {
        return address().filter(UnixDomainSocketAddress.class::isInstance).isPresent() ? Proxy.noProxy() : proxy();
    }

    Http1ClientImpl http1Client() {
        return http1Client;
    }

    boolean ownsExplicitConnection() {
        return delegate != null && delegate.connection().isEmpty() && connection().isPresent();
    }

    /**
     * Check upgrade protocols. Protocol names are case insensitive.
     *
     * @param requestUpgrade request upgrade header
     * @param responseUpgrade response upgrade header
     * @return check if protocol upgrade can proceed
     */
    static boolean upgradeSuccessful(Header requestUpgrade, Header responseUpgrade) {
        String selectedProtocol = responseUpgrade.get();
        for (String protocol : requestUpgrade.allValues()) {
            if (protocol.equalsIgnoreCase(selectedProtocol)) {
                return true;
            }
        }
        return false;
    }

    Http1ClientResponseImpl invokeRequestWithEntity(byte[] entity) {
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        Http1CallChainBase callChain = new Http1CallEntityChain(http1Client,
                                                                this,
                                                                whenSent,
                                                                whenComplete,
                                                                entity);

        return invokeWithServices(callChain, whenSent, whenComplete);
    }

    Http1ClientResponseImpl redirectProbe() {
        return (Http1ClientResponseImpl) requestWithoutRouteCleanup();
    }

    private Http1ClientResponseImpl invokePreparedEntity(byte[] entity) {
        if (followRedirects()) {
            return RedirectionProcessor.invokeWithFollowRedirects(this, entity);
        }
        return invokeRequestWithEntity(entity);
    }

    private Http1ClientResponseImpl invokeWithServices(Http1CallChainBase callChain,
                                                       CompletableFuture<WebClientServiceRequest> whenSent,
                                                       CompletableFuture<WebClientServiceResponse> whenComplete) {

        if (redirectedWhenSent != null) {
            whenSent.whenComplete((sentRequest, failure) -> {
                if (failure == null) {
                    redirectedWhenSent.complete(sentRequest);
                } else {
                    redirectedWhenSent.completeExceptionally(failure);
                }
            });
        }

        // will create a copy, so we could invoke this method multiple times
        ClientUri resolvedUri = resolvedUri();

        WebClientServiceResponse serviceResponse;
        try {
            serviceResponse = invokeServices(http1Client.webClient(),
                                             callChain,
                                             whenSent,
                                             whenComplete,
                                             resolvedUri,
                                             request -> {
                                                 if (requestEntity != null) {
                                                     requestEntity.prepareTerminal(request.headers(),
                                                                                   request.context());
                                                 }
                                                 if (request.method() == Method.QUERY
                                                         && !request.headers().contains(HeaderNames.CONTENT_TYPE)) {
                                                     throw new IllegalArgumentException(
                                                             "Content-Type header is required for method '"
                                                                     + Method.QUERY + "'");
                                                 }
                                             });
            if (followRedirects() && RedirectionProcessor.redirectionStatusCode(serviceResponse.status())) {
                redirectHeadersAfterServices = super.redirectSourceHeaders(serviceResponse.serviceRequest().headers());
            }
        } catch (RuntimeException | Error failure) {
            WebClientServiceResponse rawServiceResponse = callChain.rawServiceResponse();
            if (rawServiceResponse != null) {
                try {
                    rawServiceResponse.connection().closeResource();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (failure != cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            whenSent.completeExceptionally(failure);
            whenComplete.completeExceptionally(failure);
            throw failure;
        }

        CompletableFuture<Void> complete = new CompletableFuture<>();
        complete.whenComplete((ignored, failure) -> {
            if (failure == null) {
                serviceResponse.whenComplete().complete(serviceResponse);
                whenComplete.complete(serviceResponse);
            } else {
                serviceResponse.whenComplete().completeExceptionally(failure);
                whenComplete.completeExceptionally(failure);
            }
        });

        ClientRequestHeaders responseRequestHeaders = finalizedRequestHeaders();
        if (delegate != null) {
            ClientRequestHeaders delegateHeaders = delegate.headers();
            delegateHeaders.remove(HeaderNames.HOST);
            responseRequestHeaders.first(HeaderNames.HOST)
                    .ifPresent(value -> delegateHeaders.set(HeaderValues.create(HeaderNames.HOST, value)));
        }
        Http1ClientResponseImpl response = new Http1ClientResponseImpl(clientConfig(),
                                                                       http1Client().protocolConfig(),
                                                                       serviceResponse.status(),
                                                                       serviceResponse.serviceRequest().method(),
                                                                       responseRequestHeaders,
                                                                       serviceResponse.headers(),
                                                                       callChain.rawServiceResponse() == null
                                                                               ? null
                                                                               : callChain.connection(),
                                                                       serviceResponse.inputStream().orElse(null),
                                                                       mediaContext(),
                                                                       finalizedEndpointUri(),
                                                                       complete,
                                                                       callChain.responseTrailers(),
                                                                       serviceResponse.trailers());
        response.serviceResponse(serviceResponse, callChain.rawServiceResponse());
        if (callChain instanceof Http1CallOutputStreamChain outputStreamChain
                && outputStreamChain.closeConnectionOnResponseClose()) {
            response.closeConnectionOnClose();
        }
        return response;
    }

    /**
     * Whether this request is part of an output stream redirection probe.
     * Default is {@code false}.
     *
     * @param outputStreamRedirect whether this request is part of output stream redirection
     * @return updated request
     */
    Http1ClientRequestImpl outputStreamRedirect(boolean outputStreamRedirect) {
        this.outputStreamRedirect = outputStreamRedirect;
        return this;
    }

    boolean outputStreamRedirect() {
        return outputStreamRedirect;
    }

    void redirectedWhenSent(CompletableFuture<WebClientServiceRequest> whenSent) {
        this.redirectedWhenSent = whenSent;
    }

    ClientRequestHeaders redirectSourceHeaders() {
        return redirectHeadersAfterServices == null
                ? EntityWriterPreflight.copyOf(headers())
                : redirectHeadersAfterServices;
    }

    private static final class RequestEntity {
        private final AtomicBoolean streamHandlerClaimed = new AtomicBoolean();
        private final int preflightCapacity;
        private final EntityWriterPreflight.HeaderChanges earlyChanges;
        private Object entity;
        private GenericType<Object> genericType;
        private EntityWriter<Object> writer;
        private OutputStreamHandler streamHandler;
        private byte[] bytes;
        private long contentLength = -1;
        private EntityWriterPreflight preflight;
        private EntityWriterPreflight.HeaderChanges terminalChanges;
        private EntityWriterPreflight.Application currentApplication;

        private RequestEntity(int preflightCapacity,
                              EntityWriterPreflight.HeaderChanges earlyChanges,
                              EntityWriterPreflight.Application currentApplication) {
            this.preflightCapacity = preflightCapacity;
            this.earlyChanges = earlyChanges;
            this.currentApplication = currentApplication;
        }

        private static RequestEntity create(Http1ClientRequestImpl request, Object entity) {
            EntityWriterPreflight.HeaderRecorder recordingHeaders = EntityWriterPreflight.record(request.headers());
            GenericType<Object> genericType = GenericType.create(entity);
            EntityWriter<Object> writer = request.clientConfig().mediaContext().writer(genericType, recordingHeaders);
            long configuredContentLength = request.headers().contentLength().orElse(-1);
            int maximum = Math.max(1, request.clientConfig().maxInMemoryEntity());
            int desiredBuffer = request.clientConfig().writeBufferSize() <= 1
                    ? 1024
                    : request.clientConfig().writeBufferSize();
            RequestEntity result;
            if (writer.supportsInstanceWriter()) {
                InstanceWriter instanceWriter = writer.instanceWriter(genericType, entity, recordingHeaders);
                result = new RequestEntity(Math.max(1, Math.min(desiredBuffer, maximum)),
                                           recordingHeaders.changes(),
                                           recordingHeaders.application());
                if (instanceWriter.alwaysInMemory()) {
                    result.bytes = instanceWriter.instanceBytes();
                } else {
                    result.contentLength = instanceWriter.contentLength().orElse(configuredContentLength);
                    if (result.contentLength < 0 || result.contentLength > request.clientConfig().maxInMemoryEntity()) {
                        result.streamHandler = instanceWriter::write;
                    } else {
                        result.bytes = instanceWriter.instanceBytes();
                    }
                }
            } else if (configuredContentLength >= 0
                    && configuredContentLength <= request.clientConfig().maxInMemoryEntity()) {
                ByteArrayOutputStream bufferedEntity = new ByteArrayOutputStream((int) configuredContentLength);
                OutputStream outputStream = new OutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                        checkCapacity(1);
                        bufferedEntity.write(value);
                    }

                    @Override
                    public void write(byte[] bytes, int offset, int length) throws IOException {
                        checkCapacity(length);
                        bufferedEntity.write(bytes, offset, length);
                    }

                    private void checkCapacity(int additionalBytes) throws IOException {
                        long updatedSize = (long) bufferedEntity.size() + additionalBytes;
                        if (updatedSize > request.clientConfig().maxInMemoryEntity()) {
                            throw new IOException("Request entity writer exceeded the configured in-memory limit of "
                                                          + request.clientConfig().maxInMemoryEntity() + " bytes");
                        }
                    }
                };
                writer.write(genericType, entity, outputStream, recordingHeaders);
                result = new RequestEntity(Math.max(1, Math.min(desiredBuffer, maximum)),
                                           recordingHeaders.changes(),
                                           recordingHeaders.application());
                result.bytes = bufferedEntity.toByteArray();
            } else {
                result = new RequestEntity(Math.max(1, Math.min(desiredBuffer, maximum)),
                                           recordingHeaders.changes(),
                                           recordingHeaders.application());
                result.contentLength = configuredContentLength;
                result.entity = entity;
                result.genericType = genericType;
                result.writer = writer;
            }
            if (result.bytes != null) {
                result.contentLength = result.bytes.length;
            }
            if (result.streamHandler != null && result.contentLength >= 0) {
                request.headers().contentLength(result.contentLength);
            }
            return result;
        }

        private void prepareTerminal(ClientRequestHeaders headers, Context context) {
            EntityWriterPreflight.Application terminalApplication;
            if (writer != null) {
                Object writerEntity = entity;
                GenericType<Object> writerType = genericType;
                EntityWriter<Object> entityWriter = writer;
                preflight = EntityWriterPreflight.create(preflightCapacity,
                                                         context,
                                                         (outputStream, isolatedHeaders) -> entityWriter.write(
                                                                 writerType,
                                                                 writerEntity,
                                                                 outputStream,
                                                                 isolatedHeaders));
                terminalApplication = preflight.prepare(headers);
                terminalChanges = preflight.headerChanges();
                entity = null;
                genericType = null;
                writer = null;
            } else if (terminalChanges != null && !terminalChanges.isEmpty()) {
                terminalApplication = terminalChanges.apply(headers);
            } else {
                return;
            }
            currentApplication = currentApplication == null
                    ? terminalApplication
                    : currentApplication.andThen(terminalApplication);
        }

        private RequestEntity redirect(ClientRequestHeaders headers, boolean preserveEntity) {
            if (currentApplication != null) {
                currentApplication.rollback(headers);
                currentApplication = null;
            }
            if (!preserveEntity) {
                cancelIfUnattached();
                return null;
            }
            currentApplication = earlyChanges.apply(headers);
            return this;
        }

        private void applyContentLength(ClientRequestHeaders headers) {
            if (bytes == null && contentLength >= 0) {
                headers.contentLength(contentLength);
            }
        }

        private void writeTo(OutputStream outputStream) throws IOException {
            if (preflight != null) {
                preflight.writeTo(outputStream);
                return;
            }
            if (!streamHandlerClaimed.compareAndSet(false, true)) {
                throw new IllegalStateException("HTTP/1 request entity is one-shot and has already been consumed");
            }
            streamHandler.handle(outputStream);
        }

        private void cancelIfUnattached() {
            if (preflight != null) {
                preflight.cancelIfUnattached(new CancellationException(
                        "HTTP/1 request completed before entity writer attachment"));
            }
        }
    }

    private static boolean canRetainRouting(ClientUri configuredSourceUri,
                                            ClientRequestHeaders configuredHeaders,
                                            ClientUri sourceUri,
                                            ClientUri targetUri,
                                            RedirectSecurityState securityState) {
        ClientRequestOrigin sourceUriOrigin = ClientRequestOrigin.create(sourceUri);
        ClientRequestOrigin configuredEffectiveOrigin = ClientRequestOrigin.create(configuredSourceUri,
                                                                                    configuredHeaders);
        return ClientRequestOrigin.create(configuredSourceUri).equals(sourceUriOrigin)
                && securityState.lastUriOrigin().orElse(sourceUriOrigin).equals(sourceUriOrigin)
                && securityState.lastEffectiveOrigin()
                        .orElse(configuredEffectiveOrigin)
                        .equals(configuredEffectiveOrigin)
                && ClientRequestOrigin.create(targetUri).equals(sourceUriOrigin);
    }

}
