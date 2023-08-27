/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.websocket;

import java.net.URI;
import java.util.Set;

import io.helidon.common.uri.UriInfo;
import io.helidon.webclient.api.ClientUri;

/**
 * URI abstraction for WsClient.
 */
public class WsClientUri extends ClientUri {

    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https", "ws", "wss");

    private WsClientUri() {
        super();
    }

    private WsClientUri(ClientUri baseUri) {
        super(baseUri);
    }

    private WsClientUri(UriInfo baseUri) {
        super(baseUri);
    }

    /**
     * Create an empty URI helper.
     *
     * @return uri helper
     */
    public static WsClientUri create() {
        return new WsClientUri();
    }

    /**
     * Create a new client uri.
     *
     * @param baseUri base URI
     * @return a new client uri
     */
    public static WsClientUri create(ClientUri baseUri) {
        return new WsClientUri(baseUri);
    }

    /**
     * Create a new client uri.
     *
     * @param baseUri base URI
     * @return a new client uri
     */
    public static WsClientUri create(UriInfo baseUri) {
        return new WsClientUri(baseUri);
    }

    /**
     * Create a new client URI from an existing URI.
     *
     * @param baseUri base URI
     * @return a new client uri
     */
    public static ClientUri create(URI baseUri) {
        return create().resolve(baseUri);
    }

    @Override
    public WsClientUri copy() {
        return WsClientUri.create(this);
    }

    @Override
    protected Set<String> supportedSchemes() {
        return SUPPORTED_SCHEMES;
    }
}
