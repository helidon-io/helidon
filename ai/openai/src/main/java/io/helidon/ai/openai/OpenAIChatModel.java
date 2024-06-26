/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.ai.openai;

import java.net.URI;
import java.util.Objects;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.ai.api.ChatModel;
import io.helidon.webclient.api.HttpClient;
import io.helidon.webclient.api.HttpClientResponse;

/**
 * Class that handles OpenAI communications specified in
 * <a href="https://platform.openai.com/docs/api-reference/chat/">Chat</a> .
 */
public class OpenAIChatModel implements ChatModel<OpenAIRequest, OpenAIResponse> {

    private final HttpClient<?> httpClient;
    private final URI uri;
    private final String apiKey;
    
    private OpenAIChatModel(HttpClient<?> httpClient, URI uri, String apiKey) {
        this.httpClient = httpClient;
        this.uri = uri;
        this.apiKey = apiKey;
    }

    @Override
    public OpenAIResponse call(OpenAIRequest in) {
        HttpClientResponse response = httpClient.post(uri.toString())
                                                .header(HeaderNames.CONTENT_TYPE, "application/json")
                                                .header(HeaderNames.AUTHORIZATION, "Bearer " + apiKey)
                                                .submit(in);
        if (response.status() != Status.OK_200) {
            throw new IllegalStateException("Invalid response " + response.status()
                + ": " + response.as(String.class));
        } else {
            return response.as(OpenAIResponse.class);
        }
    }

    /**
     * Create a builder.
     *
     * @return a new fluent API builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a OpenAIChatModel for the given API key and client.
     *
     * @param apiKey the OpenAI API key
     * @param httpClient the HTTP Client
     * @return a OpenAIChatModel default instance
     */
    public static OpenAIChatModel create(String apiKey, HttpClient<?> httpClient) {
        return builder().apiKey(apiKey).client(httpClient).build();
    }

    /**
     * Fluent API builder for {@link io.helidon.ia.openid.OpenAIChatModel}.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, OpenAIChatModel> {

        private static final String DEFAULT_OPENID_URL = "https://api.openai.com";
        private HttpClient<?> httpClient;
        private String url = DEFAULT_OPENID_URL;
        private String apiKey;

        private Builder() {
        }

        @Override
        public OpenAIChatModel build() {
            Objects.requireNonNull(apiKey, "OpenAI API key is mandatory.");
            Objects.requireNonNull(httpClient, "HttpClient is mandatory to make HTTP requests to OpenAI.");
            URI uri = URI.create(url + "/v1/chat/completions");
            return new OpenAIChatModel(httpClient, uri, apiKey);
        }

        /**
         * Configure http client for HTTP requests, so multiple instances of {@link OpenAIChatModel} can
         * re-use the same client for performance reasons.
         * The client will never be closed by {@link OpenAIChatModel}.
         *
         * @param httpClient the configured HTTP Client
         * @return updated builder instance
         */
        public Builder client(HttpClient<?> httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * The URL of OpenAI. It defaults to {@link #DEFAULT_OPENID_URL}.
         *
         * @param url of OpenAI
         * @return updated builder instance
         */
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * Configure the API key to authenticate with OpenAI.
         *
         * @param apiKey
         * @return updated builder instance
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }
    }

}
