/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.service.registry.Service;

/**
 * Annotations and APIs for type safe WebSocket clients.
 * <p>
 * The type safe WebSocket client is backed by Helidon {@link io.helidon.webclient.websocket.WsClient}.
 *
 * @deprecated this API is part of incubating features of Helidon. This API may change including backward incompatible changes
 *         and full removal. We welcome feedback for incubating features.
 */
@Deprecated
public final class WebSocketClient {
    private WebSocketClient() {
    }

    /**
     * Definition of the websocket client API. A class can be annotated with this annotation and use
     * WebSocket operations through annotations in {@link io.helidon.websocket.WebSocket}.
     * The class should also have a {@link io.helidon.http.Http.Path} annotation to specify the path on the server.
     * <p>
     * In case key {@code client} node exists under the configuration node of this API, a new client will be created for this
     * instance (this always wins).
     * In case the {@link #clientName()} is defined, and an instance of that name is available in registry, it will be used
     * for this instance.
     * Then we use an unnamed client instance from the registry (if any).
     * The last resort is to create a new client that would be used for this API.
     * <p>
     * <strong>Important: </strong> for each endpoint a class is generated that is named as {@code ClassNameFactory}.
     * If such a class already exists, there will be a name conflict, use {@link #factoryClassName()} to specify a custom
     * class name in such a case. The factory will always be in the same package as the annotated type.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    @Service.Qualifier
    @Service.Singleton
    public @interface Endpoint {
        /**
         * The base URI of this API.
         * <p>
         * Note that {@link io.helidon.http.Http.Path} annotation on the API is added to this value.
         * <p>
         * Note that if the provided client (either from service registry, or provided to the factory) has a base URI specified,
         * this value will be ignored, and only the configured path will be used.
         *
         * @return endpoint URI of the generated client
         */
        String value();

        /**
         * Configuration key base to use when looking up options for the generated client.
         *
         * @return configuration key prefix
         */
        String configKey() default "";

        /**
         * Name of a named instance of {@link io.helidon.webclient.websocket.WsClient} we attempt to get from registry.
         *
         * @return client name
         */
        String clientName() default "";

        /**
         * Class name of the generated factory, default to {@code ClassNameOfAnnotatedTypeFactory}, i.e. for a type named
         * {@code EchoClientEndpoint}, we would generate an `{@code EchoClientEndpointFactory}.
         *
         * @return custom class name for the generated endpoint factory
         */
        String factoryClassName() default "";
    }
}
