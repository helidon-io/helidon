package io.helidon.service.tests.inject.events;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.service.tests.inject.events.api.Event;

class EventHandler<T> implements Event.Emitter<T> {
    private final Supplier<List<Event.Listener<T>>> consumers;
    private final Supplier<List<Event.Listener<T>>> asyncConsumers;
    private final ExecutorService executor;

    EventHandler(TypeName eventType,
                 Supplier<List<Event.Listener<T>>> consumers,
                 Supplier<List<Event.Listener<T>>> asyncConsumers) {
        this.consumers = consumers;
        this.asyncConsumers = asyncConsumers;
        this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .name(eventType.classNameWithEnclosingNames(), 0)
                .factory());
    }

    static <T> EventHandler<T> create(TypeName eventType,
                                      Supplier<List<Event.Listener<T>>> consumers,
                                      Supplier<List<Event.Listener<T>>> asyncConsumers) {
        return new EventHandler<>(eventType, consumers, asyncConsumers);
    }

    @Override
    public void emit(T eventObject) {
        asyncConsumers.get()
                        .forEach(it -> executor.submit(() -> it.onEvent(eventObject)));
        consumers.get().forEach(it -> it.onEvent(eventObject));
    }
}
