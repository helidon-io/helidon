/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.eclipsestore.greetings.se;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

/**
 * This class extends {@link EclipseStoreExecutionContext}
 * and provides data access methods.
 */
public class GreetingServiceEclipseStoreContext
        extends EclipseStoreExecutionContext {

    /**
     * Create a new instance.
     *
     * @param storageManager the EmbeddedStorageManager used.
     */
    public GreetingServiceEclipseStoreContext(
            final EmbeddedStorageManager storageManager) {
        super(storageManager);
    }

    /**
     * Add and store a new log entry.
     *
     * @param name parameter for log text.
     */
    @SuppressWarnings({"unchecked", "resource"})
    public void addLogEntry(final String name) {
        List<LogEntry> logs = (List<LogEntry>) storageManager().root();
        logs.add(new LogEntry(name, LocalDateTime.now()));
        storageManager().store(logs);
    }

    /**
     * initialize the storage root with a new, empty List.
     */
    @SuppressWarnings("resource")
    public void initRootElement() {
        if (storageManager().root() == null) {
            storageManager().setRoot(new ArrayList<LogEntry>());
            storageManager().storeRoot();
        }
    }

    /**
     * returns a List of all stored LogEntries.
     *
     * @return all LogEntries.
     */
    @SuppressWarnings({"unchecked", "resource"})
    public List<LogEntry> getLogs() {
        return (List<LogEntry>) storageManager().root();
    }

}
