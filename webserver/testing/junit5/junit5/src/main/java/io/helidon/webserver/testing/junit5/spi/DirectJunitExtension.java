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

package io.helidon.webserver.testing.junit5.spi;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import io.helidon.webserver.spi.ServerFeature;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

/**
 * Java {@link java.util.ServiceLoader} provider interface for extending unit tests with support for additional injection,
 * such as Direct HTTP/1.1 client.
 */
public interface DirectJunitExtension extends HelidonJunitExtension {
    /**
     * Resolve a parameter.
     *
     * @param parameterContext JUnit parameter context
     * @param extensionContext JUnit extension context
     * @param parameterType    type of parameter
     * @return instance of the parameter
     * @throws org.junit.jupiter.api.extension.ParameterResolutionException in case parameter cannot be resolved
     */
    default Object resolveParameter(ParameterContext parameterContext,
                                    ExtensionContext extensionContext,
                                    Class<?> parameterType) {
        throw new ParameterResolutionException("Cannot resolve parameter: " + parameterContext);
    }

    /**
     * Check if the type is supported and return a handler for it.
     *
     * @param features
     * @param type     type of the parameter to {@link io.helidon.webserver.testing.junit5.SetUpRoute} method
     * @return parameter handler if the type is supported, empty otherwise
     */
    default Optional<ParamHandler<?>> setUpRouteParamHandler(List<ServerFeature> features, Class<?> type) {
        return Optional.empty();
    }

    /**
     * Handler to provide an instance that can be injected as a parameter to
     * {@link io.helidon.webserver.testing.junit5.SetUpRoute} static methods.
     *
     * @param <T> type of the parameter (such as {@link io.helidon.webserver.http.HttpRouting.Builder}
     */
    interface ParamHandler<T> {
        /**
         * Get an instance to be injected.
         *
         * @param socketName name of a socket this will belong to
         * @return a new instance to inject as a parameter to the method
         */
        T get(String socketName);

        /**
         * Handle the value after the method has been called, and its body updated our provided instance.
         *
         * @param method method that updated the value
         * @param socketName socket name
         * @param value the value we provided with {@link #get(String)}
         */
        default void handle(Method method,
                            String socketName,
                            T value) {
        }
    }
}
