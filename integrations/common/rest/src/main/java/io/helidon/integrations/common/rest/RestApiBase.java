/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.common.rest;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonReaderFactory;
import javax.json.JsonWriterFactory;

import io.helidon.common.context.Contexts;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Collector;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.faulttolerance.FtHandler;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientRequestHeaders;
import io.helidon.webclient.WebClientResponse;

import io.opentracing.SpanContext;

/**
 * Base REST API implementation.
 * Each integration module is expected to have its own implementation of this class to handle system specific operations,
 * such as security, processing of headers etc.
 */
public abstract class RestApiBase implements RestApi {
    private static final Logger LOGGER = Logger.getLogger(RestApiBase.class.getName());

    private final WebClient webClient;
    private final FtHandler ftHandler;
    private final JsonBuilderFactory jsonBuilderFactory;
    private final JsonReaderFactory jsonReaderFactory;
    private final JsonWriterFactory jsonWriterFactory;

    /**
     * A new instance, requires a subclass of the {@link io.helidon.integrations.common.rest.RestApi.Builder}.
     *
     * @param builder builder to set this instance from
     */
    protected RestApiBase(RestApi.Builder<?, ?> builder) {
        webClient = builder.webClient();
        ftHandler = builder.ftHandler();
        jsonBuilderFactory = builder.jsonBuilderFactory();
        jsonReaderFactory = builder.jsonReaderFactory();
        jsonWriterFactory = builder.jsonWriterFactory();
    }

    @Override
    public <T extends ApiResponse> Single<T>
    invoke(Http.RequestMethod method,
           String path,
           ApiRequest<?> request,
           ApiResponse.Builder<?, T> responseBuilder) {

        String requestId = requestId(request);
        LOGGER.finest(() -> requestId + ": Invoking " + method + " on path " + path + " no entity expected.");

        return ftHandler.invoke(responseSupplier(method, path, request, requestId))
                .flatMapSingle(response -> handleResponse(path, request, method, requestId, response, responseBuilder));
    }

    @Override
    public <T extends ApiEntityResponse> Single<T>
    invokeWithResponse(Http.RequestMethod method,
                       String path,
                       ApiRequest<?> request,
                       ApiEntityResponse.Builder<?, T, JsonObject> responseBuilder) {

        String requestId = requestId(request);
        LOGGER.finest(() -> requestId + ": Invoking " + method + " on path " + path + " JSON entity expected.");

        Supplier<Single<WebClientResponse>> responseSupplier = responseSupplier(method, path, request, requestId);

        return ftHandler.invoke(responseSupplier)
                .flatMapSingle(response -> handleJsonResponse(path, request, method, requestId, response, responseBuilder));
    }

    @Override
    public <T extends ApiResponse> Single<T> invokeBytesRequest(Http.RequestMethod method,
                                                                String path,
                                                                ApiRequest<?> request,
                                                                Flow.Publisher<DataChunk> byteRequest,
                                                                ApiResponse.Builder<?, T> responseBuilder) {

        String requestId = requestId(request);

        LOGGER.finest(() -> requestId + ": Invoking " + method + " on path " + path + " with bytes request");

        WebClientRequestBuilder requestBuilder = webClient.method(method)
                .path(path);
        addHeaders(requestBuilder, path, request, method, requestId);
        addQueryParams(requestBuilder, path, request, method, requestId);

        Supplier<Single<WebClientResponse>> responseSupplier = requestBytesPayload(path,
                                                                                   request,
                                                                                   method,
                                                                                   requestId,
                                                                                   requestBuilder,
                                                                                   byteRequest);

        return ftHandler.invoke(responseSupplier)
                .flatMapSingle(response -> handleResponse(path, request, method, requestId, response, responseBuilder));
    }

    @Override
    public <R, T extends ApiOptionalResponse<R>>
    Single<T> invokePublisherResponse(Http.RequestMethod method,
                                      String path,
                                      ApiRequest<?> request,
                                      ApiOptionalResponse.BuilderBase<?, T, Multi<DataChunk>, R> responseBuilder) {

        String requestId = requestId(request);

        LOGGER.finest(() -> requestId + ": Invoking " + method + " on path " + path + " with publisher response");

        request.responseMediaType(request.responseMediaType().orElse(MediaType.WILDCARD));

        Supplier<Single<WebClientResponse>> responseSupplier = responseSupplier(method, path, request, requestId);

        return ftHandler.invoke(responseSupplier)
                .flatMapSingle(response -> handlePublisherResponse(path, request, method, requestId, response, responseBuilder));
    }

