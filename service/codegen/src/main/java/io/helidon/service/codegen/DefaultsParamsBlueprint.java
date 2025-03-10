package io.helidon.service.codegen;


import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Parameters to code generate default values.
 */
@Prototype.Blueprint
interface DefaultsParamsBlueprint {
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
