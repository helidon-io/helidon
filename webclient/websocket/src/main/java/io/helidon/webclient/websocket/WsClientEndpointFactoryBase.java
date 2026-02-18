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

package io.helidon.webclient.websocket;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigBuilderSupport;
import io.helidon.service.registry.Service;
import io.helidon.websocket.WsListener;

/**
 * This class is intended for generated code, and we do not expect this to be used directly by users.
 * <p>
 * For each declarative WebSocket client a factory is generated to allow connecting to the remote server(s).
 * The factory is named by the endpoint class + "Factory" suffix.
 * <p>
 * In case there are path parameters in the configured {@link io.helidon.http.Http.Path}, they can either be provided
 * in a map in {@link #connect(java.util.Map)}, or a method will be generated with all path parameters typed according
 * to usage in the endpoint, or as Strings if not used. The generated method name is also {@code connect}.
 */
@Service.Contract
public abstract class WsClientEndpointFactoryBase {
    /*
    Used from generated code
     */

    private final String uri;
    private final String path;
    private final Set<String> pathParamNames;

    /**
     * Create a new factory.
     *
     * @param config configuration (root) to use to set this factory up
     * @param uri base URI of this endpoint (in declarative, this would be the
     *              {@link io.helidon.webclient.websocket.WebSocketClient.Endpoint#value()})
     * @param path HTTP path, may contain path parameters (in declarative, this would be the
     *             {@link io.helidon.http.Http.Path#value()}
     * @param pathParamNames names of path parameters present in {@code path}, to avoid runtime parsing
     */
    protected WsClientEndpointFactoryBase(Config config, String uri, String path, Set<String> pathParamNames) {
        var tmpUri = ConfigBuilderSupport.resolveExpression(config, uri);
        boolean uriEndSlash = uri.endsWith("/");
        boolean pathStartSlash = path.startsWith("/");
        if (uriEndSlash) {
            if (pathStartSlash) {
                this.uri = tmpUri + path.substring(1);
            } else {
                this.uri = tmpUri + path;
            }
        } else {
            if (pathStartSlash) {
                this.uri = tmpUri + path;
            } else {
                this.uri = tmpUri + "/" + path;
            }
        }
        if (pathStartSlash) {
            this.path = path;
        } else {
            this.path = "/" + path;
        }
        this.pathParamNames = pathParamNames;
    }

    /**
     * Connect to the WebSocket endpoint.
     *
     * @param pathParameters if the configured path has path parameters, these must be provided in the map
     */
    public void connect(Map<String, String> pathParameters) {
        connect(client(), pathParameters);
    }

    /**
     * Connect to the WebSocket endpoint using a custom client.
     *
     * @param wsClient       client to use
     * @param pathParameters if the configured path has path parameters, these must be provided in the map
     */
    public void connect(WsClient wsClient, Map<String, String> pathParameters) {
        for (String pathParamName : pathParamNames) {
            if (!pathParameters.containsKey(pathParamName)) {
                throw new IllegalArgumentException("Path parameter '" + pathParamName + "' is missing");
            }
        }
        doConnect(wsClient, pathParameters);
    }

    /**
     * The client used by this factory. This may be from service registry, or a new client.
     * A custom client can be specified by calling {@link #connect(WsClient, java.util.Map)},
     * or in generated factories a method that has typed path parameters.
     *
     * @return WebSocket client used by this factory
     */
    public abstract WsClient client();

    /**
     * Path parameter names configured in the {@link io.helidon.http.Http.Path}.
     *
     * @return path parameter names
     */
    public Set<String> pathParameterNames() {
        return pathParamNames;
    }

    /**
     * Implement this method to correctly type path parameters provided by a user.
     * The implementation is expected to call {@link #doConnect(WsClient, java.util.Map, java.util.function.Supplier)}.
     *
     * @param wsClient client to use
     * @param pathParameters provided path parameters, already validate that they contain all path parameters
     */
    protected abstract void doConnect(WsClient wsClient, Map<String, String> pathParameters);

    /**
     * Connects using the provided client. If the client has a base URI specified, {@code uri} is ignored, and only
     * HTTP path will be used.
     *
     * @param client client to use
     * @param pathParameters path parameter map
     * @param listener listener supplier
     */
    protected void doConnect(WsClient client, Map<String, String> pathParameters, Supplier<WsListener> listener) {
        String uri;
        if (client.prototype().baseUri().isPresent()) {
            uri = applyPathParams(this.path, pathParameters);
        } else {
            uri = applyPathParams(this.uri, pathParameters);
        }
        client.connect(uri, listener.get());
    }


    private String applyPathParams(String text, Map<String, String> pathParameters) {
        String result = text;
        for (var entry : pathParameters.entrySet()) {
            result = result.replaceAll("\\{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
