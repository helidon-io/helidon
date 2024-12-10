package io.helidon.common.mapper;

import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.GenericType;

/**
 * Mappers manager of all configured mappers.
 * <p>
 * To map a source to target, you can use either of the {@code map} methods defined in this interface,
 * as they make sure that the mapping exists in either space.
 * <ul>
 * <li>If you call {@link #map(Object, Class, Class, String...)} and no mapper is found for the class pair,
 * the implementation calls the {@link #map(Object, io.helidon.common.GenericType, io.helidon.common.GenericType, String...)}
 * with {@link io.helidon.common.GenericType}s created for each parameters</li>
 * <li>If you call {@link #map(Object, io.helidon.common.GenericType, io.helidon.common.GenericType, String...)} and no mapper is
 * found for the {@link io.helidon.common.GenericType} pair, an attempt is to locate a mapper for
 * the underlying class *IF* the generic type represents a simple class (e.g. not a generic type declaration)</li>
 * </ul>
 */
@RuntimeType.PrototypedBy(MappersConfig.class)
public interface Mappers extends RuntimeType.Api<MappersConfig> {
    /**
     * Create a new builder to customize configuration of {@link io.helidon.common.mapper.Mappers}.
     *
     * @return a new fluent API builder
     */
    static MappersConfig.Builder builder() {
        return MappersConfig.builder();
    }

    /**
     * Create mappers using defaults.
     *
     * @return create new mappers
     */
    static Mappers create() {
        return builder().build();
    }

    /**
     * Create new {@link io.helidon.common.mapper.Mappers} using the provided configuration.
     *
     * @param config mappers configuration
     * @return a new mappers configured from the provided config
     */
    static Mappers create(MappersConfig config) {
        return new MappersImpl(config);
    }

    /**
     * Create new {@link io.helidon.common.mapper.Mappers} customizing its configuration.
     *
     * @param consumer consumer of configuration builder
     * @return a new configured mappers instance
     */
    static Mappers create(Consumer<MappersConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    /**
     * Map from source to target.
     *
     * @param source     object to map
     * @param sourceType type of the source object (to locate the mapper)
     * @param targetType type of the target object (to locate the mapper)
     * @param qualifiers qualifiers of the usage (such as {@code http-headers, http}, most specific one first)
     * @param <SOURCE>   type of the source
     * @param <TARGET>   type of the target
     * @return result of the mapping
     * @throws MapperException in case the mapper was not found or failed
     */
    <SOURCE, TARGET> TARGET map(SOURCE source,
                                GenericType<SOURCE> sourceType,
                                GenericType<TARGET> targetType,
                                String... qualifiers)
            throws MapperException;

    /**
     * Map from source to target.
     *
     * @param source     object to map
     * @param sourceType class of the source object (to locate the mapper)
     * @param targetType class of the target object (to locate the mapper)
     * @param qualifiers qualifiers of the usage (such as {@code http-headers, http}, most specific one first)
     * @param <SOURCE>   type of the source
     * @param <TARGET>   type of the target
     * @return result of the mapping
     * @throws MapperException in case the mapper was not found or failed
     */
    <SOURCE, TARGET> TARGET map(SOURCE source, Class<SOURCE> sourceType, Class<TARGET> targetType, String... qualifiers)
            throws MapperException;

    /**
     * Obtain a mapper for the provided types and qualifiers.
     *
     * @param sourceType type to map from
     * @param targetType type to map to
     * @param qualifiers qualifiers of the mapper
     * @param <SOURCE>   source type
     * @param <TARGET>   target type
     * @return mapper if found
     */
    <SOURCE, TARGET> Optional<Mapper<SOURCE, TARGET>> mapper(GenericType<SOURCE> sourceType,
                                                             GenericType<TARGET> targetType,
                                                             String... qualifiers);
}
