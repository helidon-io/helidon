/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.http.spi;

import io.helidon.webserver.http.ErrorHandler;

/**
 * Error handler provider to create a service using {@link io.helidon.service.registry.ServiceRegistry},
 * that provides an error handler.
 * <p>
 * Instances of this service discovered by service registry are only used when the whole server is started using it
 * (i.e. Helidon Declarative approach).
 * <p>
 * To manually register an error handle, please use
 * {@link io.helidon.webserver.http.HttpRouting.Builder#error(Class, io.helidon.webserver.http.ErrorHandler)}.
 *
 * @param <T> type of the exception handled by the handler
 */
public interface ErrorHandlerProvider<T extends Throwable> {
    /**
     * Type of the exception to handle.
     *
     * @return class of the exception
     */
    Class<T> errorType();

    /**
     * Create an error handler to handle the exception.
     *
     * @return a new error handler
     */
    ErrorHandler<T> create();
}
