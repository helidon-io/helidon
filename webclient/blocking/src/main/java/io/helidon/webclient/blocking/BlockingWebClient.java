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
package io.helidon.webclient.blocking;

import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;

/**
 * Base client which is used to perform requests.
 */
public class BlockingWebClient {

    private final WebClient webClient;


    private BlockingWebClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Create a new WebClient.
     *
     * @return client
     */
    public static BlockingWebClient create(WebClient webClient) {
        return new BlockingWebClient(webClient);
    }


    /**
     * Create a request builder for a put method.
     *
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder put() {
        return new BlockingWebClientRequestBuilderImpl(webClient.put());
    }

    /**
     * Create a request builder for a get method.
     *
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder get() {
        return new BlockingWebClientRequestBuilderImpl(webClient.get());
    }

    /**
     * Create a request builder for a post method.
     *
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder post() {
        return new BlockingWebClientRequestBuilderImpl(webClient.post());
    }

    /**
     * Create a request builder for a delete method.
     *
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder delete() {
        return new BlockingWebClientRequestBuilderImpl(webClient.delete());
    }

    /**
     * Create a request builder for a options method.
     *
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder options() {
        return new BlockingWebClientRequestBuilderImpl(webClient.options());
    }

    /**
     * Create a request builder for a trace method.
     *
     * @return client request builder
     */

    public BlockingWebClientRequestBuilder trace() {
        return new BlockingWebClientRequestBuilderImpl(webClient.trace());
    }

    /**
     * Create a request builder for a head method.
     *
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder head() {
        return new BlockingWebClientRequestBuilderImpl(webClient.head());
    }

    /**
     * Create a request builder for a method based on method parameter.
     *
     * @param method request method
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder method(String method) {
        return new BlockingWebClientRequestBuilderImpl(webClient.method(method));
    }

    /**
     * Create a request builder for a method based on method parameter.
     *
     * @param method request method
     * @return client request builder
     */
    public BlockingWebClientRequestBuilder method(Http.Method method) {
        return new BlockingWebClientRequestBuilderImpl(webClient.method(method));
    }

}
