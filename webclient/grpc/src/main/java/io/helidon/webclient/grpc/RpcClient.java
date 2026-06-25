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

package io.helidon.webclient.grpc;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.service.registry.Service;

/**
 * Declarative gRPC client annotations.
 */
public interface RpcClient {
    /**
     * Defines a declarative gRPC client endpoint.
     * <p>
     * Configuration options for gRPC clients (prefixed by {@link #configKey()}):
     * <table class="config">
     *    <caption>gRPC Client Configuration Options</caption>
     *    <tr>
     *      <th>Key</th>
     *      <th>Default Value</th>
     *      <th>Description</th>
     *    </tr>
     *    <tr>
     *      <th>{@code client}</th>
     *      <th>&nbsp;</th>
     *      <th>Backing {@link GrpcClient} configuration. The endpoint URI is applied after this configuration.</th>
     *    </tr>
     * </table>
     *
     * In case key {@code client} node exists under the configuration node of this API, a new client will be created for
     * this instance, using {@link #value()} as its base URI (this always wins).
     * In case the {@link #clientName()} is defined, and an instance of that name is available in registry, it will be
     * used for this instance. If the named client is not available, a new client will be created with {@link #value()}
     * as its base URI.
     * When {@link #clientName()} is not defined, we use an unnamed client instance from the registry (if any).
     * The last resort is to create a new client with {@link #value()} as its base URI.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @interface Endpoint {
        /**
         * Target URI of the generated backing gRPC client.
         * <p>
         * Supports configuration references, such as {@code "http://localhost:${server.port}"}.
         * This value is used as the base URI when the generated client creates a backing {@link GrpcClient}.
         * Registry-provided clients keep their own base URI.
         *
         * @return target URI
         */
        String value();

        /**
         * Configuration key base to use when looking up options for the backing gRPC client.
         *
         * @return configuration key prefix
         */
        String configKey() default "";

        /**
         * Name of a registry-provided {@link GrpcClient} to use.
         *
         * @return client name
         */
        String clientName() default "";
    }

    /**
     * Qualifier for generated declarative gRPC client implementations.
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
    @Documented
    @Service.Qualifier
    @interface Client {
    }
}
