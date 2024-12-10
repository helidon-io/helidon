package io.helidon.common.mapper;

import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.GenericType;

@RuntimeType.PrototypedBy(MappersConfig.class)
public interface Mappers extends RuntimeType.Api<MappersConfig> {
    static MappersConfig.Builder builder() {
        return MappersConfig.builder();
    }

    static Mappers create() {
        return builder().build();
    }

    static Mappers create(MappersConfig config) {
        return new MappersImpl(config);
    }

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