    @Override
    public <R, T extends ApiOptionalResponse<R>>
    Single<T> invokeBytesResponse(Http.RequestMethod method,
                                  String path,
                                  ApiRequest<?> request,
                                  ApiOptionalResponse.BuilderBase<?, T, byte[], R> responseBuilder) {

        String requestId = requestId(request);

        LOGGER.finest(() -> requestId + ": Invoking " + method + " on path " + path + " with bytes response");

        request.responseMediaType(request.responseMediaType().orElse(MediaType.WILDCARD));

        Supplier<Single<WebClientResponse>> responseSupplier = responseSupplier(method, path, request, requestId);

        return ftHandler.invoke(responseSupplier)
                .flatMapSingle(response -> handleBytesResponse(path, request, method, requestId, response, responseBuilder));
    }

    @Override
    public <R, T extends ApiOptionalResponse<R>>
    Single<T> invokeOptional(Http.RequestMethod method,
                             String path,
                             ApiRequest<?> request,
                             ApiOptionalResponse.BuilderBase<?, T, JsonObject, R> responseBuilder) {

        String requestId = requestId(request);

        LOGGER.finest(() -> requestId + ": Invoking " + method + " on path " + path + " with optional response");

        return ftHandler.invoke(responseSupplier(method, path, request, requestId))
                .flatMapSingle(response -> handleOptionalJsonResponse(path,
                                                                      request,
                                                                      method,
                                                                      requestId,
                                                                      response,
                                                                      responseBuilder));
    }

    /**
     * Create a response supplier from the request.
     * This method checks if there is a payload, and prepares the supplier based on this information.
     *
     * @param method HTTP method to invoke
     * @param path path to invoke
     * @param request request that may contain a JSON entity
     * @param requestId request ID to use for this request
     * @return supplier of response that is used with fault tolerance
     */
    protected Supplier<Single<WebClientResponse>> responseSupplier(Http.RequestMethod method,
                                                                   String path,
                                                                   ApiRequest<?> request,
                                                                   String requestId) {
        WebClientRequestBuilder requestBuilder = webClient.method(method)
                .path(path);
        addHeaders(requestBuilder, path, request, method, requestId);
        addQueryParams(requestBuilder, path, request, method, requestId);
        Optional<JsonObject> payload = request.toJson(jsonBuilderFactory);

        Supplier<Single<WebClientResponse>> responseSupplier;

        // now let's update the request
        if (payload.isPresent()) {
            responseSupplier = requestJsonPayload(path, request, method, requestId, requestBuilder, payload.get());
        } else {
            responseSupplier = requestPayload(path, request, method, requestId, requestBuilder);
        }
        return responseSupplier;
    }

    /**
     * Add HTTP query parameters.
     * This method adds query parameter configured on the provided {@link io.helidon.integrations.common.rest.ApiRequest}.
     *
     * @param requestBuilder web client request builder to configure query parameters on
     * @param path requested path
     * @param request API request
     * @param method HTTP method
     * @param requestId request ID
     */
    protected void addQueryParams(WebClientRequestBuilder requestBuilder,
                                  String path,
                                  ApiRequest<?> request,
                                  Http.RequestMethod method,
                                  String requestId) {
        request.queryParams()
                .forEach((name, values) -> {
                    if (values.size() == 1) {
                        requestBuilder.queryParam(name, values.iterator().next());
                    } else {
                        requestBuilder.queryParam(name, values.toArray(new String[0]));
                    }
                });

    }

    /**
     * Add HTTP headers.
     * This method adds headers configured on the provided {@link io.helidon.integrations.common.rest.ApiRequest}.
     *
     * @param requestBuilder web client request builder to configure headers on
     * @param path requested path
     * @param request API request
     * @param method HTTP method
     * @param requestId request ID
     */
    protected void addHeaders(WebClientRequestBuilder requestBuilder,
                              String path,
                              ApiRequest<?> request,
                              Http.RequestMethod method,
                              String requestId) {
        WebClientRequestHeaders headers = requestBuilder.headers();
        request.headers().forEach(headers::add);
    }

