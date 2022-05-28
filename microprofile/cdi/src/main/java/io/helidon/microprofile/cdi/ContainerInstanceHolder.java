/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.microprofile.cdi;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * This class contains the container used by this Helidon runtime.
 * The origin may be {@link io.helidon.microprofile.cdi.BuildTimeInitializer} when running using
 * MicroProfile server or {@link io.helidon.microprofile.cdi.Main},
 * or {@link io.helidon.microprofile.cdi.HelidonContainerInitializer} in case running through CDI
 * initialization.
 * NEVER both.
 */
final class ContainerInstanceHolder {
    private static final AtomicReference<HelidonContainer> CONTAINER = new AtomicReference<>();
    private static final List<Runnable> RESET_LISTENERS = new LinkedList<>();
    private static boolean isReset = false;

    private static final Semaphore accessGuard = new Semaphore(1, true);

    private ContainerInstanceHolder() {
    }

    static void set(HelidonContainer container) {
        CONTAINER.set(container);
    }

    // return true if the container was reset, indicating somebody started CDI by hand and then shut it down
    static boolean isReset() {
        return access(() -> isReset);
    }

    static HelidonContainer get() {
        return access(() -> {
            HelidonContainer helidonContainer = CONTAINER.get();
            if (null == helidonContainer) {
                helidonContainer = fromBuildTimeInitializer();
                CONTAINER.compareAndSet(null, helidonContainer);
            }
            return helidonContainer;
        });
    }

    private static HelidonContainer fromBuildTimeInitializer() {
        // this is the time the class should get initialized
        return BuildTimeInitializer.get();
    }

    static void addListener(Runnable runnable) {
        access(() -> {
            RESET_LISTENERS.add(runnable);
            return null;
        });
    }

    static void reset() {
        access(() -> {
            isReset = true;
            CONTAINER.set(null);
            for (Runnable resetListener : RESET_LISTENERS) {
                resetListener.run();
            }
            HelidonCdiProvider.unset();
            RESET_LISTENERS.clear();
            return null;
        });
    }

    private static <T> T access(Supplier<T> operation) {
        try {
            accessGuard.acquire();
            T result = operation.get();
            accessGuard.release();
            return result;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
