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

package io.helidon.examples.integrations.eclipsestore.greetings.mp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.helidon.integrations.eclipsestore.cdi.EclipseStoreStorage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

/**
 * Provider for greeting message that are persisted by eclipsestore.
 */
@ApplicationScoped
public class GreetingProvider {

    /**
     * A lock.
     */
    private final ReentrantReadWriteLock lock
            = new ReentrantReadWriteLock(true);

    /**
     * Eclipse Storage manager.
     */
    private final EmbeddedStorageManager storage;

    /**
     * Random numbers generator.
     */
    private final Random rnd = new Random();

    /**
     * Gertting messages.
     */
    private List<String> greetingMessages;

    /**
     * Creates new GreetingProvider using a eclipsestore EmbeddedStorageManager.
     *
     * @param eclipseStorageManager the used EmbeddedStorageManager.
     */
    @SuppressWarnings("unchecked")
    @Inject
    public GreetingProvider(
        @EclipseStoreStorage(configNode = "one.eclipsestore.storage.greetings")
         final  EmbeddedStorageManager eclipseStorageManager) {
        super();
        storage = eclipseStorageManager;

        // load stored data
        greetingMessages = (List<String>) storage.root();

        //  Initialize storage if empty
        if (greetingMessages == null) {
            greetingMessages = new ArrayList<>();
            storage.setRoot(greetingMessages);
            storage.storeRoot();
            addGreeting("Hello");
        }
    }

    /**
     * Add a new greeting to the available greetings and persist it.
     *
     * @param newGreeting the new greeting to be added and persisted.
     */
    public void addGreeting(final String newGreeting) {
        try {
            lock.writeLock().lock();
            greetingMessages.add(newGreeting);
            storage.store(greetingMessages);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * returns a random greeting.
     *
     * @return a greeting.
     */
    public String getGreeting() {
        try {
            lock.readLock().lock();
            return greetingMessages.get(rnd.nextInt(greetingMessages.size()));
        } finally {
            lock.readLock().unlock();
        }
    }

}
