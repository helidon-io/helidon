/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient;

import io.helidon.common.http.Http;

/**
 * HTTP client.
 *
 * @param <REQ> type of the client request
 * @param <RES> type of the client response
 */
public interface HttpClient<REQ extends ClientRequest<REQ, RES>, RES extends ClientResponse> extends WebClient {

    /**
     * Create a request for a method.
     *
     * @param method HTTP method
     * @return a new request (not thread safe)
     */
    REQ method(Http.Method method);

    /**
     * Shortcut for get method with a path.
     *
     * @param uri path to resolve against base URI, or full URI
     * @return a new request (not thread safe)
     */
    default REQ get(String uri) {
        return method(Http.Method.GET).uri(uri);
    }

    /**
     * Shortcut for get method with default path.
     *
     * @return a new request (not thread safe)
     */
    default REQ get() {
        return method(Http.Method.GET);
    }

    /**
     * Shortcut for post method with a path.
     *
     * @param uri path to resolve against base URI, or full URI
     * @return a new request (not thread safe)
     */
    default REQ post(String uri) {
        return method(Http.Method.POST).uri(uri);
    }

    /**
     * Shortcut for post method with default path.
     *
     * @return a new request (not thread safe)
     */
    default REQ post() {
        return method(Http.Method.POST);
    }

    /**
     * Shortcut for put method with a path.
     *
     * @param uri path to resolve against base URI, or full URI
     * @return a new request (not thread safe)
     */
    default REQ put(String uri) {
        return method(Http.Method.PUT).uri(uri);
    }

    /**
     * Shortcut for put method with default path.
     *
     * @return a new request (not thread safe)
     */
    default REQ put() {
        return method(Http.Method.PUT);
    }

    /**
     * Shortcut for delete method with a path.
     *
     * @param uri path to resolve against base URI, or full URI
     * @return a new request (not thread safe)
     */
    default REQ delete(String uri) {
        return method(Http.Method.DELETE).uri(uri);
    }

    /**
     * Shortcut for delete method with default path.
     *
     * @return a new request (not thread safe)
     */
    default REQ delete() {
        return method(Http.Method.DELETE);
    }

    /**
     * Shortcut for head method with a path.
     *
     * @param uri path to resolve against base URI, or full URI
     * @return a new request (not thread safe)
     */
    default REQ head(String uri) {
        return method(Http.Method.HEAD).uri(uri);
    }

    /**
     * Shortcut for head method with default path.
     *
     * @return a new request (not thread safe)
     */
    default REQ head() {
        return method(Http.Method.HEAD);
    }

    /**
     * Shortcut for options method with a path.
     *
     * @param uri path to resolve against base URI, or full URI
     * @return a new request (not thread safe)
     */
    default REQ options(String uri) {
        return method(Http.Method.OPTIONS).uri(uri);
    }

    /**
     * Shortcut for options method with default path.
     *
     * @return a new request (not thread safe)
     */
    default REQ options() {
        return method(Http.Method.OPTIONS);
    }

    /**
     * Shortcut for trace method with a path.
     *
     * @param uri path to resolve against base URI, or full URI
     * @return a new request (not thread safe)
     */
    default REQ trace(String uri) {
        return method(Http.Method.TRACE).uri(uri);
    }

    /**
     * Shortcut for trace method with default path.
     *
     * @return a new request (not thread safe)
     */
    default REQ trace() {
        return method(Http.Method.TRACE);
    }

    /**
     * Shortcut for patch method with a path.
     *
     * @param uri path to resolve against base URI, or full URI
     * @return a new request (not thread safe)
     */
    default REQ patch(String uri) {
        return method(Http.Method.PATCH).uri(uri);
    }

    /**
     * Shortcut for patch method with default path.
     *
     * @return a new request (not thread safe)
     */
    default REQ patch() {
        return method(Http.Method.PATCH);
    }
}
