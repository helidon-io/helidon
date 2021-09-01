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

package io.helidon.examples.integrations.microstream.greetings.mp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.helidon.integrations.microstream.cdi.MicrostreamStorage;

import one.microstream.storage.embedded.types.EmbeddedStorageManager;

/**
 * Provider for greeting message that are persisted by microstream.
 */
@ApplicationScoped
public class GreetingProvider {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final EmbeddedStorageManager storage;
    private final Random rnd = new Random();

    private List<String> greetingMessages;

    /**
     * Creates new GreetingProvider using a microstream EmbeddedStorageManager.
     *
     * @param storage the used EmbeddedStorageManager.
     */
    @SuppressWarnings("unchecked")
    @Inject
    public GreetingProvider(@MicrostreamStorage(configNode = "one.microstream.storage.greetings")
                                    EmbeddedStorageManager storage) {
        super();
        this.storage = storage;

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
    public void addGreeting(String newGreeting) {
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
