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

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.uri.UriFragment;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClientProtocolResponse;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.api.WebClientServiceResponseSupport;
import io.helidon.webclient.api.WebClientTransportObserverSupport;
import io.helidon.webclient.spi.WebClientService;

import static io.helidon.http.HeaderNames.CONTENT_ENCODING;

class Http3CallEntityChain implements WebClientService.TransportChain {
    private static final HeaderName ALT_USED = HeaderNames.create("Alt-Used");

    private final Http3ClientImpl http3Client;
    private final Http3ClientRequestImpl clientRequest;
    private final boolean allowDirect;
    private final AtomicReference<String> actualProtocolId;
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final CompletableFuture<WebClientServiceResponse> whenComplete;
    private final Http3RequestBody requestBody;
    private volatile Http3Discovery.Selection selection;
    private volatile WebClientServiceResponse rawResponse;
    private WebClientProtocolResponse pendingProtocolResponse;

    Http3CallEntityChain(Http3ClientImpl http3Client,
                         Http3ClientRequestImpl clientRequest,
                         boolean allowDirect,
                         AtomicReference<String> actualProtocolId,
                         CompletableFuture<WebClientServiceRequest> whenSent,
                         CompletableFuture<WebClientServiceResponse> whenComplete,
                         Http3RequestBody requestBody) {
        this.http3Client = http3Client;
        this.clientRequest = clientRequest;
        this.allowDirect = allowDirect;
        this.actualProtocolId = actualProtocolId;
        this.whenSent = whenSent;
        this.whenComplete = whenComplete;
        this.requestBody = requestBody;
    }

