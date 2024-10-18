package io.helidon.service.inject.api;

import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import io.helidon.common.types.ResolvedType;
import io.helidon.service.registry.Service;

/**
 * Event manager is used by generated code to manage events and listeners.
 */
@Service.Contract
public interface EventManager {
    /**
     * Register an event consumer.
     *
     * @param eventType     type of event
     * @param eventConsumer consumer accepting the event
     * @param qualifiers    qualifiers the consumer is interested in
     * @param <T>           type of the event object
     */
    <T> void register(ResolvedType eventType, Consumer<T> eventConsumer, Set<Qualifier> qualifiers);

    /**
     * Register an asynchronous event consumer.
     *
     * @param eventType     type of event
     * @param eventConsumer consumer accepting the event
     * @param qualifiers    qualifiers the consumer is interested in
     * @param <T>           type of the event object
     */
    <T> void registerAsync(ResolvedType eventType, Consumer<T> eventConsumer, Set<Qualifier> qualifiers);

    /**
     * Emit an event.
     *
     * @param eventObjectType type of event
     * @param eventObject     event object instance
     * @param qualifiers      qualifiers of the producer of the event
     */
    void emit(ResolvedType eventObjectType,
              Object eventObject,
              Set<Qualifier> qualifiers);

    /**
     * Emit an asynchronous event.
     *
     * @param eventObjectType type of event
     * @param eventObject     event object instance
     * @param qualifiers      qualifiers of the producer of the event
     */
    <T> CompletionStage<T> emitAsync(ResolvedType eventObjectType,
                                     T eventObject,
                                     Set<Qualifier> qualifiers);
}