    /**
     * Handle bytes response for optional bytes entity.
     * This method checks if this was a success and if the response should contain an entity.
     * For success, it returns a response using the provided response builder.
     * For failures, returns an error.
     *
     * @param path requested path
     * @param request API request
     * @param method HTTP method
     * @param requestId request ID
     * @param response the web client response
     * @param responseBuilder builder to configure success response
     * @param <R> type of the optional part of the response
     * @param <T> type of the response
     *
     * @return future with response
     */
    protected <R, T extends ApiOptionalResponse<R>>
    Single<T> handleBytesResponse(String path,
                                  ApiRequest<?> request,
                                  Http.RequestMethod method,
                                  String requestId,
                                  WebClientResponse response,
                                  ApiOptionalResponse.BuilderBase<?, T, byte[], R> responseBuilder) {

        Http.ResponseStatus status = response.status();
        boolean success = (Http.Status.Family.of(status.code()) == Http.ResponseStatus.Family.SUCCESSFUL)
                || isSuccess(path, request, method, requestId, status);
        boolean isEntityExpected = (Http.Status.Family.of(status.code()) == Http.ResponseStatus.Family.SUCCESSFUL)
                || isEntityExpected(path, request, method, requestId, status);

        if (success) {
            if (isEntityExpected) {
                return response.content()
                        .map(DataChunk::bytes)
                        .collect(new Collector<byte[], byte[]>() {
                            private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

                            @Override
                            public void collect(byte[] item) {
                                baos.writeBytes(item);
                            }

                            @Override
                            public byte[] value() {
                                return baos.toByteArray();
                            }
                        }).map(it -> responseBuilder
                                .headers(response.headers())
                                .status(status)
                                .requestId(requestId)
                                .entity(it)
                                .build());
            } else {
                return emptyResponse(path, request, method, requestId, response, responseBuilder);
            }
        } else {
            return errorResponse(path, request, method, requestId, response);
        }
    }

    /**
     * Handle response for optional publisher entity.
     * This method checks if this was a success and if the response should contain an entity.
     * For success, it returns a response using the provided response builder.
     * For failures, returns an error.
     *
     * @param path requested path
     * @param request API request
     * @param method HTTP method
     * @param requestId request ID
     * @param response the web client response
     * @param responseBuilder builder to configure success response
     * @param <R> type of the optional part of the response
     * @param <T> type of the response
     *
     * @return future with response
     */
    protected <R, T extends ApiOptionalResponse<R>>
    Single<T> handlePublisherResponse(String path,
                                      ApiRequest<?> request,
                                      Http.RequestMethod method,
                                      String requestId,
                                      WebClientResponse response,
                                      ApiOptionalResponse.BuilderBase<?, T, Multi<DataChunk>, R> responseBuilder) {

        Http.ResponseStatus status = response.status();

        boolean success = (Http.Status.Family.of(status.code()) == Http.ResponseStatus.Family.SUCCESSFUL)
                || isSuccess(path, request, method, requestId, status);
        boolean isEntityExpected = (Http.Status.Family.of(status.code()) == Http.ResponseStatus.Family.SUCCESSFUL)
                || isEntityExpected(path, request, method, requestId, status);

        if (success) {
            if (isEntityExpected) {
                return Single.just(responseBuilder
                                           .headers(response.headers())
                                           .status(status)
                                           .requestId(requestId)
                                           .entity(response.content())
                                           .build());
            } else {
                return emptyResponse(path, request, method, requestId, response, responseBuilder);
            }
        } else {
            return errorResponse(path, request, method, requestId, response);
        }
    }