    @Override
    public WebClientServiceResponse proceed(WebClientServiceRequest serviceRequest) {
        ClientUri requestUri = serviceRequest.uri();
        ClientRequestHeaders requestHeaders = serviceRequest.headers();
        Optional<Http3Discovery.Selection> selected = http3Client.requestTarget(clientRequest,
                                                                                requestUri,
                                                                                requestHeaders,
                                                                                allowDirect);
        if (selected.isEmpty()) {
            return fallback(serviceRequest, null);
        }
        selection = selected.get();
        Http3Discovery.Target target = selection.target();
        if (target.alternative()) {
            WritableHeaders<?> wireHeaders = WritableHeaders.create(requestHeaders);
            wireHeaders.set(HeaderValues.create(ALT_USED, target.altUsed()));
            requestHeaders = ClientRequestHeaders.create(wireHeaders);
        }
        Http3StreamedResponse response = null;
        RuntimeException attemptFailure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                response = exchangeClient(serviceRequest).send(
                        transportUri(serviceRequest.uri()),
                        serviceRequest.method(),
                        requestHeaders,
                        requestBody,
                        new Http3ExchangeClient.RequestOptions(
                                clientRequest.readTimeout(),
                                clientRequest.readContinueTimeout(),
                                clientRequest.sendExpectContinue().orElse(http3Client.clientConfig().sendExpectContinue()),
                                attempt > 0,
                                serviceRequest.context(),
                                () -> whenSent.complete(serviceRequest)));
                break;
            } catch (RuntimeException e) {
                attemptFailure = e;
                Throwable cause = Http3RequestFailureSupport.attemptCause(e);
                if (attempt == 0
                        && Http3RequestFailureSupport.attemptDisposition(e)
                        == Http3RequestFailureSupport.AttemptDisposition.NOT_PROCESSED
                        && Http3RequestFailureSupport.isRetryableRequestFailure(cause)
                        && requestBody.canStartAttempt()) {
                    continue;
                }
                break;
            }
        }

        if (response == null) {
            RuntimeException failure = Objects.requireNonNull(attemptFailure, "attemptFailure");
            Throwable cause = Http3RequestFailureSupport.attemptCause(failure);
            boolean sessionReusable = Http3RequestFailureSupport.sessionReusable(failure);
            if (Http3RequestFailureSupport.endpointFailure(failure)) {
                http3Client.recordFailure(selection());
            }
            Http3RequestFailureSupport.AttemptDisposition disposition =
                    Http3RequestFailureSupport.attemptDisposition(failure);
            if ((sessionReusable
                    && disposition != Http3RequestFailureSupport.AttemptDisposition.VERSION_FALLBACK)
                    || clientRequest.priorKnowledge()
                    || disposition == Http3RequestFailureSupport.AttemptDisposition.POSSIBLY_PROCESSED
                    || !requestBody.canStartAttempt()) {
                whenSent.completeExceptionally(cause);
                throw failure;
            }

            return fallback(serviceRequest, cause);
        }

        try {
            ClientResponseHeaders responseHeaders = responseHeaders(response);
            Status responseStatus = Status.create(response.status());
            captureProtocolResponse(selection(), response, responseStatus, responseHeaders);
            WebClientServiceResponse.Builder builder = WebClientServiceResponse.builder()
                    .serviceRequest(serviceRequest)
                    .whenComplete(whenComplete)
                    .status(responseStatus)
                    .headers(responseHeaders)
                    .trailers(responseTrailers(response, responseHeaders))
                    .connection(response);

            if (response.hasEntity()) {
                builder.inputStream(contentDecoder(responseHeaders)
                                            .apply(response.inputStream()));
            }

            WebClientServiceResponse result = builder.build();
            rawResponse = result;
            return result;
        } catch (RuntimeException | Error adaptationFailure) {
            try {
                response.closeResource();
            } catch (RuntimeException | Error cleanupFailure) {
                if (adaptationFailure != cleanupFailure) {
                    adaptationFailure.addSuppressed(cleanupFailure);
                }
            }
            throw adaptationFailure;
        }
    }

    @Override
    public String protocolId() {
        return actualProtocolId.get();
    }

    @Override
    public Optional<WebClientProtocolResponse> protocolResponse(WebClientServiceResponse response) {
        WebClientProtocolResponse result = pendingProtocolResponse;
        pendingProtocolResponse = null;
        if (result != null && !http3Client.responseNotificationsManagedByWebClient()) {
            http3Client.publishResponse(result);
            return Optional.empty();
        }
        return Optional.ofNullable(result);
    }

    void captureProtocolResponse(Http3Discovery.Selection currentSelection,
                                 Http3StreamedResponse response,
                                 Status status,
                                 ClientResponseHeaders headers) {
        if (status.code() == Status.MISDIRECTED_REQUEST_421_CODE) {
            http3Client.connectionCache().discovery().recordMisdirected(currentSelection);
            return;
        }
        http3Client.recordSuccess(currentSelection);
        if (!http3Client.altSvcNotificationsEnabled()
                || pendingProtocolResponse != null
                || !headers.contains(HeaderNames.ALT_SVC)) {
            return;
        }

        var responseTarget = response.resolvedTarget();
        var receivedAt = response.receivedAt();
        if (currentSelection.target().alternative()) {
            pendingProtocolResponse = WebClientProtocolResponse.createAlternative(responseTarget,
                                                                                  false,
                                                                                  protocolId(),
                                                                                  status,
                                                                                  headers,
                                                                                  receivedAt,
                                                                                  responseTarget.routeAuthority());
        } else {
            pendingProtocolResponse = WebClientProtocolResponse.create(responseTarget,
                                                                       false,
                                                                       protocolId(),
                                                                       status,
                                                                       headers,
                                                                       receivedAt);
        }
    }

    Http3Discovery.Selection selection() {
        return selection;
    }

    WebClientServiceResponse rawResponse() {
        return rawResponse;
    }

    private static URI transportUri(ClientUri uri) {
        if (!uri.fragment().hasValue()) {
            return uri.toUri();
        }
        return ClientUri.create(uri)
                .fragment(UriFragment.empty())
                .toUri();
    }

    private WebClientServiceResponse fallback(WebClientServiceRequest serviceRequest, Throwable attemptCause) {
        try {
            HttpClientResponse fallbackResponse = clientRequest.fallbackResponse(requestBody,
                                                                                  serviceRequest,
                                                                                  Http3RequestFailureSupport
                                                                                          .isVersionFallback(attemptCause),
                                                                                  whenSent,
                                                                                  actualProtocolId::set);
            try {
                actualProtocolId.set(fallbackResponse.protocolId());
                WebClientServiceResponse result = WebClientServiceResponseSupport.create(serviceRequest,
                                                                                          whenComplete,
                                                                                          fallbackResponse);
                rawResponse = result;
                return result;
            } catch (RuntimeException | Error adaptationFailure) {
                try {
                    fallbackResponse.close();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (adaptationFailure != cleanupFailure) {
                        adaptationFailure.addSuppressed(cleanupFailure);
                    }
                }
                throw adaptationFailure;
            }
        } catch (RuntimeException | Error fallbackException) {
            if (attemptCause != null) {
                fallbackException.addSuppressed(attemptCause);
            }
            whenSent.completeExceptionally(fallbackException);
            throw fallbackException;
        }
    }

    private Http3ExchangeClient exchangeClient(WebClientServiceRequest serviceRequest) {
        Http3Discovery.Selection currentSelection = selection();
        Http3Discovery.Target target = currentSelection.target();
        return new Http3ExchangeClient.Builder()
                .connectionCache(http3Client.connectionCache())
                .cacheKey(http3Client.connectionCacheKey(currentSelection.key(), target))
                .connectionCacheSize(http3Client.clientConfig().connectionCacheSize())
                .executor(http3Client.webClient().executor())
                .requestExecutor(http3Client.clientConfig().executor())
                .selection(currentSelection)
                .receiveFrameListener(http3Client.receiveFrameListener())
                .sendFrameListener(http3Client.sendFrameListener())
                .transportObserver(WebClientTransportObserverSupport.observer(http3Client.webClient()))
                .build();
    }

    private ContentDecoder contentDecoder(ClientResponseHeaders responseHeaders) {
        if (http3Client.clientConfig().contentEncoding().contentDecodingEnabled()
                && responseHeaders.contains(CONTENT_ENCODING)) {
            String contentEncoding = responseHeaders.get(CONTENT_ENCODING).get();
            if (http3Client.clientConfig().contentEncoding().contentDecodingSupported(contentEncoding)) {
                return http3Client.clientConfig().contentEncoding().decoder(contentEncoding);
            }
            throw new IllegalStateException("Unsupported content encoding: " + contentEncoding);
        }
        return ContentDecoder.NO_OP;
    }

    private ClientResponseHeaders responseHeaders(Http3StreamedResponse response) {
        return ClientResponseHeaders.create(response.headers(), http3Client.clientConfig().mediaTypeParserMode());
    }

    private CompletableFuture<ClientResponseTrailers> responseTrailers(Http3StreamedResponse response,
                                                                       ClientResponseHeaders responseHeaders) {
        if (!responseHeaders.contains(HeaderNames.TRAILER)) {
            return CompletableFuture.failedFuture(new IllegalStateException("No trailers are expected."));
        }
        return response.trailers().thenApply(this::clientResponseTrailers);
    }

    private ClientResponseTrailers clientResponseTrailers(Headers trailers) {
        return ClientResponseTrailers.create(trailers);
    }

}
