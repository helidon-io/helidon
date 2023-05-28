/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.tracing;

import java.util.function.Function;

import io.helidon.tracing.jersey.TracingHelper;

import jakarta.ws.rs.container.ContainerRequestContext;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Tracing utilities.
 */
final class MpTracingHelper {
    private static final String NAME_PROVIDER_HTTP_PATH = "http-path";
    private static final String NAME_PROVIDER_CLASS_METHOD = "class-method";
    private static final String DEFAULT_NAME_PROVIDER = NAME_PROVIDER_CLASS_METHOD;
    private static final String PROPERTY_NAME_PROVIDER = "mp.opentracing.server.operation-name-provider";
    private static final String PROPERTY_ENABLED = "tracing.enabled";

    private final Function<ContainerRequestContext, String> nameFunction;
    private final boolean enabled;

    private MpTracingHelper(Function<ContainerRequestContext, String> nameFunction,
                            boolean enabled) {
        this.nameFunction = nameFunction;
        this.enabled = enabled;
    }

    static MpTracingHelper create() {
        Config config = ConfigProvider.getConfig();
        String nameProvider = config.getOptionalValue(PROPERTY_NAME_PROVIDER, String.class)
                .orElse(DEFAULT_NAME_PROVIDER);

        Function<ContainerRequestContext, String> nameFunction;
        switch (nameProvider) {
        case NAME_PROVIDER_HTTP_PATH:
            nameFunction = TracingHelper::httpPathMethodName;
            break;
        case NAME_PROVIDER_CLASS_METHOD:
        default:
            nameFunction = TracingHelper::classMethodName;
            break;
        }

        boolean enabled = config.getOptionalValue(PROPERTY_ENABLED, Boolean.class).orElse(true);

        return new MpTracingHelper(nameFunction, enabled);
    }

    String operationName(ContainerRequestContext requestContext) {
        return nameFunction.apply(requestContext);
    }

    public boolean tracingEnabled() {
        return enabled;
    }
}