    /**
     * Provide information whether the response is a success response for requests with optional entity.
     *
     * @param path requested path
     * @param request API request
     * @param method HTTP method
     * @param requestId request ID
     * @param status returned HTTP status
     * @return {@code true} for success states, {@code false} for errors
     */
    protected boolean isSuccess(String path,
                                ApiRequest<?> request,
                                Http.RequestMethod method,
                                String requestId,
                                Http.ResponseStatus status) {
        if (status == Http.Status.NOT_FOUND_404) {
            return true;
        }
        if (status == Http.Status.NOT_MODIFIED_304) {
            return true;
        }

        Http.ResponseStatus.Family family = Http.Status.Family.of(status.code());
        switch (family) {
        // we do have not modified handled, we also follow redirects - so this is an error
        case REDIRECTION:
        case CLIENT_ERROR:
        case SERVER_ERROR:
            return false;
        case OTHER:
        default:
            return true;
        }
    }

    /**
     * This method is only called for methods that return an optional entity.
     * If a method (such as
     * {@link RestApi#invokeWithResponse(io.helidon.common.http.Http.RequestMethod, String, ApiRequest, io.helidon.integrations.common.rest.ApiEntityResponse.Builder)})
     *  receives a status that would not yield an entity (such as 404), it is automatically an error.
     * Also this method is never called for codes in the success family.
     *
     * @param path requested path
     * @param request API request
     * @param method HTTP method
     * @param requestId request ID
     * @param status returned HTTP status
     *
     * @return {@code true} if entity is expected, {@code false} otherwise (such as for 404 status code)
     */
    protected boolean isEntityExpected(String path,
                                       ApiRequest<?> request,
                                       Http.RequestMethod method,
                                       String requestId,
                                       Http.ResponseStatus status) {
        Http.ResponseStatus.Family family = Http.Status.Family.of(status.code());
        switch (family) {
        // we do have not modified handled, we also follow redirects - so this is an error
        case REDIRECTION:
        case CLIENT_ERROR:
        case SERVER_ERROR:
            return false;
        case OTHER:
        default:
            return true;
        }
    }

    /**
     * Handle response for optional JSON entity.
     * This method checks if this was a success and if the response should contain an entity.
     * For success, it returns a response using the provided response builder.
     * For failures, returns an error.
     *
     * @param path requested path
     * @param request API request
     * @param method HTTP method
     * @param requestId request ID
     * @param response the web client response
     * @param responseBuilder builder to configure success response
     * @param <R> type of the optional part of the response
     * @param <T> type of the response
     *
     * @return future with response
     */
    protected <R, T extends ApiOptionalResponse<R>>
    Single<T> handleOptionalJsonResponse(String path,
                                         ApiRequest<?> request,
                                         Http.RequestMethod method,
                                         String requestId,
                                         WebClientResponse response,
                                         ApiOptionalResponse.BuilderBase<?, T, JsonObject, R> responseBuilder) {

        Http.ResponseStatus status = response.status();

        boolean success = (Http.Status.Family.of(status.code()) == Http.ResponseStatus.Family.SUCCESSFUL)
                || isSuccess(path, request, method, requestId, status);
        boolean isEntityExpected = (Http.Status.Family.of(status.code()) == Http.ResponseStatus.Family.SUCCESSFUL)
                || isEntityExpected(path, request, method, requestId, status);

        if (success) {
            if (isEntityExpected) {
                LOGGER.finest(() -> requestId + ": " + method + " on path " + path + " returned " + status);
                if (response.headers().contentLength().orElse(-1L) == 0) {
                    // explicit content length set to 0
                    return emptyResponse(path, request, method, requestId, response, responseBuilder);
                } else {
                    return response.content()
                            .as(JsonObject.class)
                            .map(json -> jsonOkResponse(path, request, method, requestId, response, json, responseBuilder))
                            .onErrorResumeWithSingle(it -> Single
                                    .error(readErrorFailedEntity(path, request, method, requestId, response, it)));
                }
            } else {
                return emptyResponse(path, request, method, requestId, response, responseBuilder);
            }
        } else {
            LOGGER.finest(() -> requestId + ": " + method + " on path " + path + " failed " + status);
            return errorResponse(path, request, method, requestId, response);
        }
    }

