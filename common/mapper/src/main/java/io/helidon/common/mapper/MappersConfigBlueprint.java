package io.helidon.common.mapper;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.mapper.spi.MapperProvider;

/**
 * Mappers configuration.
 * <p>
 * All mappers registered within this type (through its builder) are ordered by their weight, regardless of
 * order of configuration on builder.
 */
@Prototype.Blueprint(decorator = MappersConfigSupport.BuilderDecorator.class)
@Prototype.RegistrySupport
@Prototype.CustomMethods(MappersConfigSupport.CustomMethods.class)
interface MappersConfigBlueprint extends Prototype.Factory<Mappers> {
    /**
     * Mapper providers allow for introduction of new mappers into the system.
     * A mapper provider can be exposed either as a {@link io.helidon.service.registry.ServiceRegistry}
     * service, or a {@link java.util.ServiceLoader}, but never both.
     * <p>
     * Mapper providers are ordered by their {@link io.helidon.common.Weight}.
     *
     * @return a list of configured mapper providers
     */
    @Option.Singular
    @Option.RegistryService
    List<MapperProvider> mapperProviders();

    /**
     * Mappers discovered through {@link io.helidon.service.registry.ServiceRegistry}, or explicitly created
     * that implement both {@link Mapper#sourceType()} and {@link Mapper#targetType()} methods.
     * <p>
     * Mappers are ordered by {@link io.helidon.common.Weight} together with {@link MappersConfig#mapperProviders()} to
     * create a single list.
     *
     * @return list of mappers
     */
    @Option.Singular
    @Option.RegistryService
    List<Mapper<?, ?>> mappers();

    /**
     * Whether to use built-in mappers.
     * Defaults to {@code true}.
     *
     * @return whether to use built in mappers (such as String to Integer)
     */
    @Option.DefaultBoolean(true)
    boolean useBuiltInMappers();
}
