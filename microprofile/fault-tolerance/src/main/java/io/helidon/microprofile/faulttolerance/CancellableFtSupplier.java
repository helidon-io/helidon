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
 */

package io.helidon.microprofile.faulttolerance;

import java.util.concurrent.CancellationException;

/**
 * A {@code FtSupplier} that can be cancelled.
 */
class CancellableFtSupplier<T> implements FtSupplier<T> {

    private boolean cancelled = false;
    private boolean getCalled = false;
    private final FtSupplier<T> supplier;

    private CancellableFtSupplier(FtSupplier<T> supplier) {
        this.supplier = supplier;
    }

    void cancel() {
        this.cancelled = true;
    }

    boolean isCancelled() {
        return cancelled;
    }

    boolean getCalled() {
        return getCalled;
    }

    @Override
    public T get() throws Throwable {
        getCalled = true;
        if (cancelled) {
            throw new CancellationException("Supplier has been cancelled");
        }
        return supplier.get();
    }

    static <T> CancellableFtSupplier<T> create(FtSupplier<T> supplier) {
        return new CancellableFtSupplier<>(supplier);
    }
}
