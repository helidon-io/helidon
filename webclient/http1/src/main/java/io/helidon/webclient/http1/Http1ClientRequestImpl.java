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
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
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
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientRequestBase;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.Proxy.ProxyType;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

class Http1ClientRequestImpl extends ClientRequestBase<Http1ClientRequest, Http1ClientResponse> implements Http1ClientRequest {
    private static final System.Logger LOGGER = System.getLogger(Http1ClientRequestImpl.class.getName());
    // RFC 9110, section 8.1: these define the replayed representation data's format and encoding.
    private static final Set<HeaderName> REPRESENTATION_HEADERS = Set.of(HeaderNames.CONTENT_TYPE,
                                                                         HeaderNames.CONTENT_ENCODING);

    private final Http1ClientImpl http1Client;
    private final FullClientRequest<?> delegate;

    Http1ClientRequestImpl(Http1ClientImpl http1Client,
                           Method method,
                           ClientUri clientUri,
                           Map<String, String> properties) {
        this(http1Client, null, method, clientUri, null, properties);
    }

    Http1ClientRequestImpl(Http1ClientImpl http1Client,
                           FullClientRequest<?> delegate,
                           Method method,
                           ClientUri clientUri,
                           Boolean sendExpectContinue,
                           Map<String, String> properties) {
        this(http1Client, delegate, method, clientUri, sendExpectContinue, properties, null, false);
    }

    private Http1ClientRequestImpl(Http1ClientImpl http1Client,
                                   FullClientRequest<?> delegate,
                                   Method method,
                                   ClientUri clientUri,
                                   Boolean sendExpectContinue,
                                   Map<String, String> properties,
                                   ClientUri redirectSourceUri,
                                   boolean crossOriginRedirect) {
        super(http1Client.clientConfig(),
              http1Client.webClient().cookieManager(),
              Http1Client.PROTOCOL_ID,
              method,
              clientUri,
              sendExpectContinue,
              properties,
              redirectSourceUri,
              crossOriginRedirect);
        this.http1Client = http1Client;
        this.delegate = delegate;
    }

    //Copy constructor for redirection purposes
    Http1ClientRequestImpl(Http1ClientRequestImpl request,
                           Method method,
                           ClientUri clientUri,
                           Map<String, String> properties,
                           boolean preserveEntity) {
        this(request.http1Client,
             null,
             method,
             clientUri,
             null,
             properties,
             request.resolvedUri(),
             request.crossesRedirectOriginBoundary(clientUri));

        followRedirects(request.followRedirects());
        maxRedirects(request.maxRedirects());
        tls(request.tls());
        readTimeout(request.readTimeout());
        readContinueTimeout(request.readContinueTimeout());
        request.sendExpectContinue().ifPresent(this::sendExpectContinue);
        if (preserveEntity) {
            REPRESENTATION_HEADERS.forEach(name -> request.headers().find(name).ifPresent(headers()::set));
        }
    }

    @Override
    public Http1ClientResponse doSubmit(Object entity) {
        byte[] entityBytes;
        if (entity == BufferData.EMPTY_BYTES) {
            entityBytes = BufferData.EMPTY_BYTES;
        } else if (entity instanceof byte[] buffer) {
            entityBytes = buffer;
        } else {
            // must apply media writer, and if the writer has unknown length, or longer than we can buffer, stream it
            GenericType<Object> genericType = GenericType.create(entity);
            EntityWriter<Object> mediaWriter = clientConfig()
                    .mediaContext()
                    .writer(genericType, headers());

            long configuredContentLength = headers().contentLength().orElse(-1);
            if (mediaWriter.supportsInstanceWriter()) {
                InstanceWriter instanceWriter = mediaWriter.instanceWriter(genericType, entity, headers());
                if (instanceWriter.alwaysInMemory()) {
                    entityBytes = instanceWriter.instanceBytes();
                } else {
                    long length = instanceWriter.contentLength().orElse(configuredContentLength);
                    if (length == -1) {
                        return doOutputStream((output, ignored1) -> instanceWriter.write(output));
                    } else if (length > clientConfig().maxInMemoryEntity()) {
                        headers().contentLength(length);
                        return doOutputStream((output, ignored2) -> instanceWriter.write(output));
                    } else {
                        entityBytes = instanceWriter.instanceBytes();
                    }
                }
            } else {
                if (configuredContentLength == -1 || configuredContentLength > clientConfig().maxInMemoryEntity()) {
                    return doOutputStream((output, requestHeaders) -> mediaWriter.write(genericType,
                                                                                       entity,
                                                                                       output,
                                                                                       requestHeaders));
                } else {
                    // safe to cast to int, as the maxInMemoryEntity configuration option is an int
                    ByteArrayOutputStream baos = new ByteArrayOutputStream((int) configuredContentLength);
                    mediaWriter.write(genericType, entity, baos, headers());
                    entityBytes = baos.toByteArray();
                }
            }
        }

        if (followRedirects()) {
            return RedirectionProcessor.invokeWithFollowRedirects(this, entityBytes);
        }
        return invokeRequestWithEntity(entityBytes);
    }

