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

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigFilterFactory;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.EventConfigSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.WatchableSource;

final class OverrideFilterFactory implements ConfigFilterFactory {
    private final OverrideConfig config;
    private final Config definitions;
    private final boolean changesSupported;
    private final AtomicReference<Runnable> requestReload = new AtomicReference<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();

    private OverrideFilterFactory(OverrideConfig config, Config definitions, boolean changesSupported) {
        this.config = config;
        this.definitions = definitions;
        this.changesSupported = changesSupported;
        definitions.onChange(_ -> changed());
    }

    static OverrideFilterFactory create(OverrideConfig config) {
        var sources = new ArrayList<ConfigSource>();
        config.configSources().forEach(supplier -> sources.add(Objects.requireNonNull(supplier.get())));
        Config definitions;
        if (sources.isEmpty()) {
            definitions = Config.empty();
        } else {
            var builder = Config.builder()
                    .disableEnvironmentVariablesSource()
                    .disableSystemPropertiesSource()
                    .disableFilterServices()
                    .disableValueResolving();
            sources.forEach(builder::addSource);
            definitions = builder.build();
        }
        try {
            boolean changesSupported = sources.stream().anyMatch(OverrideFilterFactory::changesSupported);
            return new OverrideFilterFactory(config, definitions, changesSupported);
        } catch (RuntimeException e) {
            definitions.context().stopChangeSupport();
            throw e;
        }
    }

    @Override
    public ConfigFilter create(Config rawConfig) {
        Objects.requireNonNull(rawConfig);
        // Capture the latest known definitions without restarting monitoring or reopening source configuration.
        // After stop, a manual target reload can still use this last known snapshot.
        Config snapshot = definitions.context().last();
        return OverrideConfigFilter.snapshot(config, snapshot);
    }

    @Override
    public boolean startChangeSupport(Runnable callback) {
        Objects.requireNonNull(callback);
        if (stopped.get() || !started.compareAndSet(false, true)) {
            return false;
        }
        if (changesSupported) {
            requestReload.set(callback);
            if (stopped.get()) {
                requestReload.set(null);
                return false;
            }
            // Reconcile a definition change between the initial filter capture and installing the callback.
            changed();
        }
        return changesSupported;
    }

    @Override
    public void stopChangeSupport() {
        if (stopped.compareAndSet(false, true)) {
            requestReload.set(null);
            definitions.context().stopChangeSupport();
        }
    }

    private static boolean changesSupported(ConfigSource source) {
        return source instanceof EventConfigSource
                || source instanceof PollableSource<?> pollable && pollable.pollingStrategy().isPresent()
                || source instanceof WatchableSource<?> watchable && watchable.changeWatcher().isPresent();
    }

    private void changed() {
        Runnable callback = requestReload.get();
        if (callback != null) {
            callback.run();
        }
    }
}
