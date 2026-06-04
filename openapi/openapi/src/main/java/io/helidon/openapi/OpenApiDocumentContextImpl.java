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

package io.helidon.openapi;

import java.util.Map;
import java.util.Objects;

import io.helidon.config.Config;
import io.helidon.config.ConfigBuilderSupport;
import io.helidon.openapi.spi.OpenApiVersion;

final class OpenApiDocumentContextImpl implements OpenApiDocumentContext {
    private final String featureName;
    private final String webContext;
    private final String listener;
    private final OpenApiGeneratedMode generatedMode;
    private final OpenApiVersion openApiVersion;
    private final Config config;
    private final Map<String, String> operationIds;
    private final boolean resolveConfigExpressions;

    OpenApiDocumentContextImpl(String featureName,
                               String webContext,
                               String listener,
                               OpenApiGeneratedMode generatedMode,
                               OpenApiVersion openApiVersion) {
        this(featureName, webContext, listener, generatedMode, openApiVersion, Map.of());
    }

    OpenApiDocumentContextImpl(String featureName,
                               String webContext,
                               String listener,
                               OpenApiGeneratedMode generatedMode,
                               OpenApiVersion openApiVersion,
                               Config config) {
        this(featureName, webContext, listener, generatedMode, openApiVersion, config, Map.of());
    }

    OpenApiDocumentContextImpl(String featureName,
                               String webContext,
                               String listener,
                               OpenApiGeneratedMode generatedMode,
                               OpenApiVersion openApiVersion,
                               Map<String, String> operationIds) {
        this(featureName, webContext, listener, generatedMode, openApiVersion, Config.empty(), operationIds, false);
    }

    OpenApiDocumentContextImpl(String featureName,
                               String webContext,
                               String listener,
                               OpenApiGeneratedMode generatedMode,
                               OpenApiVersion openApiVersion,
                               Config config,
                               Map<String, String> operationIds) {
        this(featureName, webContext, listener, generatedMode, openApiVersion, config, operationIds, false);
    }

    OpenApiDocumentContextImpl(String featureName,
                               String webContext,
                               String listener,
                               OpenApiGeneratedMode generatedMode,
                               OpenApiVersion openApiVersion,
                               Config config,
                               Map<String, String> operationIds,
                               boolean resolveConfigExpressions) {
        this.featureName = Objects.requireNonNull(featureName);
        this.webContext = Objects.requireNonNull(webContext);
        this.listener = Objects.requireNonNull(listener);
        this.generatedMode = Objects.requireNonNull(generatedMode);
        this.openApiVersion = Objects.requireNonNull(openApiVersion);
        this.config = Objects.requireNonNull(config);
        this.operationIds = Map.copyOf(operationIds);
        this.resolveConfigExpressions = resolveConfigExpressions;
    }

    @Override
    public String featureName() {
        return featureName;
    }

    @Override
    public String webContext() {
        return webContext;
    }

    @Override
    public String listener() {
        return listener;
    }

    @Override
    public OpenApiGeneratedMode generatedMode() {
        return generatedMode;
    }

    @Override
    public OpenApiVersion openApiVersion() {
        return openApiVersion;
    }

    String operationId(String signature, String defaultOperationId) {
        String configured = operationIds.get(Objects.requireNonNull(signature));
        if (configured == null || configured.isBlank()) {
            return Objects.requireNonNull(defaultOperationId);
        }
        return configured;
    }

    String resolveExpression(String expression) {
        Objects.requireNonNull(expression);
        if (!resolveConfigExpressions) {
            return expression;
        }
        return ConfigBuilderSupport.resolveExpression(config, expression);
    }
}
