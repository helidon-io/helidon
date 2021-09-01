/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.microstream.greetings.se;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.reactive.Single;

import one.microstream.storage.embedded.types.EmbeddedStorageManager;

/**
 * This class extends the MicrostreamSingleThreadedExecutionContext and provides
 * data access methods using the MicrostreamSingleThreadedExecutionContext.
 */
public class GreetingServiceMicrostreamContext extends MicrostreamSingleThreadedExecutionContext {

    /**
     * Create a new GreetingServiceMicrostreamContext.
     *
     * @param storageManager the EmbeddedStorageManager used.
     */
    public GreetingServiceMicrostreamContext(EmbeddedStorageManager storageManager) {
        super(storageManager);
    }

    /**
     * Add and store a new log entry.
     *
     * @param name paramter for log text.
     * @return Void
     */
    public CompletableFuture<Void> addLogEntry(String name) {
        return execute(() -> {
            @SuppressWarnings("unchecked")
            List<LogEntry> logs = (List<LogEntry>) storageManager().root();
            logs.add(new LogEntry(name, LocalDateTime.now()));
            storageManager().store(logs);
            return null;
        });
    }

    /**
     * initialize the storage root with a new, empty List.
     *
     * @return Void
     */
    public CompletableFuture<Void> initRootElement() {
        return execute(() -> {
            if (storageManager().root() == null) {
                storageManager().setRoot(new ArrayList<LogEntry>());
                storageManager().storeRoot();
            }
            return null;
        });
    }

    /**
     * returns a List of all stored LogEntries.
     *
     * @return all LogEntries.
     */
    public Single<List<LogEntry>> getLogs() {
        @SuppressWarnings("unchecked")
        CompletableFuture<List<LogEntry>> completableFuture = CompletableFuture.supplyAsync(() -> {
            return (List<LogEntry>) storageManager().root();
        }, executor());
        return (Single<List<LogEntry>>) Single.create(completableFuture);
    }

}
