/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver.jersey;

import java.util.concurrent.ExecutorService;

import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.config.Config;

import org.glassfish.jersey.server.ManagedAsyncExecutor;
import org.glassfish.jersey.spi.ExecutorServiceProvider;

@ManagedAsyncExecutor
class AsyncExecutorProvider implements ExecutorServiceProvider {
    private final ThreadPoolSupplier executorServiceSupplier;

    AsyncExecutorProvider(Config config) {
        this.executorServiceSupplier = ThreadPoolSupplier.builder()
                .corePoolSize(1)
                .maxPoolSize(10)
                .prestart(false)
                .threadNamePrefix("helidon-jersey-async")
                .config(config)
                .build();
    }

    @Override
    public ExecutorService getExecutorService() {
        return executorServiceSupplier.get();
    }

    @Override
    public void dispose(ExecutorService executorService) {

    }
}