    /**
     * Empty response, may be because of a {@link Http.Status#NOT_FOUND_404}, or
     * some other status, such as {@link Http.Status#NOT_MODIFIED_304}.
     *
     * @param path requested path
     * @param request original request
     * @param method HTTP method
     * @param requestId ID of the request
     * @param response actual response where we do not expect an entity
     * @param responseBuilder builder to create a response instance
     * @param <T> type of the response
     *
     * @return typed response with no entity
     */
    protected <T> Single<T> emptyResponse(String path,
                                          ApiRequest<?> request,
                                          Http.RequestMethod method,
                                          String requestId,
                                          WebClientResponse response,
                                          ResponseBuilder<?, T, ?> responseBuilder) {
        return Single.just(responseBuilder
                                   .headers(response.headers())
                                   .status(response.status())
                                   .requestId(requestId)
                                   .build());
    }

    /**
     * Builds the response using the response builder provided.
     * This is the last chance to update the response builder with system specific information.
     *
     * @param path requested path
     * @param request original request
     * @param method HTTP method
     * @param requestId ID of the request
     * @param response actual response where we do not expect an entity
     * @param json the JsonObject parsed from entity
     * @param responseBuilder builder to create a response instance
     * @param <T> type of the response
     *
     * @return typed response
     */
    protected <T>
    T jsonOkResponse(String path,
                     ApiRequest<?> request,
                     Http.RequestMethod method,
                     String requestId,
                     WebClientResponse response,
                     JsonObject json,
                     ResponseBuilder<?, T, JsonObject> responseBuilder) {

        return responseBuilder
                .headers(response.headers())
                .status(response.status())
                .requestId(requestId)
                .entity(json)
                .build();
    }

    /**
     * Reads JsonObject from response entity and either calls the {@code jsonOkResponse} or {@code errorResponse} depending
     * on its success.
     *
     * @param path requested path
     * @param request original request
     * @param method HTTP method
     * @param requestId ID of the request
     * @param response actual response where we do not expect an entity
     * @param responseBuilder builder to create a response instance
     * @param <T> type of the response
     *
     * @return future with typed response
     */
    protected <T extends ApiEntityResponse> Single<T>
    handleJsonResponse(String path,
                       ApiRequest<?> request,
                       Http.RequestMethod method,
                       String requestId,
                       WebClientResponse response,
                       ApiEntityResponse.Builder<?, T, JsonObject> responseBuilder) {

        Http.ResponseStatus status = response.status();

        if (Http.Status.Family.of(status.code()) == Http.ResponseStatus.Family.SUCCESSFUL) {
            LOGGER.finest(() -> requestId + ": " + method + " on path " + path + " returned " + status);
            return response.content()
                    .as(JsonObject.class)
                    .map(json -> jsonOkResponse(path, request, method, requestId, response, json, responseBuilder))
                    .onErrorResumeWithSingle(it -> Single
                            .error(readErrorFailedEntity(path, request, method, requestId, response, it)));
        } else {
            LOGGER.finest(() -> requestId + ": " + method + " on path " + path + " failed " + status);
            return errorResponse(path, request, method, requestId, response);
        }
    }

    /**
     * Handle response for a request not expecting an entity.
     *
     * @param path requested path
     * @param request original request
     * @param method HTTP method
     * @param requestId ID of the request
     * @param response actual response where we do not expect an entity
     * @param responseBuilder builder to create a response instance
     * @param <T> type of the response
     *
     * @return future with typed response
     */
    protected <T extends ApiResponse> Single<T> handleResponse(String path,
                                                               ApiRequest<?> request,
                                                               Http.RequestMethod method,
                                                               String requestId,
                                                               WebClientResponse response,
                                                               ApiResponse.Builder<?, T> responseBuilder) {
        Http.ResponseStatus status = response.status();

        boolean success = (Http.Status.Family.of(status.code()) == Http.ResponseStatus.Family.SUCCESSFUL);

        if (success) {
            LOGGER.finest(() -> requestId + ": " + method + " on path " + path + " returned " + status);
            return noEntityOkResponse(path, request, method, requestId, response, responseBuilder);
        } else {
            LOGGER.finest(() -> requestId + ": " + method + " on path " + path + " failed " + status);
            return errorResponse(path, request, method, requestId, response);
        }
    }

