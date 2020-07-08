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

package io.helidon.microprofile.faulttolerance;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

final class AsynchronousUtil {

    private AsynchronousUtil() {
    }

    /**
     * Maps an {@link FtSupplier} to a supplier of {@link CompletionStage}. Avoids
     * unnecessary wrapping of stages.
     *
     * @param supplier The supplier.
     * @return The new supplier.
     */
    static Supplier<? extends CompletionStage<Object>> toCompletionStageSupplier(FtSupplier<Object> supplier) {
        return () -> {
            try {
                Object result = supplier.get();
                return result instanceof CompletionStage<?> ? (CompletionStage<Object>) result
                        : CompletableFuture.completedFuture(result);
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        };
    }
}
