/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.common.concurrency.limits;

import java.util.concurrent.Callable;

import io.helidon.common.config.NamedService;
import io.helidon.service.registry.Service;

/**
 * Contract for a concurrency limiter.
 */
@Service.Contract
public interface Limit extends NamedService {
    /**
     * Invoke a callable within the limits of this limiter.
     * <p>
     * {@link io.helidon.common.concurrency.limits.Limit} implementors note:
     * Make sure to catch {@link io.helidon.common.concurrency.limits.IgnoreTaskException} from the
     * callable, and call its {@link IgnoreTaskException#handle()} to either return the provided result,
     * or throw the exception after ignoring the timing for future decisions.
     *
     * @param callable callable to execute within the limit
     * @param <T>      the callable return type
     * @return result of the callable
     * @throws LimitException      in case the limiter did not have an available permit
     * @throws java.lang.Exception in case the task failed with an exception
     */
    <T> T invoke(Callable<T> callable) throws LimitException, Exception;

    /**
     * Invoke a runnable within the limits of this limiter.
     * <p>
     * {@link io.helidon.common.concurrency.limits.Limit} implementors note:
     * Make sure to catch {@link io.helidon.common.concurrency.limits.IgnoreTaskException} from the
     * runnable, and call its {@link IgnoreTaskException#handle()} to either return the provided result,
     * or throw the exception after ignoring the timing for future decisions.
     *
     * @param runnable runnable to execute within the limit
     * @throws LimitException in case the limiter did not have an available permit
     * @throws java.lang.Exception in case the task failed with an exception
     */
    void invoke(Runnable runnable) throws LimitException, Exception;
}
