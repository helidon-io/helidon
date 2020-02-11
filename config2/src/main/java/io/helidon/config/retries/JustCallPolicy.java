/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.config.retries;

import java.util.function.Supplier;

import io.helidon.config.spi.RetryPolicy;

/**
 * A retry policy that calls the supplier exactly once.
 */
public final class JustCallPolicy implements RetryPolicy {
    private static final JustCallPolicy INSTANCE = new JustCallPolicy();

    private JustCallPolicy() {
    }

    @Override
    public <T> T execute(Supplier<T> call) {
        return call.get();
    }

    /**
     * An implementation that invokes the requested method just once, without any retries.
     *
     * @return a default execute policy
     */
    public static JustCallPolicy create() {
        return INSTANCE;
    }
}
