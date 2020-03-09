/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config.examples.changes;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.FileSystemWatcher;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;
import static io.helidon.config.PollingStrategies.regular;

/**
 * Example shows how to use Config accessor methods that return {@link Supplier}.
 * {@link Supplier} returns always the last loaded config value.
 * <p>
 * The feature is based on using {@link io.helidon.config.spi.PollingStrategy} with
 * selected config source(s) to check for changes.
 */
public class AsSupplierExample {

    private static final Logger LOGGER = Logger.getLogger(AsSupplierExample.class.getName());

    private final AtomicReference<String> lastPrinted = new AtomicReference<>();
    private final ScheduledExecutorService executor = initExecutor();

    /**
     * Executes the example.
     */
    public void run() {
        Config config = Config
                .create(file("conf/dev.yaml")
                                .optional()
                                // change watcher is a standalone component that watches for
                                // changes and notifies the config system when a change occurs
                                .changeWatcher(FileSystemWatcher.create()),
                        file("conf/config.yaml")
                                .optional()
                                // polling strategy triggers regular checks on the source to check
                                // for changes, utilizing a concept of "stamp" of the data that is provided
                                // and validated by the source
                                .pollingStrategy(regular(Duration.ofSeconds(2))),
                        classpath("default.yaml"));

        // greeting.get() always return up-to-date value
        final Supplier<String> greeting = config.get("app.greeting").asString().supplier();
        // name.get() always return up-to-date value
        final Supplier<String> name = config.get("app.name").asString().supplier();

        // first greeting
        printIfChanged(greeting.get() + " " + name.get() + ".");

        // use same Supplier instances to get up-to-date value
        executor.scheduleWithFixedDelay(
                () -> printIfChanged(greeting.get() + " " + name.get() + "."),
                // check every 1 second for changes
                0, 1, TimeUnit.SECONDS);
    }

    /**
     * Utility to print same message just once.
     */
    private void printIfChanged(String message) {
        lastPrinted.accumulateAndGet(message, (origValue, newValue) -> {
            //print MESSAGE only if changed since the last print
            if (!Objects.equals(origValue, newValue)) {
                LOGGER.info("[AsSupplier] " + newValue);
            }
            return newValue;
        });
    }

    private static ScheduledExecutorService initExecutor() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdown));
        return executor;
    }

    /**
     * Shutdowns executor.
     */
    public void shutdown() {
        executor.shutdown();
    }

}
