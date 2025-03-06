package io.helidon.common.mapper;

import java.util.List;
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.common.types.Annotation;

/**
 * A service that resolves defaults from annotations.
 */
public interface DefaultsResolver {
    /**
     * Resolve defaults from the set of annotations.
     * Uses default annotations from {@link io.helidon.common.Default}.
     * In case there is more than one annotation defined, processes the first one in this order:
     * <ul>
     *     <li>{@link io.helidon.common.Default.Provider}</li>
     *     <li>{@link io.helidon.common.Default.Value}</li>
     *     <li>{@link io.helidon.common.Default.Int}</li>
     *     <li>{@link io.helidon.common.Default.Double}</li>
     *     <li>{@link io.helidon.common.Default.Boolean}</li>
     *     <li>{@link io.helidon.common.Default.Long}</li>
     * </ul>
     *
     * @param annotations  set of annotations to analyze
     * @param expectedType type we expect to map to
     * @param name         name of the element that has default value annotation
     * @param context      sent to
     *                     {@link io.helidon.common.Default.DefaultValueProvider#apply(io.helidon.common.GenericType, String,
     *                     Object)}
     *                     when the provider annotation is used
     * @return a list of default values, correctly typed
     * @throws io.helidon.common.mapper.MapperException in case there is a type mismatch
     */
    List<?> resolve(Set<Annotation> annotations, GenericType<?> expectedType, String name, Object context);
}
