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

package io.helidon.webclient.http3;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.uri.UriFragment;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.InstanceWriter;
import io.helidon.webclient.api.ClientRequestBase;
import io.helidon.webclient.api.ClientRequestOrigin;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.EntityWriterPreflight;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.ProxyRoute;
import io.helidon.webclient.api.RedirectSecurityState;
import io.helidon.webclient.api.ReleasableResource;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

class Http3ClientRequestImpl extends ClientRequestBase<Http3ClientRequest, Http3ClientResponse>
        implements Http3ClientRequest, FullClientRequest<Http3ClientRequest> {
    private static final HeaderName AUTHORITY = HeaderNames.create(":authority");
    private static final HeaderName PROXY_CONNECTION = HeaderNames.create("Proxy-Connection");

    private final Http3ClientImpl http3Client;
    private final FullClientRequest<?> delegate;
    private final boolean explicitSelection;
    private boolean priorKnowledge;
    private long tlsGeneration;
    private EntityWriterPreflight.Application preparedEntityHeaders;
    private EntityWriterPreflight.Application terminalEntityHeaders;
    private ClientRequestHeaders redirectHeadersAfterServices;

    Http3ClientRequestImpl(Http3ClientImpl http3Client,
                           FullClientRequest<?> delegate,
                           Method method,
                           ClientUri clientUri,
                           Map<String, String> properties) {
        this(http3Client,
             delegate,
             method,
             clientUri,
             properties,
             delegate == null ? RedirectSecurityState.initial() : delegate.redirectSecurityState(),
             null);
    }

    private Http3ClientRequestImpl(Http3ClientImpl http3Client,
                                   FullClientRequest<?> delegate,
                                   Method method,
                                   ClientUri clientUri,
                                   Map<String, String> properties,
                                   RedirectSecurityState redirectSecurityState,
                                   ClientUri redirectSourceUri) {
        super(http3Client.clientConfig(),
              http3Client.webClient().cookieManager(),
              Http3Client.PROTOCOL_ID,
              method,
              clientUri,
              null,
              properties,
              redirectSourceUri);

        this.http3Client = http3Client;
        this.priorKnowledge = http3Client.protocolConfig().priorKnowledge();
        this.delegate = delegate;
        super.redirectSecurityState(redirectSecurityState);
        this.explicitSelection = delegate != null
                && Http3Client.PROTOCOL_ID.equals(delegate.requestedProtocolId().orElse(null));
    }

    Http3ClientRequestImpl(Http3ClientRequestImpl request,
                           Method method,
                           ClientUri clientUri,
                           Map<String, String> properties,
                           ClientUri redirectSourceUri,
                           boolean redirectEntityReplay,
                           Http3RequestBody previousBody,
                           boolean preserveEntity) {
        this(request.http3Client,
             request.delegate,
             method,
             clientUri,
             properties,
             request.redirectSecurityState().forRedirect(redirectEntityReplay),
             redirectSourceUri);

        followRedirects(request.followRedirects());
        maxRedirects(request.maxRedirects());
        tls(request.tls());
        proxy(request.proxy());
        keepAlive(request.keepAlive());
        readTimeout(request.readTimeout());
        readContinueTimeout(request.readContinueTimeout());
        request.sendExpectContinue().ifPresent(this::sendExpectContinue);
        priorKnowledge(request.priorKnowledge);
        request.sni().ifPresent(this::sni);
        headers().clear();
        headers(request.redirectSourceHeaders());
        if (request.preparedEntityHeaders != null) {
            request.preparedEntityHeaders.rollback(headers());
        }
        if (preserveEntity && !previousBody.earlyHeaderChanges().isEmpty()) {
            preparedEntityHeaders = previousBody.earlyHeaderChanges().apply(headers());
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
        boolean sameOrigin = ClientRequestOrigin.create(redirectSourceUri)
                .equals(ClientRequestOrigin.create(clientUri));
        if (!sameOrigin) {
            headers().remove(HeaderNames.HOST);
            headers().remove(AUTHORITY);
        }
        ClientRequestOrigin targetOrigin = ClientRequestOrigin.create(clientUri, headers());
        if (sameOrigin) {
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
        }
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
    public Http3ClientRequest priorKnowledge(boolean priorKnowledge) {
        this.priorKnowledge = priorKnowledge;
        return this;
    }

    @Override
    public Http3ClientResponse doSubmit(Object entity) {
        Http3RequestBody requestBody;
        if (entity == BufferData.EMPTY_BYTES) {
            requestBody = Http3RequestBody.create(BufferData.EMPTY_BYTES);
        } else if (entity instanceof byte[] bytes) {
            requestBody = Http3RequestBody.create(bytes);
        } else {
            EntityWriterPreflight.HeaderRecorder recordingHeaders = EntityWriterPreflight.record(headers());
            GenericType<Object> genericType = GenericType.create(entity);
            EntityWriter<Object> writer = clientConfig().mediaContext().writer(genericType, recordingHeaders);
            long configuredContentLength = headers().contentLength().orElse(-1);
            if (writer.supportsInstanceWriter()) {
                InstanceWriter instanceWriter = writer.instanceWriter(genericType, entity, recordingHeaders);
                if (instanceWriter.alwaysInMemory()) {
                    requestBody = Http3RequestBody.create(instanceWriter.instanceBytes(), recordingHeaders.changes());
                } else {
                    long contentLength = instanceWriter.contentLength().orElse(configuredContentLength);
                    requestBody = contentLength < 0 || contentLength > clientConfig().maxInMemoryEntity()
                            ? Http3RequestBody.create(instanceWriter::write,
                                                      contentLength,
                                                      recordingHeaders.changes())
                            : Http3RequestBody.create(instanceWriter.instanceBytes(), recordingHeaders.changes());
                }
            } else if (configuredContentLength < 0
                    || configuredContentLength > clientConfig().maxInMemoryEntity()) {
                requestBody = Http3RequestBody.create((outputStream, isolatedHeaders) -> writer.write(genericType,
                                                                                                        entity,
                                                                                                        outputStream,
                                                                                                        isolatedHeaders),
                                                      configuredContentLength,
                                                      recordingHeaders.changes());
            } else {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream((int) configuredContentLength);
                writer.write(genericType, entity, outputStream, recordingHeaders);
                requestBody = Http3RequestBody.create(outputStream.toByteArray(), recordingHeaders.changes());
            }
            if (!recordingHeaders.changes().isEmpty()) {
                preparedEntityHeaders = recordingHeaders.application();
            }
        }
        requestBody.contentLength().ifPresent(headers()::contentLength);

        if (followRedirects()) {
            return Http3RedirectionProcessor.invokeWithFollowRedirects(this, 0, requestBody);
        }
        return invokeEntity(requestBody);
    }

    @Override
    public Http3ClientResponse doOutputStream(OutputStreamHandler outputStreamConsumer) {
        Http3RequestBody requestBody = Http3RequestBody.create(outputStreamConsumer,
                                                               headers().contentLength().orElse(-1));
        if (followRedirects()) {
            return Http3RedirectionProcessor.invokeWithFollowRedirects(this, 0, requestBody);
        }
        return invokeEntity(requestBody);
    }

    boolean priorKnowledge() {
        return priorKnowledge;
    }

    @Override
    public long tlsGeneration() {
        return delegate == null ? tlsGeneration : delegate.tlsGeneration();
    }

    @Override
    public void tlsGeneration(long tlsGeneration) {
        this.tlsGeneration = tlsGeneration;
        if (delegate != null) {
            delegate.tlsGeneration(tlsGeneration);
        }
    }

    Http3ClientResponse invokeEntity(Http3RequestBody requestBody) {
        requestBody.contentLength().ifPresent(headers()::contentLength);
        if (delegate == null) {
            tlsGeneration = tls().generation();
        }

        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        AtomicReference<String> actualProtocolId = new AtomicReference<>(Http3Client.PROTOCOL_ID);
        Http3CallEntityChain callChain = new Http3CallEntityChain(http3Client,
                                                                  this,
                                                                  delegate == null
                                                                          || explicitSelection
                                                                          || priorKnowledge
                                                                          || http3Client.allowsDirectHttp3(),
                                                                  actualProtocolId,
                                                                  whenSent,
                                                                  whenComplete,
                                                                  requestBody);

        Http3ClientResponse response;
        try {
            response = invokeWithServices(callChain,
                                          whenSent,
                                          whenComplete,
                                          requestBody);
        } catch (RuntimeException | Error failure) {
            requestBody.cancelIfUnattached();
            throw failure;
        }
        if (!followRedirects() || !Http3RedirectionProcessor.redirectionStatusCode(response.status())) {
            requestBody.cancelIfUnattached();
        }
        return response;
    }

    Http3ClientResponse invokeWithServices(Http3CallEntityChain callChain,
                                           CompletableFuture<WebClientServiceRequest> whenSent,
                                           CompletableFuture<WebClientServiceResponse> whenComplete,
                                           Http3RequestBody requestBody) {
        ClientUri resolvedUri = resolvedUri();

        WebClientServiceResponse serviceResponse;
        try {
            serviceResponse = invokeServices(http3Client.webClient(),
                                             callChain,
                                             whenSent,
                                             whenComplete,
                                             resolvedUri,
                                             request -> prepareRequestBody(requestBody, request));
            if (followRedirects() && Http3RedirectionProcessor.redirectionStatusCode(serviceResponse.status())) {
                redirectHeadersAfterServices = super.redirectSourceHeaders(serviceResponse.serviceRequest().headers());
            }
        } catch (RuntimeException | Error e) {
            WebClientServiceResponse rawResponse = callChain.rawResponse();
            if (rawResponse != null) {
                try {
                    rawResponse.connection().closeResource();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (e != cleanupFailure) {
                        e.addSuppressed(cleanupFailure);
                    }
                }
            }
            whenSent.completeExceptionally(e);
            whenComplete.completeExceptionally(e);
            throw e;
        }

        WebClientServiceResponse rawResponse = callChain.rawResponse();
        ResponseResources responseResources = null;
        CompletableFuture<WebClientServiceResponse> serviceCompletion = null;
        CompletableFuture<Void> complete = new CompletableFuture<>();
        try {
            ReleasableResource rawResource = rawResponse == null ? null : rawResponse.connection();
            responseResources = new ResponseResources(null, rawResource);
            ReleasableResource returnedResource = serviceResponse == rawResponse
                    ? rawResource
                    : serviceResponse.connection();
            responseResources = new ResponseResources(returnedResource, rawResource);
            serviceCompletion = serviceResponse.whenComplete();
            CompletableFuture<WebClientServiceResponse> finalServiceCompletion = serviceCompletion;
            complete.whenComplete((_, throwable) -> {
                if (throwable == null) {
                    finalServiceCompletion.complete(serviceResponse);
                    whenComplete.complete(serviceResponse);
                } else {
                    finalServiceCompletion.completeExceptionally(throwable);
                    whenComplete.completeExceptionally(throwable);
                }
            });

            ClientRequestHeaders finalizedHeaders = finalizedRequestHeaders();
            headers().clear();
            headers(finalizedHeaders);
            if (terminalEntityHeaders != null) {
                preparedEntityHeaders = preparedEntityHeaders == null
                        ? terminalEntityHeaders
                        : preparedEntityHeaders.andThen(terminalEntityHeaders);
            }
            if (delegate != null) {
                ClientRequestHeaders delegateHeaders = delegate.headers();
                delegateHeaders.remove(HeaderNames.HOST);
                finalizedHeaders.first(HeaderNames.HOST)
                        .ifPresent(value -> delegateHeaders.set(HeaderValues.create(HeaderNames.HOST, value)));
            }

            ResponseResources finalResponseResources = responseResources;
            return new Http3ClientResponseImpl(readTimeout(),
                                               callChain.protocolId(),
                                               serviceResponse.status(),
                                               finalizedHeaders,
                                               serviceResponse.headers(),
                                               serviceResponse.trailers(),
                                               serviceResponse.inputStream().orElse(null),
                                               mediaContext(),
                                               finalizedEndpointUri(),
                                               complete,
                                               finalResponseResources::close,
                                               finalResponseResources::readFailed,
                                               http3Client.protocolConfig().maxBufferedEntitySize().toBytes());
        } catch (RuntimeException | Error adaptationFailure) {
            if (responseResources != null) {
                try {
                    responseResources.close();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (adaptationFailure != cleanupFailure) {
                        adaptationFailure.addSuppressed(cleanupFailure);
                    }
                }
            }
            if (serviceCompletion != null) {
                serviceCompletion.completeExceptionally(adaptationFailure);
            }
            complete.completeExceptionally(adaptationFailure);
            whenSent.completeExceptionally(adaptationFailure);
            whenComplete.completeExceptionally(adaptationFailure);
            throw adaptationFailure;
        }
    }

    private void prepareRequestBody(Http3RequestBody requestBody, WebClientServiceRequest serviceRequest) {
        int maximum = Math.max(1, clientConfig().maxInMemoryEntity());
        int configuredBuffer = clientConfig().writeBufferSize();
        int desiredBuffer = configuredBuffer <= 1 ? 1024 : configuredBuffer;
        EntityWriterPreflight.Application serviceApplication = requestBody.prepare(serviceRequest.headers(),
                                                                                   serviceRequest.context(),
                                                                                   Math.max(1,
                                                                                            Math.min(desiredBuffer,
                                                                                                     maximum)));
        if (serviceRequest.method() == Method.QUERY && !serviceRequest.headers().contains(HeaderNames.CONTENT_TYPE)) {
            throw new IllegalArgumentException("Content-Type header is required for method '" + Method.QUERY + "'");
        }
        if (serviceApplication == null || requestBody.terminalHeaderChanges().isEmpty()) {
            return;
        }
        terminalEntityHeaders = serviceApplication;
    }

    HttpClientResponse fallbackResponse(Http3RequestBody requestBody,
                                        WebClientServiceRequest serviceRequest,
                                        boolean http1Only,
                                        CompletableFuture<WebClientServiceRequest> whenSent,
                                        Consumer<String> protocolConsumer) {
        if (priorKnowledge) {
            throw new IllegalStateException("HTTP/3 priorKnowledge is enabled, but this request cannot use HTTP/3.");
        }

        HttpClientRequest request = http3Client.fallbackClient().method(serviceRequest.method());
        if (http1Only) {
            request.protocolId("http/1.1");
        }
        ClientUri transportUri = ClientUri.create(serviceRequest.uri()).fragment(UriFragment.empty());
        request.headers().clear();
        request.skipUriEncoding(skipUriEncoding())
                .uri(transportUri)
                .followRedirects(false)
                .maxRedirects(maxRedirects())
                .keepAlive(keepAlive())
                .readTimeout(readTimeout())
                .readContinueTimeout(readContinueTimeout())
                .proxy(proxy())
                .tls(tls())
                .headers(serviceRequest.headers());
        ClientRequestOrigin targetOrigin = ClientRequestOrigin.create(serviceRequest.uri(), serviceRequest.headers());
        selectedProxyRoute().ifPresent(value -> {
            var inheritedOrigin = inheritedSelectedProxyRouteOrigin();
            if (inheritedOrigin.isEmpty()) {
                request.selectedProxyRoute(value);
            } else if (inheritedOrigin.get().equals(targetOrigin)) {
                request.inheritedSelectedProxyRoute(value, inheritedOrigin.get());
            }
        });
        address().ifPresent(value -> {
            var inheritedOrigin = inheritedAddressOrigin();
            if (inheritedOrigin.isEmpty()) {
                request.address(value);
            } else if (inheritedOrigin.get().equals(targetOrigin)) {
                request.inheritedAddress(value, inheritedOrigin.get());
            }
        });
        connection().ifPresent(value -> {
            var inheritedOrigin = inheritedConnectionOrigin();
            if (inheritedOrigin.isEmpty()) {
                request.connection(value);
            } else if (inheritedOrigin.get().equals(targetOrigin)) {
                request.inheritedConnection(value, inheritedOrigin.get());
            }
        });
        sendExpectContinue().ifPresent(request::sendExpectContinue);
        sni().ifPresent(request::sni);
        pathParams().forEach(request::pathParam);
        properties().forEach(request::property);
        Header originalProxyConnection = serviceRequest.headers().contains(PROXY_CONNECTION)
                ? serviceRequest.headers().get(PROXY_CONNECTION)
                : null;
        AtomicBoolean proxyConnectionRestored = new AtomicBoolean();
        Runnable restoreProxyConnection = () -> {
            if (proxyConnectionRestored.compareAndSet(false, true)) {
                serviceRequest.headers().remove(PROXY_CONNECTION);
                if (originalProxyConnection != null) {
                    serviceRequest.headers().set(originalProxyConnection);
                }
            }
        };
        CompletableFuture<WebClientServiceRequest> fallbackWhenSent = new CompletableFuture<>();
        fallbackWhenSent.whenComplete((_, failure) -> {
            restoreProxyConnection.run();
            if (failure == null) {
                whenSent.complete(serviceRequest);
            } else {
                whenSent.completeExceptionally(failure);
            }
        });
        if (!request.serviceRequestAfterServices(serviceRequest,
                                                 _ -> {
                                                 },
                                                 fallbackWhenSent,
                                                 protocolConsumer,
                                                 true,
                                                 http3Client::publishResponse)) {
            throw new IllegalStateException("Fallback request does not support post-service dispatch");
        }
        HttpClientResponse response;
        try {
            response = requestBody.submit(request);
        } finally {
            restoreProxyConnection.run();
        }
        request.lastSelectedProxyRoute().ifPresent(route -> request.inheritedLastSelectedProxyRouteOrigin()
                .ifPresentOrElse(origin -> inheritedSelectedProxyRoute(route, origin),
                                 () -> selectedProxyRoute(route)));
        tlsGeneration(request.tlsGeneration());
        return response;
    }

    ClientRequestHeaders redirectSourceHeaders() {
        return redirectHeadersAfterServices == null
                ? EntityWriterPreflight.copyOf(headers())
                : redirectHeadersAfterServices;
    }

    private static final class ResponseResources {
        private final ReleasableResource returned;
        private final ReleasableResource raw;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ResponseResources(ReleasableResource returned, ReleasableResource raw) {
            this.returned = returned;
            this.raw = raw == returned ? null : raw;
        }

        private void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            Throwable failure = close(returned, null);
            failure = close(raw, failure);
            rethrow(failure);
        }

        private void readFailed(Throwable readFailure) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            Throwable failure = fail(returned, readFailure, null);
            failure = fail(raw, readFailure, failure);
            rethrow(failure);
        }

        private static Throwable close(ReleasableResource resource, Throwable previous) {
            if (resource == null) {
                return previous;
            }
            try {
                resource.closeResource();
            } catch (RuntimeException | Error failure) {
                return suppress(previous, failure);
            }
            return previous;
        }

        private static Throwable fail(ReleasableResource resource, Throwable readFailure, Throwable previous) {
            if (resource == null) {
                return previous;
            }
            try {
                if (resource instanceof Http3StreamedResponse streamedResponse) {
                    streamedResponse.readTimedOut(readFailure);
                } else {
                    resource.closeResource();
                }
            } catch (RuntimeException | Error failure) {
                return suppress(previous, failure);
            }
            return previous;
        }

        private static Throwable suppress(Throwable previous, Throwable failure) {
            if (previous == null) {
                return failure;
            }
            if (previous != failure) {
                previous.addSuppressed(failure);
            }
            return previous;
        }

        private static void rethrow(Throwable failure) {
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
        }
    }

}
