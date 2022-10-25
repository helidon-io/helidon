/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package org.openapitools.client.api;

import java.util.Objects;
import org.openapitools.client.ApiResponse;

import io.helidon.common.GenericType;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;

import org.openapitools.client.ApiClient;
import org.openapitools.client.model.Message;

/**
 * OpenAPI Helidon Quickstart
 *
 * <p>This is a sample for Helidon Quickstart project.
 */
public class MessageApiImpl implements MessageApi {

  private final ApiClient apiClient;

  protected static final GenericType<Message> RESPONSE_TYPE_getDefaultMessage = ResponseType.create(Message.class);
  protected static final GenericType<Message> RESPONSE_TYPE_getMessage = ResponseType.create(Message.class);
  protected static final GenericType<Void> RESPONSE_TYPE_updateGreeting = ResponseType.create(Void.class);

  /**
   * Creates a new instance of MessageApiImpl initialized with the specified {@link ApiClient}.
   *
   */
  public static MessageApiImpl create(ApiClient apiClient) {
    return new MessageApiImpl(apiClient);
  }

  protected MessageApiImpl(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  @Override
  public ApiResponse<Message> getDefaultMessage() {
    WebClientRequestBuilder webClientRequestBuilder = getDefaultMessageRequestBuilder();
    return getDefaultMessageSubmit(webClientRequestBuilder);
  }

  /**
   * Creates a {@code WebClientRequestBuilder} for the getDefaultMessage operation.
   * Optional customization point for subclasses.
   *
   * @return WebClientRequestBuilder for getDefaultMessage
   */
  protected WebClientRequestBuilder getDefaultMessageRequestBuilder() {
    WebClientRequestBuilder webClientRequestBuilder = apiClient.webClient()
            .method("GET");

    webClientRequestBuilder.path("/greet");
    webClientRequestBuilder.accept(MediaType.APPLICATION_JSON);

    return webClientRequestBuilder;
  }

  /**
   * Initiates the request for the getDefaultMessage operation.
   * Optional customization point for subclasses.
   *
   * @param webClientRequestBuilder the request builder to use for submitting the request
   * @return {@code ApiResponse<Message>} for the submitted request
   */
  protected ApiResponse<Message> getDefaultMessageSubmit(WebClientRequestBuilder webClientRequestBuilder) {
    Single<WebClientResponse> webClientResponse = webClientRequestBuilder.submit();
    return ApiResponse.create(RESPONSE_TYPE_getDefaultMessage, webClientResponse);
  }

  @Override
  public ApiResponse<Message> getMessage(String name) {
    Objects.requireNonNull(name, "Required parameter 'name' not specified");
    WebClientRequestBuilder webClientRequestBuilder = getMessageRequestBuilder(name);
    return getMessageSubmit(webClientRequestBuilder, name);
  }

  /**
   * Creates a {@code WebClientRequestBuilder} for the getMessage operation.
   * Optional customization point for subclasses.
   *
   * @param name the name to greet (required)
   * @return WebClientRequestBuilder for getMessage
   */
  protected WebClientRequestBuilder getMessageRequestBuilder(String name) {
    WebClientRequestBuilder webClientRequestBuilder = apiClient.webClient()
            .method("GET");

    String path = "/greet/{name}"
            .replace("{name}", ApiClient.urlEncode(name));
    webClientRequestBuilder.path(path);
    webClientRequestBuilder.accept(MediaType.APPLICATION_JSON);

    return webClientRequestBuilder;
  }

  /**
   * Initiates the request for the getMessage operation.
   * Optional customization point for subclasses.
   *
   * @param webClientRequestBuilder the request builder to use for submitting the request
   * @param name the name to greet (required)
   * @return {@code ApiResponse<Message>} for the submitted request
   */
  protected ApiResponse<Message> getMessageSubmit(WebClientRequestBuilder webClientRequestBuilder, String name) {
    Single<WebClientResponse> webClientResponse = webClientRequestBuilder.submit();
    return ApiResponse.create(RESPONSE_TYPE_getMessage, webClientResponse);
  }

  @Override
  public ApiResponse<Void> updateGreeting(Message message) {
    Objects.requireNonNull(message, "Required parameter 'message' not specified");
    WebClientRequestBuilder webClientRequestBuilder = updateGreetingRequestBuilder(message);
    return updateGreetingSubmit(webClientRequestBuilder, message);
  }

  /**
   * Creates a {@code WebClientRequestBuilder} for the updateGreeting operation.
   * Optional customization point for subclasses.
   *
   * @param message Message for the user (required)
   * @return WebClientRequestBuilder for updateGreeting
   */
  protected WebClientRequestBuilder updateGreetingRequestBuilder(Message message) {
    WebClientRequestBuilder webClientRequestBuilder = apiClient.webClient()
            .method("PUT");

    webClientRequestBuilder.path("/greet/greeting");
    webClientRequestBuilder.contentType(MediaType.APPLICATION_JSON);
    webClientRequestBuilder.accept(MediaType.APPLICATION_JSON);

    return webClientRequestBuilder;
  }

  /**
   * Initiates the request for the updateGreeting operation.
   * Optional customization point for subclasses.
   *
   * @param webClientRequestBuilder the request builder to use for submitting the request
   * @param message Message for the user (required)
   * @return {@code ApiResponse<Void>} for the submitted request
   */
  protected ApiResponse<Void> updateGreetingSubmit(WebClientRequestBuilder webClientRequestBuilder, Message message) {
    Single<WebClientResponse> webClientResponse = webClientRequestBuilder.submit(message);
    return ApiResponse.create(RESPONSE_TYPE_updateGreeting, webClientResponse);
  }
}
