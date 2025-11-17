package io.helidon.builder.codegen;

import java.util.Optional;

import io.helidon.builder.api.Prototype;

/**
 * Configuration specific to a factory method to create a runtime type from a prototype with a builder.
 */
@Prototype.Blueprint(detach = true)
interface RuntimeTypeInfoBlueprint {
    /**
     * Factory method.
     * If not defined, we expect the builder to build the correct type.
     *
     * @return the factory method if present
     */
    Optional<FactoryMethod> factoryMethod();

    /**
     * Builder information associated with this factory method.
     *
     * @return builder information
     */
    OptionBuilder optionBuilder();
}
