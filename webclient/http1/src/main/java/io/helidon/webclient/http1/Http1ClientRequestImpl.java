/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.InstanceWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.webclient.api.ClientRequestBase;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.Proxy.ProxyType;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

class Http1ClientRequestImpl extends ClientRequestBase<Http1ClientRequest, Http1ClientResponse> implements Http1ClientRequest {
    private static final System.Logger LOGGER = System.getLogger(Http1ClientRequestImpl.class.getName());
    private final Http1ClientImpl http1Client;

    Http1ClientRequestImpl(Http1ClientImpl http1Client,
                           Method method,
                           ClientUri clientUri,
                           Map<String, String> properties) {
        this(http1Client, method, clientUri, null, properties);
    }

    Http1ClientRequestImpl(Http1ClientImpl http1Client,
                               Method method,
                               ClientUri clientUri,
                               Boolean sendExpectContinue,
                               Map<String, String> properties) {
        super(http1Client.clientConfig(),
                http1Client.webClient().cookieManager(),
                Http1Client.PROTOCOL_ID,
                method,
                clientUri,
                sendExpectContinue,
                properties);
        this.http1Client = http1Client;
    }

    //Copy constructor for redirection purposes
    Http1ClientRequestImpl(Http1ClientRequestImpl request,
                           Method method,
                           ClientUri clientUri,
                           Map<String, String> properties) {
        this(request.http1Client,
                method,
                clientUri,
                null,
                properties);

        followRedirects(request.followRedirects());
        maxRedirects(request.maxRedirects());
        tls(request.tls());
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
                        return doOutputStream(instanceWriter::write);
                    } else if (length > clientConfig().maxInMemoryEntity()) {
                        headers().contentLength(length);
                        return doOutputStream(instanceWriter::write);
                    } else {
                        entityBytes = instanceWriter.instanceBytes();
                    }
                }
            } else {
                if (configuredContentLength == -1 || configuredContentLength > clientConfig().maxInMemoryEntity()) {
                    return doOutputStream(it -> mediaWriter.write(genericType, entity, it, headers()));
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
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        Http1CallChainBase callChain = new Http1CallOutputStreamChain(http1Client,
                                                                      this,
                                                                      whenSent,
                                                                      whenComplete,
                                                                      streamHandler);

        return invokeWithServices(callChain, whenSent, whenComplete);
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
            if (isUpgradeSuccessful(requestedUpgrade, response.headers().get(HeaderNames.UPGRADE))) {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    response.connection()
                            .helidonSocket().log(LOGGER,
                                                 System.Logger.Level.TRACE,
                                                 "Upgrading to %s",
                                                 requestedUpgrade);
                }
                // upgrade was a success
                return UpgradeResponse.success(response, response.connection());
            } else {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    response.connection().helidonSocket().log(LOGGER,
                                                              System.Logger.Level.TRACE,
                                                              "Upgrade failed. Expected upgrade: {0}, got headers: {1}",
                                                              requestedUpgrade,
                                                              response.headers());
                }
            }
        } else {
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                response.connection().helidonSocket().log(LOGGER,
                                                          System.Logger.Level.TRACE,
                                                          "Upgrade failed. Tried upgrading to %s, got status: %s",
                                                          requestedUpgrade,
                                                          response.status());
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

    /**
     * Check upgrade protocols. Protocol names are case insensitive.
     *
     * @param requestUpgrade request upgrade header
     * @param responseUpgrade response upgrade header
     * @return check if protocol upgrade can proceed
     */
    static boolean isUpgradeSuccessful(Header requestUpgrade, Header responseUpgrade) {
        for (String protocol : requestUpgrade.allValues()) {
            if (protocol.equalsIgnoreCase(responseUpgrade.get())) {
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

        return new Http1ClientResponseImpl(clientConfig(),
                                           http1Client().protocolConfig(),
                                           serviceResponse.status(),
                                           serviceResponse.serviceRequest().headers(),
                                           serviceResponse.headers(),
                                           callChain.connection(),
                                           serviceResponse.inputStream().orElse(null),
                                           mediaContext(),
                                           resolvedUri,
                                           complete);
    }

}