    /**
     * Create an error response.
     * This method attempts to read the response entity as a string, parse it into a JsonObject and
     * depending on result, calls methods to create a proper exception.
     *
     * @param path requested path
     * @param request original request
     * @param method HTTP method
     * @param requestId ID of the request
     * @param response actual response where we do not expect an entity
     * @param <T> type of the response
     *
     * @return future with error
     */
    protected <T extends ApiResponse> Single<T> errorResponse(String path,
                                                              ApiRequest<?> request,
                                                              Http.RequestMethod method,
                                                              String requestId,
                                                              WebClientResponse response) {

        if (response.headers().contentLength().orElse(-1L) == 0) {
            // explicitly no content
            return Single.error(readError(path, request, method, requestId, response));
        } else {
            AtomicBoolean processedError = new AtomicBoolean();

            return response.content()
                    .as(String.class)
                    .flatMapSingle(string -> {
                        try {
                            JsonObject json = jsonReaderFactory.createReader(new StringReader(string))
                                    .readObject();
                            Single<T> error = Single.error(readError(path, request, method, requestId, response, json));
                            processedError.set(true);
                            return error;
                        } catch (Exception e) {
                            Single<T> error = Single.error(readError(path, request, method, requestId, response, string));
                            processedError.set(true);
                            return error;
                        }
                    })
                    .onErrorResumeWithSingle(it -> {
                        if (processedError.get()) {
                            return Single.error(it);
                        }
                        return Single
                                .error(readErrorFailedEntity(path, request, method, requestId, response, it));
                    });
        }
    }

    /**
     * Read error information when we failed to read resposen entity.
     *
     * @param path requested path
     * @param request original request
     * @param method HTTP method
     * @param requestId ID of the request
     * @param response actual response where we do not expect an entity
     * @param throwable throwable that caused this problem (such as parsing exception)
     *
     * @return throwable to be used in response
     */
    protected Throwable readErrorFailedEntity(String path,
                                              ApiRequest<?> request,
                                              Http.RequestMethod method,
                                              String requestId,
                                              WebClientResponse response,
                                              Throwable throwable) {
        return RestException.builder()
                .cause(throwable)
                .requestId(requestId)
                .status(response.status())
                .message("Failed to invoke " + method + " on path " + path
                                 + " " + response.status().code() + ", failed to read entity.")
                .headers(response.headers())
                .build();
    }

    /**
     * Read error with an entity that failed to be parsed into a JSON object.
     *
     * @param path requested path
     * @param request original API request
     * @param method HTTP method
     * @param requestId request ID
     * @param response web client response with entity consumed
     * @param entity entity as a string
     *
     * @return a throwable to be used in response
     */
    protected Throwable readError(String path,
                                  ApiRequest<?> request,
                                  Http.RequestMethod method,
                                  String requestId,
                                  WebClientResponse response,
                                  String entity) {
        LOGGER.finest(() -> requestId + ": request failed for path " + path + ", error response: " + entity);

        return RestException.builder()
                .requestId(requestId)
                .status(response.status())
                .message("Failed to invoke " + method + " on path " + path
                                 + " " + response.status().code())
                .headers(response.headers())
                .build();
    }

    /**
     * Read error with no entity (content length set to 0).
     *
     * @param path requested path
     * @param request original API request
     * @param method HTTP method
     * @param requestId request ID
     * @param response web client response with entity consumed
     *
     * @return a throwable to be used in response
     */
    protected Throwable readError(String path,
                                  ApiRequest<?> request,
                                  Http.RequestMethod method,
                                  String requestId,
                                  WebClientResponse response) {

        LOGGER.finest(() -> requestId + ": request failed for path " + path);

        String messagePrefix = "Failed to invoke " + method + " on path " + path
                + " " + response.status().code();
        return RestException.builder()
                .requestId(requestId)
                .status(response.status())
                .message(messagePrefix)
                .headers(response.headers())
                .build();
    }

