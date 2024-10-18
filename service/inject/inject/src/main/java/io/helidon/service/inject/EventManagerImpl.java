/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.types.ResolvedType;
import io.helidon.service.inject.api.EventDispatchException;
import io.helidon.service.inject.api.EventManager;
import io.helidon.service.inject.api.GeneratedInjectService.EventObserverRegistration;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.service.registry.Service;

@Injection.Singleton
class EventManagerImpl implements EventManager {
    private static final System.Logger LOGGER = System.getLogger(EventManager.class.getName());

    private final Supplier<List<EventObserverRegistration>> registrations;
    private final Map<RegistrationKey, List<Consumer<?>>> listeners = new HashMap<>();
    private final Map<RegistrationKey, List<Consumer<?>>> asyncListeners = new HashMap<>();
    private final ReadWriteLock listenersLock = new ReentrantReadWriteLock();
    private final ExecutorService executor;

    @Injection.Inject
    EventManagerImpl(Supplier<List<EventObserverRegistration>> registrations,
                     @Injection.NamedByType(EventManager.class) Optional<ExecutorService> executorService) {
        this.registrations = registrations;
        this.executor = executorService.orElseGet(() -> Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name("inject-event-manager-", 0)
                        .factory()));
    }

    @Override
    public <T> void register(ResolvedType eventType, Consumer<T> eventConsumer, Set<Qualifier> qualifiers) {
        listenersLock.writeLock().lock();
        try {
            listeners.computeIfAbsent(new RegistrationKey(eventType, qualifiers),
                                      k -> new ArrayList<>())
                    .add(eventConsumer);
        } finally {
            listenersLock.writeLock().unlock();
        }
    }

    @Override
    public <T> void registerAsync(ResolvedType eventType, Consumer<T> eventConsumer, Set<Qualifier> qualifiers) {
        listenersLock.writeLock().lock();
        try {
            asyncListeners.computeIfAbsent(new RegistrationKey(eventType, qualifiers),
                                           k -> new ArrayList<>())
                    .add(eventConsumer);
        } finally {
            listenersLock.writeLock().unlock();
        }
    }

    @Override
    public void emit(ResolvedType eventObjectType, Object eventObject, Set<Qualifier> qualifiers) {
        // first get all consumers
        listenersLock.readLock().lock();

        List<Consumer<?>> consumers;
        List<Consumer<?>> asyncConsumers;
        try {
            consumers = listeners.get(new RegistrationKey(eventObjectType, qualifiers));
            asyncConsumers = asyncListeners.get(new RegistrationKey(eventObjectType, qualifiers));
        } finally {
            listenersLock.readLock().unlock();
        }

        // async consumers should not block anything, just fire and forget
        if (asyncConsumers != null) {
            fireAndForget(asyncConsumers, eventObject);
        }
        // consumers block the execution
        if (consumers != null) {
            fire(consumers, eventObject);
        }
    }

    @Override
    public <T> CompletionStage<T> emitAsync(ResolvedType eventObjectType, T eventObject, Set<Qualifier> qualifiers) {
        // first get all consumers
        listenersLock.readLock().lock();

        List<Consumer<?>> consumers;
        List<Consumer<?>> asyncConsumers;
        try {
            consumers = listeners.get(new RegistrationKey(eventObjectType, qualifiers));
            asyncConsumers = asyncListeners.get(new RegistrationKey(eventObjectType, qualifiers));
        } finally {
            listenersLock.readLock().unlock();
        }

        // async consumers should not block anything, just fire and forget
        if (asyncConsumers != null) {
            fireAndForget(asyncConsumers, eventObject);
        }
        // consumers block the execution
        if (consumers != null) {
            // we do care about results of this (it may throw only EventDispatchException or an error
            return CompletableFuture.supplyAsync(() -> {
                                                     fire(consumers, eventObject);
                                                     return eventObject;
                                                 },
                                                 executor);
        }
        return CompletableFuture.completedFuture(eventObject);
    }

    @Service.PostConstruct
    void init() {
        var registrationList = registrations.get();
        registrationList.forEach(reg -> reg.register(this));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void fire(List<Consumer<?>> consumers, Object eventObject) {
        List<Exception> thrown = new ArrayList<>();
        for (Consumer consumer : consumers) {
            try {
                consumer.accept(eventObject);
            } catch (Exception e) {
                thrown.add(e);
            }
        }
        if (thrown.isEmpty()) {
            return;
        }

        var exception = new EventDispatchException("Event dispatching failed, see suppressed exceptions", thrown.getFirst());
        for (int i = 1; i < thrown.size(); i++) {
            exception.addSuppressed(thrown.get(i));
        }

        throw exception;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void fireAndForget(List<Consumer<?>> asyncConsumers, Object eventObject) {
        for (Consumer asyncConsumer : asyncConsumers) {
            executor.submit(() -> {
                try {
                    asyncConsumer.accept(eventObject);
                } catch (Exception e) {
                    LOGGER.log(System.Logger.Level.WARNING, "Asynchronous event dispatch failed.", e);
                }
            });
        }
    }

    private record RegistrationKey(ResolvedType eventObject,
                                   Set<Qualifier> qualifiers) {
    }
}