    @Override
    public Http1ClientResponse doOutputStream(OutputStreamHandler streamHandler) {
        return doOutputStream(streamHandler, null);
    }

    @Override
    public UpgradeResponse upgrade(String protocol) {
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
    }

    @Override
    protected MediaContext mediaContext() {
        return super.mediaContext();
    }

    @Override
    protected void additionalHeaders() {
        super.additionalHeaders();
        if (proxy().type() != ProxyType.NONE) {
            header(PROXY_CONNECTION);
        }
    }

    Http1ClientImpl http1Client() {
        return http1Client;
    }

    ClientUri redirectUri(String location) {
        return resolveRedirectUri(location);
    }

    void sanitizeRedirectHeaders(ClientUri requestUri, ClientRequestHeaders requestHeaders) {
        super.sanitizeRedirectSensitiveHeaders(requestUri, requestHeaders);
    }

    boolean canReplayEntityTo(ClientUri requestUri) {
        return clientConfig().followCrossOriginEntityRedirects() || !crossesRedirectOriginBoundary(requestUri);
    }

    /**
     * Check upgrade protocols. Protocol names are case insensitive.
     *
     * @param requestUpgrade request upgrade header
     * @param responseUpgrade response upgrade header
     * @return check if protocol upgrade can proceed
     */
    static boolean upgradeSuccessful(Header requestUpgrade, Header responseUpgrade) {
        var requestedProtocols = upgradeProtocols(requestUpgrade);
        var selectedProtocols = upgradeProtocols(responseUpgrade);
        if (selectedProtocols.isEmpty()) {
            return false;
        }
        // RFC 2817, section 3.3 retains HTTP/1.1 above the newly selected TLS layer.
        boolean tlsWithRetainedHttp = selectedProtocols.size() == 2
                && selectedProtocols.getFirst().matches("(?i)TLS/[0-9]\\.[0-9]")
                && Http1Client.PROTOCOL_ID.equalsIgnoreCase(selectedProtocols.getLast());
        for (String selectedProtocol : selectedProtocols) {
            if (requestedProtocols.stream().noneMatch(protocol -> protocol.equalsIgnoreCase(selectedProtocol))
                    && !(tlsWithRetainedHttp && Http1Client.PROTOCOL_ID.equalsIgnoreCase(selectedProtocol))) {
                return false;
            }
        }
        return true;
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

    private static List<String> upgradeProtocols(Header header) {
        return header.allValues().stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private Http1ClientResponseImpl invokeWithServices(Http1CallChainBase callChain,
                                                       CompletableFuture<WebClientServiceRequest> whenSent,
                                                       CompletableFuture<WebClientServiceResponse> whenComplete) {

        // will create a copy, so we could invoke this method multiple times
        ClientUri resolvedUri = resolvedUri();

        WebClientServiceResponse serviceResponse = invokeServices(callChain, whenSent, whenComplete, resolvedUri);

        CompletableFuture<Void> complete = new CompletableFuture<>();
        complete.thenAccept(ignored -> serviceResponse.whenComplete().complete(serviceResponse))
                .exceptionally(throwable -> {
                    serviceResponse.whenComplete().completeExceptionally(throwable);
                    return null;
                });

        if (delegate != null) {
            ClientRequestHeaders delegateHeaders = delegate.headers();
            this.headers().forEach(delegateHeaders::set);
        }
        ClientConnection responseConnection = serviceResponse.connection() instanceof ClientConnection clientConnection
                ? clientConnection
                : callChain.connection();
        return new Http1ClientResponseImpl(clientConfig(),
                                           http1Client().protocolConfig(),
                                           serviceResponse.status(),
                                           serviceResponse.serviceRequest().method(),
                                           serviceResponse.serviceRequest().headers(),
                                           serviceResponse.headers(),
                                           responseConnection,
                                           serviceResponse.inputStream().orElse(null),
                                           mediaContext(),
                                           resolvedUri,
                                           complete,
                                           callChain.transportResponse());
    }

    private Http1ClientResponseImpl doOutputStream(BiConsumer<OutputStream, ClientRequestHeaders> mediaWriter) {
        Http1ClientResponseImpl response = null;
        try (Http1EntityWriter entityWriter = new Http1EntityWriter(mediaWriter)) {
            response = doOutputStream(null, entityWriter);
            return response;
        } catch (RuntimeException | Error failure) {
            if (response != null) {
                try {
                    response.closeConnectionOnClose();
                    response.close();
                } catch (RuntimeException | Error closeFailure) {
                    if (closeFailure != failure) {
                        failure.addSuppressed(closeFailure);
                    }
                }
            }
            throw failure;
        }
    }

    private Http1ClientResponseImpl doOutputStream(OutputStreamHandler streamHandler, Http1EntityWriter entityWriter) {
        Http1ClientRequestImpl request = this;
        OutputStreamHandler handler = streamHandler;
        boolean allowEntity = true;
        int followedRedirects = 0;
        while (true) {
            CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
            CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
            Http1CallOutputStreamChain callChain = new Http1CallOutputStreamChain(http1Client,
                                                                                 request,
                                                                                 whenSent,
                                                                                 whenComplete,
                                                                                 handler,
                                                                                 allowEntity,
                                                                                 entityWriter);
            Http1ClientResponseImpl response = request.invokeWithServices(callChain, whenSent, whenComplete);
            if (!request.followRedirects() || !RedirectionProcessor.redirectionStatusCode(response.status())) {
                return response;
            }

            Http1ClientRequestImpl redirectedRequest;
            boolean sendEntity;
            boolean emptyLegacyEntity = false;
            try (response) {
                // Finish this hop's services and release its response before the next hop starts its own service chain.
                response.closeConnectionOnClose();
                if (followedRedirects >= maxRedirects()) {
                    throw RedirectionProcessor.maxRedirectsReached(maxRedirects());
                }
                followedRedirects++;
                if (!response.headers().contains(HeaderNames.LOCATION)) {
                    throw new IllegalStateException("There is no " + HeaderNames.LOCATION
                                                            + " header present in the response! "
                                                            + "It is not clear where to redirect.");
                }
                ClientUri redirectUri = request.redirectUri(response.headers().get(HeaderNames.LOCATION).get());
                sendEntity = RedirectionProcessor.keepsMethodAndEntity(response.status());
                allowEntity = !sendEntity || request.canReplayEntityTo(redirectUri);
                if (!allowEntity) {
                    if (callChain.hasEntity()) {
                        throw new IllegalStateException("Cross-origin redirect with request entity is disabled.");
                    }
                    if (callChain.handlerInvoked()) {
                        handler = OutputStream::close;
                        if (entityWriter != null) {
                            entityWriter.close();
                            entityWriter = null;
                            emptyLegacyEntity = true;
                        }
                    }
                    // An uninvoked unknown-length handler may still be empty; reject its first nonempty write on the next hop.
                }
                redirectedRequest = new Http1ClientRequestImpl(request,
                                                              sendEntity ? request.method() : Method.GET,
                                                              redirectUri,
                                                              request.properties(),
                                                              sendEntity);
                if (emptyLegacyEntity) {
                    redirectedRequest.header(HeaderValues.CONTENT_LENGTH_ZERO);
                }
            }
            redirectedRequest.proxy(request.proxy());
            redirectedRequest.additionalHeaders();
            if (!sendEntity) {
                if (entityWriter != null) {
                    entityWriter.close();
                }
                return RedirectionProcessor.invokeWithFollowRedirects(redirectedRequest,
                                                                      followedRedirects,
                                                                      BufferData.EMPTY_BYTES);
            }
            request = redirectedRequest;
        }
    }

}
