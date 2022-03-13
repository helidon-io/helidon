/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.jersey.client;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import io.helidon.common.context.Contexts;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.glassfish.jersey.client.ClientAsyncExecutor;
import org.glassfish.jersey.client.internal.LocalizationMessages;
import org.glassfish.jersey.internal.util.collection.LazyValue;
import org.glassfish.jersey.internal.util.collection.Value;
import org.glassfish.jersey.internal.util.collection.Values;
import org.glassfish.jersey.spi.ThreadPoolExecutorProvider;

/**
 * Wraps default executor to enable Helidon context propagation for Jersey async calls.
 */
@ClientAsyncExecutor
public class ExecutorProvider extends ThreadPoolExecutorProvider {

    static final String THREAD_NAME_PREFIX = "helidon-client-async-executor";
    private static final Logger LOGGER = Logger.getLogger(ExecutorProvider.class.getName());
    private final LazyValue<Integer> asyncThreadPoolSize;

    /**
     * Create new instance of the context aware executor provider.
     * @param poolSize Maximum pool size
     */
    @Inject
    public ExecutorProvider(@Named("ClientAsyncThreadPoolSize") final Optional<Integer> poolSize) {
        super(THREAD_NAME_PREFIX);

        this.asyncThreadPoolSize = Values.lazy((Value<Integer>) () -> {
            if (poolSize.filter(i -> i > 0).isEmpty()) {
                LOGGER.config(LocalizationMessages.IGNORED_ASYNC_THREADPOOL_SIZE(poolSize.orElse(0)));
                // using default
                return Integer.MAX_VALUE;
            } else {
                LOGGER.config(LocalizationMessages.USING_FIXED_ASYNC_THREADPOOL(poolSize.get()));
                return poolSize.get();
            }
        });
    }

    @Override
    protected int getMaximumPoolSize() {
        return asyncThreadPoolSize.get();
    }

    @Override
    protected int getCorePoolSize() {
        // Mimicking the Executors.newCachedThreadPool and newFixedThreadPool configuration values.
        final int maximumPoolSize = getMaximumPoolSize();
        if (maximumPoolSize != Integer.MAX_VALUE) {
            return maximumPoolSize;
        } else {
            return 0;
        }
    }

    @Override
    public ExecutorService getExecutorService() {
        return Contexts.wrap(super.getExecutorService());
    }
}
