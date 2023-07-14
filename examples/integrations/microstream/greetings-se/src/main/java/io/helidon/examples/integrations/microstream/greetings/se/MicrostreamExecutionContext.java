/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import one.microstream.reference.LazyReferenceManager;
import one.microstream.storage.embedded.types.EmbeddedStorageManager;

/**
 * Provides a very simply way to access a Microstream storage, and it's associated data.
 */
public class MicrostreamExecutionContext {

    private final EmbeddedStorageManager storage;

    /**
     * Creates a new instance.
     *
     * @param storageManager the used EmbeddedStorageManager.
     */
    public MicrostreamExecutionContext(EmbeddedStorageManager storageManager) {
        this.storage = storageManager;
    }

    /**
     * returns the used storageManager.
     *
     * @return the used EmbeddedStorageManager.
     */
    public EmbeddedStorageManager storageManager() {
        return storage;
    }

    /**
     * Start the storage.
     *
     * @return the started EmbeddedStorageManager.
     */
    public EmbeddedStorageManager start() {
        return storage.start();
    }

    /**
     * Shutdown the storage.
     *
     * @return the stopped EmbeddedStorageManager.
     */
    public EmbeddedStorageManager shutdown() {
        storage.shutdown();
        LazyReferenceManager.get().stop();
        return storage;
    }

    /**
     * Return the persistent object graph's root object.
     *
     * @param <T> type of the root object
     * @return the graph's root object casted to <T>
     */
    @SuppressWarnings("unchecked")
    public <T> T root() {
        return (T) storage.root();
    }

    /**
     * Sets the passed instance as the new root for the persistent object graph.
     *
     * @param object the new root object
     * @return the new root object
     */
    public Object setRoot(Object object) {
        return storage.setRoot(object);
    }

    /**
     * Stores the registered root instance.
     *
     * @return the root instance's objectId.
     */
    public long storeRoot() {
        return storage.storeRoot();
    }

    /**
     * Stores the passed object.
     *
     * @param object object to store
     * @return the object id representing the passed instance.
     */
    public long store(Object object) {
        return storage.store(object);
    }
}
