/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.jersey.connector;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Response;

import io.helidon.common.Version;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;

import org.glassfish.jersey.client.ClientAsyncExecutorLiteral;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.Connector;
import org.glassfish.jersey.internal.util.PropertiesHelper;
import org.glassfish.jersey.spi.ExecutorServiceProvider;

/**
 * A {@link Connector} that utilizes the Helidon HTTP Client to send and receive
 * HTTP request and responses.
 */
class HelidonConnector implements Connector {

    private static final String HELIDON_VERSION = "Helidon/" + Version.VERSION + " (java " + AccessController
            .doPrivileged(PropertiesHelper.getSystemProperty("java.runtime.version")) + ")";
    static final Logger LOGGER = Logger.getLogger(HelidonConnector.class.getName());

    private final WebClient webClient;

    private final ExecutorServiceKeeper executorServiceKeeper;
    private final HelidonEntity.HelidonEntityType entityType;

    private static final InputStream NO_CONTENT_INPUT_STREAM = new InputStream() {
        @Override
        public int read() throws IOException {
            return -1;
        }
    };

    // internal implementation entity type, can be removed in the future
    // settable for testing purposes
    // see LargeDataTest

    static final String INTERNAL_ENTITY_TYPE = "jersey.connector.helidon.entity.type";

    HelidonConnector(final Client client, final Configuration config) {
        executorServiceKeeper = new ExecutorServiceKeeper(client);
        entityType = getEntityType(config);

        final WebClient.Builder webClientBuilder = WebClient.builder();

        webClientBuilder.addReader(HelidonStructures.createInputStreamBodyReader());
        HelidonEntity.helidonWriter(entityType).ifPresent(webClientBuilder::addWriter);

        HelidonStructures.createProxy(config).ifPresent(webClientBuilder::proxy);

        HelidonStructures.helidonConfig(config).ifPresent(webClientBuilder::config);

        webClientBuilder.connectTimeout(ClientProperties.getValue(config.getProperties(),
                ClientProperties.CONNECT_TIMEOUT, 10000), TimeUnit.MILLISECONDS);

        HelidonStructures.createSSL(client.getSslContext()).ifPresent(webClientBuilder::tls);

        webClient = webClientBuilder.build();
    }

    @Override
    public ClientResponse apply(ClientRequest request) {
        try {
            return applyInternal(request).toCompletableFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new ProcessingException(e);
        }
    }

    @Override
    public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
        final BiConsumer<? super ClientResponse, ? super Throwable> action = (r, th) -> {
            if (th == null) {
                callback.response(r);
            } else {
                callback.failure(th);
            }
        };
        return applyInternal(request)
                .whenCompleteAsync(action, executorServiceKeeper.getExecutorService(request))
                .toCompletableFuture();
    }

    @Override
    public String getName() {
        return HELIDON_VERSION;
    }

    @Override
    public void close() {

    }

    private CompletionStage<ClientResponse> applyInternal(ClientRequest request) {
        final WebClientRequestBuilder webClientRequestBuilder = webClient.method(request.getMethod());
        webClientRequestBuilder.uri(request.getUri());

        webClientRequestBuilder.headers(HelidonStructures.createHeaders(request.getRequestHeaders()));

        for (String propertyName : request.getConfiguration().getPropertyNames()) {
            Object property = request.getConfiguration().getProperty(propertyName);
            if (!propertyName.startsWith("jersey") && String.class.isInstance(property)) {
                webClientRequestBuilder.property(propertyName, (String) property);
            }
        }

        for (String propertyName : request.getPropertyNames()) {
            Object property = request.resolveProperty(propertyName, Object.class);
            if (!propertyName.startsWith("jersey") && String.class.isInstance(property)) {
                webClientRequestBuilder.property(propertyName, (String) property);
            }
        }

        // TODO
        // HelidonStructures.createProxy(request).ifPresent(webClientRequestBuilder::proxy);

        webClientRequestBuilder.followRedirects(request.resolveProperty(ClientProperties.FOLLOW_REDIRECTS, true));
        webClientRequestBuilder.readTimeout(request.resolveProperty(ClientProperties.READ_TIMEOUT, 10000), TimeUnit.MILLISECONDS);

        CompletionStage<WebClientResponse> responseStage = null;

        if (request.hasEntity()) {
            responseStage = HelidonEntity.submit(
                    entityType, request, webClientRequestBuilder, executorServiceKeeper.getExecutorService(request)
            );
        } else {
            responseStage = webClientRequestBuilder.submit();
        }

        return responseStage.thenCompose((a) -> convertResponse(request, a));
    }

    private CompletionStage<ClientResponse> convertResponse(final ClientRequest requestContext,
                                                            final WebClientResponse webClientResponse) {

        final ClientResponse responseContext = new ClientResponse(new Response.StatusType() {
            @Override
            public int getStatusCode() {
                return webClientResponse.status().code();
            }

            @Override
            public Response.Status.Family getFamily() {
                return Response.Status.Family.familyOf(getStatusCode());
            }

            @Override
            public String getReasonPhrase() {
                return webClientResponse.status().reasonPhrase();
            }
        }, requestContext);

        for (Map.Entry<String, List<String>> entry : webClientResponse.headers().toMap().entrySet()) {
            for (String value : entry.getValue()) {
                responseContext.getHeaders().add(entry.getKey(), value);
            }
        }

        responseContext.setResolvedRequestUri(webClientResponse.lastEndpointURI());

        final CompletionStage<InputStream> stream = HelidonStructures.hasEntity(webClientResponse)
                ? webClientResponse.content().as(InputStream.class)
                : CompletableFuture.supplyAsync(() -> NO_CONTENT_INPUT_STREAM);

        return stream.thenApply((a) -> {
            responseContext.setEntityStream(new FilterInputStream(a) {
                private final AtomicBoolean closed = new AtomicBoolean(false);

                @Override
                public void close() throws IOException {
                    // Avoid idempotent close in the underlying input stream
                    if (!closed.compareAndSet(false, true)) {
                        super.close();
                    }
                }
            });
            return responseContext;
        });
    }

    private static HelidonEntity.HelidonEntityType getEntityType(final Configuration config) {
        final String helidonType = ClientProperties.getValue(config.getProperties(),
                INTERNAL_ENTITY_TYPE, HelidonEntity.HelidonEntityType.READABLE_BYTE_CHANNEL.name());
        final HelidonEntity.HelidonEntityType entityType = HelidonEntity.HelidonEntityType.valueOf(helidonType);

        return entityType;
    }

    private static class ExecutorServiceKeeper {
        private Optional<ExecutorService> executorService;

        private ExecutorServiceKeeper(Client client) {
            final ClientConfig config = ((JerseyClient) client).getConfiguration();
            executorService = Optional.ofNullable(config.getExecutorService());
        }

        private ExecutorService getExecutorService(ClientRequest request) {
            if (!executorService.isPresent()) {
                // cache for multiple requests
                executorService = Optional.ofNullable(request.getInjectionManager()
                        .getInstance(ExecutorServiceProvider.class, ClientAsyncExecutorLiteral.INSTANCE).getExecutorService());
            }

            return executorService.get();
        }
    }
}
