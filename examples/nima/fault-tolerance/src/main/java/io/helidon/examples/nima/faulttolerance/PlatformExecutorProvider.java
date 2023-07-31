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

package io.helidon.examples.nima.faulttolerance;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.LazyValue;

import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * This service will be part of Níma on Injection module.
 * It may use Injection to get config sources exposed through injection.
 */
@Singleton
@Named("platform-executor")
class PlatformExecutorProvider implements Provider<ExecutorService> {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static final LazyValue<ExecutorService> EXECUTOR_SERVICE = LazyValue.create(() -> {
        return Executors.newFixedThreadPool(10,
                                            r -> new Thread(r, "platform-" + COUNTER.getAndIncrement()));
    });

    @Override
    public ExecutorService get() {
        return EXECUTOR_SERVICE.get();
    }
}
