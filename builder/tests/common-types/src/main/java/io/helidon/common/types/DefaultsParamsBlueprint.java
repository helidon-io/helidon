package io.helidon.common.types;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Parameters to code generate default values.
 */
@Prototype.Blueprint
interface DefaultsParamsBlueprint {
    /**
     * Name of the {@link io.helidon.common.GenericType} field/variable that is the target type.
     * This is only needed when mapping to a type, or when using a provider.
     *
     * @return name of the field holding the {@link io.helidon.common.GenericType}
     */
    Optional<String> genericTypeNameField();

    /**
     * Suffix of field/parameter name for default value handling of a provider within the current class.
     * This is only needed when using a provider, as we need to create a field,
     * parameter, and assignment for it.
     *
     * @return suffix to use when creating provider field/parameter
     */
    @Option.Default("_invalid")
    String providerNameSuffix();

    /**
     * Qualifier used when mapping string values to target type.
     *
     * @return mapper qualifier
     */
    @Option.Default("defaults")
    String mapperQualifier();

    /**
     * Name of the field/variable that contains a {@code Mappers} instance.
     *
     * @return mappers field name
     */
    @Option.Default("mappers")
    String mappersField();

    /**
     * Name as sent to default value provider.
     *
     * @return name to use with provider
     */
    @Option.Default("default")
    String name();

    /**
     * Name of the field/variable of the context to be sent to default value provider.
     *
     * @return context name
     */
    Optional<String> contextField();
}
