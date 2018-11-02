/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.webserver.synchronous;

import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;

/**
 * Web server service introducing support for synchronous operation from webserver scope.
 * This class is used to configure the executor service used for synchronous operation and to be registered in routing.
 * <p>
 * Register an instance of this class with webserver:
 * <pre>
 * Routing.builder()
 *   .register(SyncSupport.create())
 * ...
 * </pre>
 * Then to execute a synchronous operation, use static methods on {@link Sync} class.
 *
 * @see Sync#accept(io.helidon.webserver.ServerRequest, Runnable)
 * @see Sync#submit(io.helidon.webserver.ServerRequest, Supplier)
 */
public final class SyncSupport implements Service {
    private final ExecutorService executorService;

    /**
     * Create sync support with default executor service.
     *
     * @return a new sync support with defaults
     * @see ThreadPoolSupplier
     */
    public static SyncSupport create() {
        return builder().build();
    }

    /**
     * Create sync support configured from the configuration provided.
     * The config should be located on the node of the sync support, which expects a node "thread-pool" with
     * {@link ThreadPoolSupplier} configuration options.
     *
     * @param config configuration of synchronous support
     * @return a new instance configured from config
     */
    public static SyncSupport create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * Builder of synchronous support.
     *
     * @return fluent API builder for this class
     */
    public static Builder builder() {
        return new Builder();
    }

    private SyncSupport(Builder builder) {
        this.executorService = builder.executorService.get();
    }

    @Override
    public void update(Routing.Rules rules) {
        Sync sync = new Sync(executorService);
        rules.any((req, res) -> {
            req.context().register(sync);
            req.next();
        });
    }

    /**
     * A fluent API builder for {@link SyncSupport}.
     */
    public static final class Builder implements io.helidon.common.Builder<SyncSupport> {
        private Supplier<? extends ExecutorService> executorService = ThreadPoolSupplier.create();

        private Builder() {
        }

        @Override
        public SyncSupport build() {
            return new SyncSupport(this);
        }

        /**
         * Configure this builder from a {@link Config} instance located on the node of this
         * builder (expects optional "thread-pool" key with configuration of {@link ThreadPoolSupplier}.
         *
         * @param config configuration
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("thread-pool")
                    .asOptional(ThreadPoolSupplier.class)
                    .ifPresent(this::executorServiceSupplier);
            return this;
        }

        /**
         * Configure a supplier of an executor service.
         * The supplier will be evaluated during {@link #build()} method (e.g. each built instance will call
         * this method).
         *
         * @param supplier supplier to get {@link ExecutorService}
         * @return updated builder instance
         * @see ThreadPoolSupplier
         */
        public Builder executorServiceSupplier(Supplier<? extends ExecutorService> supplier) {
            this.executorService = supplier;
            return this;
        }

        /**
         * Configure an executor service to use (will be shared by all instances built from this builder).
         *
         * @param service executor service to use
         * @return updated builder instance
         */
        public Builder executorService(ExecutorService service) {
            this.executorService = () -> service;
            return this;
        }
    }
}
