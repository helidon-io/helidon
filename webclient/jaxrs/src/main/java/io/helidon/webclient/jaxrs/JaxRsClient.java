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
 */
package io.helidon.webclient.jaxrs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;

/**
 * Point of access to {@link javax.ws.rs.client.ClientBuilder} to support Helidon features,
 * such as propagation of tracing, correct handling of {@link io.helidon.common.context.Context}.
 */
public final class JaxRsClient {
    private static final AtomicReference<Supplier<ExecutorService>> EXECUTOR_SUPPLIER =
            new AtomicReference<>(ThreadPoolSupplier.builder()
                                          .threadNamePrefix("helidon-jaxrs-client-")
                                          .build());

    private JaxRsClient() {
    }

    /**
     * Configure defaults for all clients created.
     * Configuration options:
     *
     * <table class="config">
     * <caption>Configuration parameters</caption>
     * <tr>
     *     <th>key</th>
     *     <th>default value</th>
     *     <th>description</th>
     * </tr>
     * <tr>
     *     <td>executor</td>
     *     <td>{@link io.helidon.common.configurable.ThreadPoolSupplier#create(io.helidon.config.Config)}</td>
     *     <td>Default executor service to use for asynchronous operations. For configuration options
     *      of {@code executor}, please refer to
     *      {@link io.helidon.common.configurable.ThreadPoolSupplier.Builder#config(io.helidon.config.Config)}</td>
     * </tr>
     * </table>
     *
     * @param config configuration to use to configure JAX-RS clients defaults
     */
    public static void configureDefaults(Config config) {
        EXECUTOR_SUPPLIER.set(ThreadPoolSupplier.create(config));
    }

    /**
     * Configure the default executor supplier to be used for asynchronous requests when explicit supplier is not
     * provided.
     *
     * @param executorServiceSupplier supplier that provides the executor service
     */
    public static void defaultExecutor(Supplier<ExecutorService> executorServiceSupplier) {
        Supplier<ExecutorService> wrapped = () -> Contexts.wrap(executorServiceSupplier.get());

        EXECUTOR_SUPPLIER.set(wrapped);
    }

    /**
     * The executor supplier configured as default.
     *
     * @return supplier of {@link java.util.concurrent.ExecutorService} to use for client
     * asynchronous operations
     */
    static Supplier<ExecutorService> executor() {
        return EXECUTOR_SUPPLIER.get();
    }
}
