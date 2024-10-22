/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.System.Logger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import io.helidon.common.context.Contexts;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.faulttolerance.FtHandler;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.integrations.common.rest.ApiOptionalResponse.BuilderBase;
import io.helidon.tracing.SpanContext;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonWriterFactory;

/**
 * Base REST API implementation.
 * Each integration module is expected to have its own implementation of this class to handle system specific operations,
 * such as security, processing of headers etc.
 */
public abstract class RestApiBase implements RestApi {
    private static final System.Logger LOGGER = System.getLogger(RestApiBase.class.getName());
    private final WebClient webClient;
    private final FtHandler ftHandler;
    private final JsonBuilderFactory jsonBuilderFactory;
    private final JsonReaderFactory jsonReaderFactory;
    private final JsonWriterFactory jsonWriterFactory;

    /**
     * A new instance, requires a subclass of the {@link RestApi.Builder}.
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
    public <T extends ApiResponse> T invoke(Method method,
                                            String path,
                                            ApiRequest<?> request,
                                            ApiResponse.Builder<?, T> responseBuilder) {

        String requestId = requestId(request);
        LOGGER.log(Logger.Level.TRACE, () -> requestId + ": Invoking " + method + " on path " + path + " no entity expected.");

        HttpClientResponse response = ftHandler.invoke(responseSupplier(method, path, request, requestId));
        return handleResponse(path, request, method, requestId, response, responseBuilder);
    }

    @Override
    public <T extends ApiEntityResponse> T invokeWithResponse(Method method,
                                                              String path,
                                                              ApiRequest<?> request,
                                                              ApiEntityResponse.Builder<?, T, JsonObject> responseBuilder) {

        String requestId = requestId(request);
        LOGGER.log(Logger.Level.TRACE, () -> requestId + ": Invoking " + method + " on path " + path + " JSON entity expected.");

        Supplier<HttpClientResponse> responseSupplier = responseSupplier(method, path, request, requestId);

        HttpClientResponse response = ftHandler.invoke(responseSupplier);
        return handleJsonResponse(path, request, method, requestId, response, responseBuilder);
    }

    @Override
    public <T extends ApiResponse> T invokeBytesRequest(Method method,
                                                        String path,
                                                        ApiRequest<?> apiRequest,
                                                        InputStream is,
                                                        ApiResponse.Builder<?, T> responseBuilder) {

        String requestId = requestId(apiRequest);

        LOGGER.log(Logger.Level.TRACE, () -> requestId + ": Invoking " + method + " on path " + path + " with bytes request");

        HttpClientRequest request = webClient.method(method).path(path);
        addHeaders(request, apiRequest.headers());
        addQueryParams(request, apiRequest.queryParams());

        Supplier<HttpClientResponse> responseSupplier = requestBytesPayload(
                path,
                apiRequest,
                method,
                requestId,
                request,
                is);

        HttpClientResponse response = ftHandler.invoke(responseSupplier);
        return handleResponse(path, apiRequest, method, requestId, response, responseBuilder);
    }

    @Override
    public <R, T extends ApiOptionalResponse<R>> T invokeEntityResponse(Method method,
                                                                        String path,
                                                                        ApiRequest<?> request,
                                                                        BuilderBase<?, T, InputStream, R> responseBuilder) {

        String requestId = requestId(request);

        LOGGER.log(Logger.Level.TRACE,
                () -> requestId + ": Invoking " + method + " on path " + path + " with publisher response");

        request.responseMediaType(request.responseMediaType().orElse(MediaTypes.WILDCARD));
        HttpClientResponse response = ftHandler.invoke(responseSupplier(method, path, request, requestId));
        return handleEntityResponse(path, request, method, requestId, response, responseBuilder);
    }

    @Override
    public <R, T extends ApiOptionalResponse<R>> T invokeBytesResponse(Method method,
                                                                       String path,
                                                                       ApiRequest<?> request,
                                                                       BuilderBase<?, T, byte[], R> responseBuilder) {

        String requestId = requestId(request);

        LOGGER.log(Logger.Level.TRACE, () -> requestId + ": Invoking " + method + " on path " + path + " with bytes response");

        request.responseMediaType(request.responseMediaType().orElse(MediaTypes.WILDCARD));

        Supplier<HttpClientResponse> responseSupplier = responseSupplier(method, path, request, requestId);
        HttpClientResponse response = ftHandler.invoke(responseSupplier);
        return handleBytesResponse(path, request, method, requestId, response, responseBuilder);
    }

    @Override
    public <R, T extends ApiOptionalResponse<R>> T invokeOptional(Method method,
                                                                  String path,
                                                                  ApiRequest<?> request,
                                                                  BuilderBase<?, T, JsonObject, R> responseBuilder) {

        String requestId = requestId(request);

        LOGGER.log(Logger.Level.TRACE, () -> requestId + ": Invoking " + method + " on path " + path + " with optional response");

        HttpClientResponse response = ftHandler.invoke(responseSupplier(method, path, request, requestId));
        return handleOptionalJsonResponse(path, request, method, requestId, response, responseBuilder);
    }

    /**
     * Create a response supplier from the request.
     * This method checks if there is a payload, and prepares the supplier based on this information.
     *
     * @param method    HTTP method to invoke
     * @param path      path to invoke
     * @param request   request that may contain a JSON entity
     * @param requestId request ID to use for this request
     * @return supplier of response that is used with fault tolerance
     */
    protected Supplier<HttpClientResponse> responseSupplier(Method method,
                                                             String path,
                                                             ApiRequest<?> request,
                                                             String requestId) {

        HttpClientRequest requestBuilder = webClient.method(method).path(path);
        addHeaders(requestBuilder, request.headers());
        addQueryParams(requestBuilder, request.queryParams());
        Optional<JsonObject> payload = request.toJson(jsonBuilderFactory);

        Supplier<HttpClientResponse> responseSupplier;

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
     *
     * @param request     client request
     * @param queryParams query parameters
     */
    protected void addQueryParams(HttpClientRequest request, Map<String, List<String>> queryParams) {
        queryParams.forEach((name, values) -> {
            if (values.size() == 1) {
                request.queryParam(name, values.iterator().next());
            } else {
                request.queryParam(name, values.toArray(new String[0]));
            }
        });
    }

    /**
     * Add HTTP headers.
     *
     * @param request client request
     * @param headers headers to add
     */
    protected void addHeaders(HttpClientRequest request, Map<String, List<String>> headers) {
        request.headers(clientHeaders -> {
            headers.forEach((key, value) -> clientHeaders.set(HeaderNames.create(key), value));
        });
    }

    /**
     * Handle bytes response for optional bytes entity.
     * This method checks if this was a success and if the response should contain an entity.
     * For success, it returns a response using the provided response builder.
     *
     * @param path            requested path
     * @param request         API request
     * @param method          HTTP method
     * @param requestId       request ID
     * @param response        the web client response
     * @param responseBuilder builder to configure success response
     * @param <R>             type of the optional part of the response
     * @param <T>             type of the response
     * @return typed response
     * @throws ApiRestException if an error occurs
     */
    protected <R, T extends ApiOptionalResponse<R>> T handleBytesResponse(
            String path,
            ApiRequest<?> request,
            Method method,
            String requestId,
            HttpClientResponse response,
            BuilderBase<?, T, byte[], R> responseBuilder) {

        ResponseState statusKind = responseState(path, request, method, requestId, response);
        if (statusKind.success) {
            if (statusKind.entityExpected) {
                try {
                    return responseBuilder
                            .headers(response.headers())
                            .status(response.status())
                            .requestId(requestId)
                            .entity(response.inputStream().readAllBytes())
                            .build();
                } catch (IOException ex) {
                    throw readErrorFailedEntity(path, request, method, requestId, response, ex);
                }
            }
            return emptyResponse(path, request, method, requestId, response, responseBuilder);
        }
        throw responseError(path, request, method, requestId, response);
    }

    /**
     * Handle response for optional publisher entity.
     * This method checks if this was a success and if the response should contain an entity.
     * For success, it returns a response using the provided response builder.
     *
     * @param path            requested path
     * @param request         API request
     * @param method          HTTP method
     * @param requestId       request ID
     * @param response        the web client response
     * @param responseBuilder builder to configure success response
     * @param <R>             type of the optional part of the response
     * @param <T>             type of the response
     * @return typed response
     * @throws ApiRestException if an error occurs
     */
    protected <R, T extends ApiOptionalResponse<R>> T handleEntityResponse(
            String path,
            ApiRequest<?> request,
            Method method,
            String requestId,
            HttpClientResponse response,
            BuilderBase<?, T, InputStream, R> responseBuilder) {

        ResponseState statusKind = responseState(path, request, method, requestId, response);
        if (statusKind.success) {
            if (statusKind.entityExpected) {
                return responseBuilder
                        .headers(response.headers())
                        .status(response.status())
                        .requestId(requestId)
                        .entity(response.inputStream())
                        .build();
            } else {
                return emptyResponse(path, request, method, requestId, response, responseBuilder);
            }
        } else {
            throw responseError(path, request, method, requestId, response);
        }
    }

    /**
     * Provide information whether the response is a success response for requests with optional entity.
     *
     * @param path      requested path
     * @param request   API request
     * @param method    HTTP method
     * @param requestId request ID
     * @param status    returned HTTP status
     * @return {@code true} for success states, {@code false} for errors
     */
    @SuppressWarnings("unused")
    protected boolean isSuccess(String path,
                                ApiRequest<?> request,
                                Method method,
                                String requestId,
                                Status status) {
        if (status == Status.NOT_FOUND_404) {
            return true;
        }
        if (status == Status.NOT_MODIFIED_304) {
            return true;
        }

        Status.Family family = Status.Family.of(status.code());
        return switch (family) {
            // we do have not modified handled, we also follow redirects - so this is an error
            case REDIRECTION, CLIENT_ERROR, SERVER_ERROR -> false;
            default -> true;
        };
    }

    /**
     * This method is only called for methods that return an optional entity.
     * If a method (such as
     * {@link RestApi#invokeWithResponse(Method, String, ApiRequest, io.helidon.integrations.common.rest.ApiEntityResponse.Builder)})
     * receives a status that would not yield an entity (such as 404), it is automatically an error.
     * Also this method is never called for codes in the success family.
     *
     * @param path      requested path
     * @param request   API request
     * @param method    HTTP method
     * @param requestId request ID
     * @param status    returned HTTP status
     * @return {@code true} if entity is expected, {@code false} otherwise (such as for 404 status code)
     */
    @SuppressWarnings("unused")
    protected boolean isEntityExpected(String path,
                                       ApiRequest<?> request,
                                       Method method,
                                       String requestId,
                                       Status status) {
        Status.Family family = Status.Family.of(status.code());
        return switch (family) {
            // we do have not modified handled, we also follow redirects - so this is an error
            case REDIRECTION, CLIENT_ERROR, SERVER_ERROR -> false;
            default -> true;
        };
    }

    /**
     * Handle response for optional JSON entity.
     * This method checks if this was a success and if the response should contain an entity.
     * For success, it returns a response using the provided response builder.
     *
     * @param path            requested path
     * @param request         API request
     * @param method          HTTP method
     * @param requestId       request ID
     * @param response        the web client response
     * @param responseBuilder builder to configure success response
     * @param <R>             type of the optional part of the response
     * @param <T>             type of the response
     * @return typed response
     * @throws ApiRestException if an error occurs
     */
    protected <R, T extends ApiOptionalResponse<R>> T handleOptionalJsonResponse(
            String path,
            ApiRequest<?> request,
            Method method,
            String requestId,
            HttpClientResponse response,
            BuilderBase<?, T, JsonObject, R> responseBuilder) {

        ResponseState statusKind = responseState(path, request, method, requestId, response);
        if (statusKind.success) {
            if (statusKind.entityExpected) {
                LOGGER.log(Logger.Level.TRACE,
                        () -> requestId + ": " + method + " on path " + path + " returned " + response.status());
                if (response.headers().contentLength().orElse(-1L) == 0) {
                    // explicit content length set to 0
                    return emptyResponse(path, request, method, requestId, response, responseBuilder);
                } else {
                    try {
                        JsonObject entity = response.entity().as(JsonObject.class);
                        return jsonOkResponse(path, request, method, requestId, response, entity, responseBuilder);
                    } catch (Throwable ex) {
                        throw readErrorFailedEntity(path, request, method, requestId, response, ex);
                    }
                }
            }
            return emptyResponse(path, request, method, requestId, response, responseBuilder);
        }
        LOGGER.log(Logger.Level.TRACE, () -> requestId + ": " + method + " on path " + path + " failed " + response.status());
        throw responseError(path, request, method, requestId, response);
    }

    /**
     * Empty response, may be because of a {@link io.helidon.http.Status#NOT_FOUND_404}, or
     * some other status, such as {@link io.helidon.http.Status#NOT_MODIFIED_304}.
     *
     * @param path            requested path
     * @param request         original request
     * @param method          HTTP method
     * @param requestId       ID of the request
     * @param response        actual response where we do not expect an entity
     * @param responseBuilder builder to create a response instance
     * @param <T>             type of the response
     * @return typed response with no entity
     */
    @SuppressWarnings("unused")
    protected <T> T emptyResponse(String path,
                                  ApiRequest<?> request,
                                  Method method,
                                  String requestId,
                                  HttpClientResponse response,
                                  ResponseBuilder<?, T, ?> responseBuilder) {
        return responseBuilder.headers(response.headers())
                              .status(response.status())
                              .requestId(requestId)
                              .build();
    }

    /**
     * Builds the response using the response builder provided.
     * This is the last chance to update the response builder with system specific information.
     *
     * @param path            requested path
     * @param request         original request
     * @param method          HTTP method
     * @param requestId       ID of the request
     * @param response        actual response where we do not expect an entity
     * @param json            the JsonObject parsed from entity
     * @param responseBuilder builder to create a response instance
     * @param <T>             type of the response
     * @return typed response
     */
    @SuppressWarnings("unused")
    protected <T> T jsonOkResponse(String path,
                                   ApiRequest<?> request,
                                   Method method,
                                   String requestId,
                                   HttpClientResponse response,
                                   JsonObject json,
                                   ResponseBuilder<?, T, JsonObject> responseBuilder) {
        return responseBuilder.headers(response.headers())
                              .status(response.status())
                              .requestId(requestId)
                              .entity(json)
                              .build();
    }

    /**
     * Reads JsonObject from response entity and either calls the {@code jsonOkResponse}.
     *
     * @param path            requested path
     * @param request         original request
     * @param method          HTTP method
     * @param requestId       ID of the request
     * @param response        actual response where we do not expect an entity
     * @param responseBuilder builder to create a response instance
     * @param <T>             type of the response
     * @return typed response
     * @throws ApiRestException if an error occurs
     */
    protected <T extends ApiEntityResponse> T handleJsonResponse(
            String path,
            ApiRequest<?> request,
            Method method,
            String requestId,
            HttpClientResponse response,
            ApiEntityResponse.Builder<?, T, JsonObject> responseBuilder) {

        Status status = response.status();
        if (Status.Family.of(status.code()) == Status.Family.SUCCESSFUL) {
            LOGGER.log(Logger.Level.TRACE, () -> requestId + ": " + method + " on path " + path + " returned " + status);
            try {
                JsonObject entity = response.entity().as(JsonObject.class);
                return jsonOkResponse(path, request, method, requestId, response, entity, responseBuilder);
            } catch (Throwable ex) {
                throw readErrorFailedEntity(path, request, method, requestId, response, ex);
            }
        }
        LOGGER.log(Logger.Level.TRACE, () -> requestId + ": " + method + " on path " + path + " failed " + status);
        throw responseError(path, request, method, requestId, response);
    }

    /**
     * Handle response for a request not expecting an entity.
     *
     * @param path            requested path
     * @param request         original request
     * @param method          HTTP method
     * @param requestId       ID of the request
     * @param response        actual response where we do not expect an entity
     * @param responseBuilder builder to create a response instance
     * @param <T>             type of the response
     * @return typed response
     * @throws ApiRestException if an error occurs
     */
    protected <T extends ApiResponse> T handleResponse(String path,
                                                       ApiRequest<?> request,
                                                       Method method,
                                                       String requestId,
                                                       HttpClientResponse response,
                                                       ApiResponse.Builder<?, T> responseBuilder) {
        Status status = response.status();

        boolean success = (Status.Family.of(status.code()) == Status.Family.SUCCESSFUL);

        if (success) {
            LOGGER.log(Logger.Level.TRACE, () -> requestId + ": " + method + " on path " + path + " returned " + status);
            return noEntityOkResponse(path, request, method, requestId, response, responseBuilder);
        }
        LOGGER.log(Logger.Level.TRACE, () -> requestId + ": " + method + " on path " + path + " failed " + status);
        throw responseError(path, request, method, requestId, response);
    }

    /**
     * Create an {@link ApiRestException}.
     * This method attempts to read the response entity as a string, parse it into a JsonObject and
     * depending on result, calls methods to create a proper exception.
     *
     * @param path      requested path
     * @param request   original request
     * @param method    HTTP method
     * @param requestId ID of the request
     * @param response  actual response where we do not expect an entity
     * @return ApiException
     */
    protected ApiRestException responseError(String path,
                                             ApiRequest<?> request,
                                             Method method,
                                             String requestId,
                                             HttpClientResponse response) {
        if (response.headers().contentLength().orElse(-1L) == 0) {
            // explicitly no content
            return readError(path, request, method, requestId, response);
        }
        try {
            String entity = response.entity().as(String.class);
            try {
                JsonObject json = jsonReaderFactory.createReader(new StringReader(entity))
                                                   .readObject();
                return readError(path, request, method, requestId, response, json);
            } catch (Throwable ex) {
                return readError(path, request, method, requestId, response, entity);
            }
        } catch (Throwable ex) {
            return readErrorFailedEntity(path, request, method, requestId, response, ex);
        }
    }

    /**
     * Read error information when we failed to read response entity.
     *
     * @param path      requested path
     * @param request   original request
     * @param method    HTTP method
     * @param requestId ID of the request
     * @param response  actual response where we do not expect an entity
     * @param throwable throwable that caused this problem (such as parsing exception)
     * @return an ApiRestException
     */
    @SuppressWarnings("unused")
    protected ApiRestException readErrorFailedEntity(String path,
                                                     ApiRequest<?> request,
                                                     Method method,
                                                     String requestId,
                                                     HttpClientResponse response,
                                                     Throwable throwable) {
        return RestException.builder()
                            .cause(throwable)
                            .requestId(requestId)
                            .status(response.status())
                            .message("Failed to invoke %s on path %s %d, failed to read entity.",
                                    method, path, response.status().code())
                            .headers(response.headers())
                            .build();
    }

    /**
     * Read error with an entity that failed to be parsed into a JSON object.
     *
     * @param path      requested path
     * @param request   original API request
     * @param method    HTTP method
     * @param requestId request ID
     * @param response  web client response with entity consumed
     * @param entity    entity as a string
     * @return an ApiRestException
     */
    @SuppressWarnings("unused")
    protected ApiRestException readError(String path,
                                         ApiRequest<?> request,
                                         Method method,
                                         String requestId,
                                         HttpClientResponse response,
                                         String entity) {
        LOGGER.log(Logger.Level.TRACE, () -> requestId + ": request failed for path " + path + ", error response: " + entity);

        return RestException.builder()
                            .requestId(requestId)
                            .status(response.status())
                            .message("Failed to invoke %s on path %s %d", method, path, response.status().code())
                            .headers(response.headers())
                            .build();
    }

    /**
     * Read error with no entity (content length set to 0).
     *
     * @param path      requested path
     * @param request   original API request
     * @param method    HTTP method
     * @param requestId request ID
     * @param response  web client response with entity consumed
     * @return an ApiRestException
     */
    @SuppressWarnings("unused")
    protected ApiRestException readError(String path,
                                         ApiRequest<?> request,
                                         Method method,
                                         String requestId,
                                         HttpClientResponse response) {
        LOGGER.log(Logger.Level.TRACE, () -> requestId + ": request failed for path " + path);

        return RestException.builder()
                            .requestId(requestId)
                            .status(response.status())
                            .message("Failed to invoke %s on path %s %d", method, path, response.status().code())
                            .headers(response.headers())
                            .build();
    }

    /**
     * Read error with a JSON entity.
     *
     * @param path        requested path
     * @param request     original API request
     * @param method      HTTP method
     * @param requestId   request ID
     * @param response    web client response with entity consumed
     * @param errorObject entity as a JSON object
     * @return an ApiRestException
     */
    @SuppressWarnings("unused")
    protected ApiRestException readError(String path,
                                         ApiRequest<?> request,
                                         Method method,
                                         String requestId,
                                         HttpClientResponse response,
                                         JsonObject errorObject) {
        LOGGER.log(Logger.Level.TRACE, () -> requestId + ": request failed for path " + path + ", error object: " + errorObject);
        return RestException.builder()
                            .requestId(requestId)
                            .status(response.status())
                            .message("Failed to invoke %s on path %s %d", method, path, response.status().code())
                            .headers(response.headers())
                            .build();
    }

    /**
     * Create a response for no entity.
     * This method builds the response from builder.
     *
     * @param path            requested path
     * @param request         original API request
     * @param method          HTTP method
     * @param requestId       request ID
     * @param response        web client response with entity consumed
     * @param responseBuilder response builder
     * @param <T>             type of the response
     * @return typed response
     */
    @SuppressWarnings("unused")
    protected <T extends ApiResponse> T noEntityOkResponse(String path,
                                                           ApiRequest<?> request,
                                                           Method method,
                                                           String requestId,
                                                           HttpClientResponse response,
                                                           ApiResponse.Builder<?, T> responseBuilder) {
        return responseBuilder.headers(response.headers())
                              .status(response.status())
                              .requestId(requestId)
                              .build();
    }

    /**
     * Create a supplier for a response with JSON request.
     * Defaults to "{@code () -> clientRequest.submit(jsonObject)}".
     * Also configures content type and accept headers.
     *
     * @param path           path requested
     * @param request        API request
     * @param method         HTTP method
     * @param requestId      ID of this request
     * @param requestBuilder {@link HttpClientRequest} request builder
     * @param jsonObject     JSON object that should be sent as a request entity
     * @return supplier of a client response
     */
    protected Supplier<HttpClientResponse> requestJsonPayload(String path,
                                                               ApiRequest<?> request,
                                                               Method method,
                                                               String requestId,
                                                               HttpClientRequest requestBuilder,
                                                               JsonObject jsonObject) {
        AtomicBoolean updated = new AtomicBoolean();
        return () -> {
            // we should only update request builder once - if a retry is done, it should not be reset
            HttpClientRequest clientRequest = requestBuilder;
            if (updated.compareAndSet(false, true)) {
                MediaType mediaType = request.responseMediaType().orElse(MediaTypes.APPLICATION_JSON);
                clientRequest.accept(mediaType).contentType(mediaType);
                clientRequest = updateRequestBuilder(requestBuilder,
                        path,
                        request,
                        method,
                        requestId,
                        jsonObject);
            }
            return clientRequest.submit(jsonObject);
        };
    }

    /**
     * Create a supplier for a response with publisher request.
     * Defaults to "{@code () -> clientRequest.submit(publisher)}".
     * Also configures content type and accept headers.
     *
     * @param path           path requested
     * @param request        API request
     * @param method         HTTP method
     * @param requestId      ID of this request
     * @param requestBuilder {@link HttpClientRequest} request builder
     * @param is             entity input stream
     * @return supplier of a client response
     */
    protected Supplier<HttpClientResponse> requestBytesPayload(String path,
                                                                ApiRequest<?> request,
                                                                Method method,
                                                                String requestId,
                                                                HttpClientRequest requestBuilder,
                                                                InputStream is) {
        AtomicBoolean updated = new AtomicBoolean();
        return () -> {
            // we should only update request builder once - if a retry is done, it should not be reset
            HttpClientRequest clientRequest = requestBuilder;
            if (updated.compareAndSet(false, true)) {
                Optional<MediaType> mediaType = request.responseMediaType();
                clientRequest.accept(mediaType.orElse(MediaTypes.APPLICATION_JSON))
                             .contentType(mediaType.orElse(MediaTypes.APPLICATION_OCTET_STREAM));
                clientRequest = updateRequestBuilderBytesPayload(requestBuilder, path, request, method, requestId);
            }
            return clientRequest.submit(is);
        };
    }

    /**
     * Create a supplier for a response.
     * Defaults to {@code requestBuilder::request}.
     *
     * @param path           path requested
     * @param request        API request
     * @param method         HTTP method
     * @param requestId      ID of this request
     * @param requestBuilder {@link HttpClientRequest} request builder
     * @return supplier of a client response
     */
    protected Supplier<HttpClientResponse> requestPayload(String path,
                                                           ApiRequest<?> request,
                                                           Method method,
                                                           String requestId,
                                                           HttpClientRequest requestBuilder) {
        AtomicBoolean updated = new AtomicBoolean();
        return () -> {
            // we should only update request builder once - if a retry is done, it should not be reset
            HttpClientRequest clientRequest = requestBuilder;
            if (updated.compareAndSet(false, true)) {
                clientRequest = updateRequestBuilder(requestBuilder, path, request, method, requestId);
            }
            return clientRequest.request();
        };
    }

    /**
     * Update request builder with no request payload.
     * Default implementation does nothing.
     *
     * @param requestBuilder current request builder
     * @param path           path to be executed
     * @param request        API request
     * @param method         method
     * @param requestId      request ID
     * @return updated builder
     */
    protected HttpClientRequest updateRequestBuilder(HttpClientRequest requestBuilder,
                                                      String path,
                                                      ApiRequest<?> request,
                                                      Method method,
                                                      String requestId) {
        return updateRequestBuilderCommon(requestBuilder, path, request, method, requestId);
    }

    /**
     * Update request builder with publisher request payload.
     * Default implementation does nothing.
     *
     * @param requestBuilder current request builder
     * @param path           path to be executed
     * @param request        API request
     * @param method         method
     * @param requestId      request ID
     * @return updated builder
     */
    protected HttpClientRequest updateRequestBuilderBytesPayload(HttpClientRequest requestBuilder,
                                                                  String path,
                                                                  ApiRequest<?> request,
                                                                  Method method,
                                                                  String requestId) {
        return updateRequestBuilderCommon(requestBuilder, path, request, method, requestId);
    }

    /**
     * Update request builder with no request payload.
     * Default implementation does nothing.
     *
     * @param requestBuilder current request builder
     * @param path           path to be executed
     * @param request        API request
     * @param method         method
     * @param requestId      request ID
     * @param jsonObject     json object with the request
     * @return updated builder
     */
    @SuppressWarnings("unused")
    protected HttpClientRequest updateRequestBuilder(HttpClientRequest requestBuilder,
                                                      String path,
                                                      ApiRequest<?> request,
                                                      Method method,
                                                      String requestId,
                                                      JsonObject jsonObject) {
        return updateRequestBuilderCommon(requestBuilder, path, request, method, requestId);
    }

    /**
     * Update request builder used by all default implementation in {@link RestApiBase}.
     *
     * @param requestBuilder current request builder
     * @param path           path to be executed
     * @param request        API request
     * @param method         method
     * @param requestId      request ID
     * @return updated builder
     */
    @SuppressWarnings("unused")
    protected HttpClientRequest updateRequestBuilderCommon(HttpClientRequest requestBuilder,
                                                            String path,
                                                            ApiRequest<?> request,
                                                            Method method,
                                                            String requestId) {
        return requestBuilder;
    }

    /**
     * Attempts to find a good request id.
     * This looks in the following (sequentially):
     * <ul>
     *    <li>{@link ApiRestRequest#requestId()}</li>
     *    <li>Trace ID of the current span if one is present (obtained from {@link io.helidon.common.context.Context}</li>
     *    <li>Random UUID</li>
     * </ul>
     *
     * @param request request
     * @return request ID
     */
    protected String requestId(ApiRequest<?> request) {
        return request.requestId()
                      .or(() -> Contexts.context()
                                        .flatMap(it -> it.get(SpanContext.class))
                                        .map(SpanContext::traceId))
                      .orElseGet(UUID.randomUUID()::toString);
    }

    /**
     * WebClient to be used to invoke requests.
     *
     * @return web client
     */
    @SuppressWarnings("unused")
    protected WebClient webClient() {
        return webClient;
    }

    /**
     * Fault tolerance handler to use to invoke requests.
     *
     * @return fault tolerance handler
     */
    @SuppressWarnings("unused")
    protected FtHandler ftHandler() {
        return ftHandler;
    }

    /**
     * JSON builder factory.
     *
     * @return builder factory
     */
    @SuppressWarnings("unused")
    protected JsonBuilderFactory jsonBuilderFactory() {
        return jsonBuilderFactory;
    }

    /**
     * JSON reader factory.
     *
     * @return reader factory
     */
    @SuppressWarnings("unused")
    protected JsonReaderFactory jsonReaderFactory() {
        return jsonReaderFactory;
    }

    /**
     * JSON writer factory.
     *
     * @return writer factory
     */
    @SuppressWarnings("unused")
    protected JsonWriterFactory jsonWriterFactory() {
        return jsonWriterFactory;
    }

    private record ResponseState(boolean success, boolean entityExpected) {
    }

    private ResponseState responseState(String path,
                                        ApiRequest<?> request,
                                        Method method,
                                        String requestId,
                                        HttpClientResponse response) {

        Status status = response.status();
        boolean success = (Status.Family.of(status.code()) == Status.Family.SUCCESSFUL)
                || isSuccess(path, request, method, requestId, status);
        boolean isEntityExpected = (Status.Family.of(status.code()) == Status.Family.SUCCESSFUL)
                || isEntityExpected(path, request, method, requestId, status);
        return new ResponseState(success, isEntityExpected);
    }
}