    /**
     * Read error with a JSON entity.
     *
     * @param path requested path
     * @param request original API request
     * @param method HTTP method
     * @param requestId request ID
     * @param response web client response with entity consumed
     * @param errorObject entity as a JSON object
     * @return a throwable to be used in response
     */
    protected Throwable readError(String path,
                                  ApiRequest<?> request,
                                  Http.RequestMethod method,
                                  String requestId,
                                  WebClientResponse response,
                                  JsonObject errorObject) {

        LOGGER.finest(() -> requestId + ": request failed for path " + path + ", error object: " + errorObject);

        String messagePrefix = "Failed to invoke " + method + " on path " + path
                + " " + response.status().code();
        return RestException.builder()
                .requestId(requestId)
                .status(response.status())
                .message(messagePrefix)
                .headers(response.headers())
                .build();
    }

    /**
     * Create a response for no entity.
     * This method builds the response from builder.
     *
     * @param path requested path
     * @param request original API request
     * @param method HTTP method
     * @param requestId request ID
     * @param response web client response with entity consumed
     * @param responseBuilder response builder
     * @param <T> type of the response
     *
     * @return future with the typed response
     */
    protected <T extends ApiResponse> Single<T> noEntityOkResponse(String path,
                                                                   ApiRequest<?> request,
                                                                   Http.RequestMethod method,
                                                                   String requestId,
                                                                   WebClientResponse response,
                                                                   ApiResponse.Builder<?, T> responseBuilder) {
        return Single.just(responseBuilder.headers(response.headers())
                                   .status(response.status())
                                   .requestId(requestId)
                                   .build());
    }

    /**
     * Create a supplier for a response with JSON request.
     * Defaults to "{@code () -> clientRequest.submit(jsonObject)}".
     * Also configures content type and accept headers.
     *
     * @param path path requested
     * @param request API request
     * @param method HTTP method
     * @param requestId ID of this request
     * @param requestBuilder {@link io.helidon.webclient.WebClient} request builder
     * @param jsonObject JSON object that should be sent as a request entity
     *
     * @return supplier of a web client response
     */
    protected Supplier<Single<WebClientResponse>> requestJsonPayload(String path,
                                                                     ApiRequest<?> request,
                                                                     Http.RequestMethod method,
                                                                     String requestId,
                                                                     WebClientRequestBuilder requestBuilder,
                                                                     JsonObject jsonObject) {

        requestBuilder.accept(request.responseMediaType().orElse(MediaType.APPLICATION_JSON));
        requestBuilder.contentType(request.requestMediaType().orElse(MediaType.APPLICATION_JSON));

        return () -> updateRequestBuilder(requestBuilder, path, request, method, requestId, jsonObject)
                .flatMapSingle(it -> it.submit(jsonObject));
    }

    /**
     * Create a supplier for a response with publisher request.
     * Defaults to "{@code () -> clientRequest.submit(publisher)}".
     * Also configures content type and accept headers.
     *
     * @param path path requested
     * @param request API request
     * @param method HTTP method
     * @param requestId ID of this request
     * @param requestBuilder {@link io.helidon.webclient.WebClient} request builder
     * @param publisher publisher to be used as request entity
     *
     * @return supplier of a web client response
     */
    protected Supplier<Single<WebClientResponse>> requestBytesPayload(String path,
                                                                      ApiRequest<?> request,
                                                                      Http.RequestMethod method,
                                                                      String requestId,
                                                                      WebClientRequestBuilder requestBuilder,
                                                                      Flow.Publisher<DataChunk> publisher) {
        requestBuilder.accept(request.responseMediaType().orElse(MediaType.APPLICATION_JSON));
        requestBuilder.contentType(request.requestMediaType().orElse(MediaType.APPLICATION_OCTET_STREAM));

        return () -> updateRequestBuilderBytesPayload(requestBuilder, path, request, method, requestId)
                .flatMapSingle(it -> it.submit(publisher));
    }

    /**
     * Create a supplier for a response.
     * Defaults to {@code requestBuilder::request}.
     *
     * @param path path requested
     * @param request API request
     * @param method HTTP method
     * @param requestId ID of this request
     * @param requestBuilder {@link io.helidon.webclient.WebClient} request builder
     * @return supplier of a web client response
     */
    protected Supplier<Single<WebClientResponse>> requestPayload(String path,
                                                                 ApiRequest<?> request,
                                                                 Http.RequestMethod method,
                                                                 String requestId,
                                                                 WebClientRequestBuilder requestBuilder) {

        return () -> updateRequestBuilder(requestBuilder,
                                          path,
                                          request,
                                          method,
                                          requestId)
                .flatMapSingle(WebClientRequestBuilder::request);
    }

