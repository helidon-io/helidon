package io.helidon.service.tests.inject.events.api;

import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import io.helidon.common.types.ResolvedType;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.service.registry.Service;

@Service.Contract
public interface EventManager {
    <T> void register(ResolvedType eventType, Consumer<T> eventConsumer, Set<Qualifier> qualifiers);

    <T> void registerAsync(ResolvedType eventType, Consumer<T> eventConsumer, Set<Qualifier> qualifiers);

    void emit(ResolvedType eventObjectType,
              Object eventObject,
              Set<Qualifier> qualifiers);

    <T> CompletionStage<T> emitAsync(ResolvedType eventObjectType,
                                     T eventObject,
                                     Set<Qualifier> qualifiers);
}
