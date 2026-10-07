/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.config.overrides;

import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;

final class OverrideChangeSupport implements OverrideConfigFilter.ChangeSupport {
    private static final System.Logger LOGGER = System.getLogger(OverrideChangeSupport.class.getName());

    private final AtomicReference<Config.Context> targetContext;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();

    private OverrideChangeSupport(Config.Context targetContext) {
        this.targetContext = new AtomicReference<>(targetContext);
    }

    static OverrideConfigFilter.ChangeSupport create(Config definitions, Config target) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(target, "target");
        if (definitions.context() == target.context()
                || definitions.root().context().last() == target.root().context().last()) {
            throw new IllegalArgumentException("Override definitions and target must use independent configuration trees");
        }

        OverrideChangeSupport support = new OverrideChangeSupport(target.context());
        try {
            definitions.onChange(_ -> support.changed());
            // Reconcile changes between creating the target's first filter and subscribing to the definitions.
            support.changed();
            return support;
        } catch (RuntimeException e) {
            support.close();
            throw e;
        }
    }

    @Override
    public void close() {
        // Config.onChange does not support unsubscription. Its remaining listener must not retain the target.
        targetContext.set(null);
        pending.set(false);
    }

    private void changed() {
        if (targetContext.get() != null) {
            pending.set(true);
            dispatch();
        }
    }

    private void dispatch() {
        if (targetContext.get() != null && running.compareAndSet(false, true)) {
            try {
                // Do not reload inside a definitions callback: it may run while its source/provider holds a lock.
                ForkJoinPool.commonPool().execute(this::reload);
            } catch (RuntimeException _) {
                running.set(false);
                LOGGER.log(System.Logger.Level.WARNING, "Could not schedule configuration reload after overrides changed");
            }
        }
    }

    private void reload() {
        try {
            if (pending.getAndSet(false)) {
                Config.Context context = targetContext.get();
                if (context != null) {
                    // Once admitted, a reload may finish after close. No bridge lock is held during user/source work.
                    context.reload();
                }
            }
        } catch (RuntimeException _) {
            // A later change can retry. Do not expose source locations, configuration values, or exception messages.
            LOGGER.log(System.Logger.Level.WARNING, "Could not reload configuration after overrides changed");
        } finally {
            running.set(false);
            // Signals arriving during a reload are coalesced into another task, including after a failed reload.
            if (pending.get()) {
                dispatch();
            }
        }
    }
}