    /**
     * Update request builder with no request payload.
     * Default implementation does nothing.
     *
     * @param requestBuilder current request builder
     * @param path path to be executed
     * @param request API request
     * @param method method
     * @param requestId request ID
     * @return updated builder
     */
    protected Single<WebClientRequestBuilder> updateRequestBuilder(WebClientRequestBuilder requestBuilder,
                                                                   String path,
                                                                   ApiRequest<?> request,
                                                                   Http.RequestMethod method,
                                                                   String requestId) {
        return updateRequestBuilderCommon(requestBuilder, path, request, method, requestId);
    }

    /**
     * Update request builder with publisher request payload.
     * Default implementation does nothing.
     *
     * @param requestBuilder current request builder
     * @param path path to be executed
     * @param request API request
     * @param method method
     * @param requestId request ID
     * @return updated builder
     */
    protected Single<WebClientRequestBuilder> updateRequestBuilderBytesPayload(WebClientRequestBuilder requestBuilder,
                                                                               String path,
                                                                               ApiRequest<?> request,
                                                                               Http.RequestMethod method,
                                                                               String requestId) {
        return updateRequestBuilderCommon(requestBuilder, path, request, method, requestId);
    }

    /**
     * Update request builder with no request payload.
     * Default implementation does nothing.
     *
     * @param requestBuilder current request builder
     * @param path path to be executed
     * @param request API request
     * @param method method
     * @param requestId request ID
     * @param jsonObject json object with the request
     * @return updated builder
     */
    protected Single<WebClientRequestBuilder> updateRequestBuilder(WebClientRequestBuilder requestBuilder,
                                                                   String path,
                                                                   ApiRequest<?> request,
                                                                   Http.RequestMethod method,
                                                                   String requestId,
                                                                   JsonObject jsonObject) {
        return updateRequestBuilderCommon(requestBuilder, path, request, method, requestId);
    }

    /**
     * Update request builder used by all default implementation in {@link io.helidon.integrations.common.rest.RestApiBase}.
     *
     * @param requestBuilder current request builder
     * @param path path to be executed
     * @param request API request
     * @param method method
     * @param requestId request ID
     * @return updated builder
     */
    protected Single<WebClientRequestBuilder> updateRequestBuilderCommon(WebClientRequestBuilder requestBuilder,
                                                                         String path,
                                                                         ApiRequest<?> request,
                                                                         Http.RequestMethod method,
                                                                         String requestId) {
        return Single.just(requestBuilder);
    }

    /**
     * Attempts to find a good request id.
     * This looks in the following (sequentially):
     * <ul>
     *    <li>{@link ApiRestRequest#requestId()}</li>
     *    <li>Trace ID of the current span if one is present (obtained from {@link io.helidon.common.context.Context}</li>
     *    <li>Random UUID</li>
     * </ul>
     * @param restRequest request
     * @return request ID
     */
    protected String requestId(ApiRequest<?> restRequest) {
        return restRequest.requestId()
                .or(() -> Contexts.context()
                        .flatMap(it -> it.get(SpanContext.class))
                        .map(SpanContext::toTraceId))
                .orElseGet(UUID.randomUUID()::toString);
    }

    /**
     * WebClient to be used to invoke requests.
     *
     * @return web client
     */
    protected WebClient webClient() {
        return webClient;
    }

    /**
     * Fault tolerance handler to use to invoke requests.
     *
     * @return fault tolerance handler
     */
    protected FtHandler ftHandler() {
        return ftHandler;
    }

    /**
     * JSON builder factory.
     *
     * @return builder factory
     */
    protected JsonBuilderFactory jsonBuilderFactory() {
        return jsonBuilderFactory;
    }

    /**
     * JSON reader factory.
     *
     * @return reader factory
     */
    protected JsonReaderFactory jsonReaderFactory() {
        return jsonReaderFactory;
    }

    /**
     * JSON writer factory.
     *
     * @return writer factory
     */
    protected JsonWriterFactory jsonWriterFactory() {
        return jsonWriterFactory;
    }
}
