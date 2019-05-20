/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package io.helidon.openapi;

import java.util.Objects;

import io.helidon.config.Config;
import io.helidon.openapi.internal.OpenAPIConfigImpl;

import io.smallrye.openapi.api.OpenApiConfig;
import org.jboss.jandex.IndexView;

/**
 * Builds {@link OpenAPISupport} in a Helidon SE environment.
 * <p>
 * The builder mostly delegates to an instance of Helidon's
 * {@link OpenAPIConfigImpl.Builder} which in turn prepares a smallrye
 * {@link OpenApiConfig} which is what the smallrye implementation uses to
 * control its behavior.
 */
public final class SEOpenAPISupportBuilder extends OpenAPISupport.Builder {

    private static final String CONFIG_PREFIX = "openapi";
    private final OpenAPIConfigImpl.Builder apiConfigBuilder = OpenAPIConfigImpl.builder();

    /**
     * Set various builder attributes from the specified {@code Config} object.
     * <p>
     * The {@code Config} object can specify {@value #CONFIG_PREFIX}.web-context
     * and {@value #CONFIG_PREFIX}.static-file in addition to settings
     * supported by {@link OpenAPIConfigImpl.Builder}.
     *
     * @param config the {@code Config} object possibly containing settings
     * @exception NullPointerException if the provided {@code Config} is null
     * @return updated builder instance
     */
    public SEOpenAPISupportBuilder helidonConfig(Config config) {
        config.get(CONFIG_PREFIX + ".web-context").asString().ifPresent(this::webContext);
        config.get(CONFIG_PREFIX + ".static-file").asString().ifPresent(this::staticFile);
        apiConfigBuilder.config(config);
        return this;
    }

    @Override
    public OpenApiConfig openAPIConfig() {
        return apiConfigBuilder.build();
    }

    @Override
    public IndexView indexView() {
        return null;
    }

    /**
     * Sets the app-provided model reader class.
     *
     * @param className name of the model reader class
     * @return updated builder instance
     */
    public SEOpenAPISupportBuilder modelReader(String className) {
        Objects.requireNonNull(className, "modelReader class name must be non-null");
        apiConfigBuilder.modelReader(className);
        return this;
    }

    /**
     * Set the app-provided OpenAPI model filter class.
     *
     * @param className name of the filter class
     * @return updated builder instance
     */
    public SEOpenAPISupportBuilder filter(String className) {
        Objects.requireNonNull(className, "filter class name must be non-null");
        apiConfigBuilder.filter(className);
        return this;
    }

    /**
     * Sets the servers which offer the endpoints in the OpenAPI document.
     *
     * @param serverList comma-separated list of servers
     * @return updated builder instance
     */
    public SEOpenAPISupportBuilder servers(String serverList) {
        Objects.requireNonNull(serverList, "serverList must be non-null");
        apiConfigBuilder.servers(serverList);
        return this;
    }

    /**
     * Adds an operation server for a given operation ID.
     *
     * @param operationID operation ID to which the server corresponds
     * @param operationServer name of the server to add for this operation
     * @return updated builder instance
     */
    public SEOpenAPISupportBuilder addOperationServer(String operationID, String operationServer) {
        Objects.requireNonNull(operationID, "operationID must be non-null");
        Objects.requireNonNull(operationServer, "operationServer must be non-null");
        apiConfigBuilder.addOperationServer(operationID, operationServer);
        return this;
    }

    /**
     * Adds a path server for a given path.
     *
     * @param path path to which the server corresponds
     * @param pathServer name of the server to add for this path
     * @return updated builder instance
     */
    public SEOpenAPISupportBuilder addPathServer(String path, String pathServer) {
        Objects.requireNonNull(path, "path must be non-null");
        Objects.requireNonNull(pathServer, "pathServer must be non-null");
        apiConfigBuilder.addPathServer(path, pathServer);
        return this;
    }
}
