package io.helidon.common.mapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.Default;
import io.helidon.common.Default.DefaultValueProvider;
import io.helidon.common.GenericType;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryException;

@Service.Singleton
class DefaultResolverService implements DefaultsResolver {
    private static final TypeName PROVIDER_TYPE = TypeName.create(Default.Provider.class);
    private static final TypeName VALUE_TYPE = TypeName.create(Default.Value.class);
    private static final TypeName INT_TYPE = TypeName.create(Default.Int.class);
    private static final TypeName DOUBLE_TYPE = TypeName.create(Default.Double.class);
    private static final TypeName BOOLEAN_TYPE = TypeName.create(Default.Boolean.class);
    private static final TypeName LONG_TYPE = TypeName.create(Default.Long.class);

    private final Supplier<Mappers> mappers;
    private final ServiceRegistry registry;

    @Service.Inject
    DefaultResolverService(Supplier<Mappers> mappers, ServiceRegistry registry) {
        this.mappers = mappers;
        this.registry = registry;
    }

    @Override
    public List<?> resolve(Set<Annotation> annotations, GenericType<?> expectedType, String name, Object context) {
        /*
         *     <li>{@link io.helidon.common.Default.Provider}</li>
         *     <li>{@link io.helidon.common.Default.Value}</li>
         *     <li>{@link io.helidon.common.Default.Int}</li>
         *     <li>{@link io.helidon.common.Default.Double}</li>
         *     <li>{@link io.helidon.common.Default.Boolean}</li>
         *     <li>{@link io.helidon.common.Default.Long}</li>
         */

        Optional<Annotation> found = Annotations.findFirst(PROVIDER_TYPE, annotations);
        if (found.isPresent()) {
            return provider(found.get(), expectedType, name, context);
        }
        found = Annotations.findFirst(VALUE_TYPE, annotations);
        if (found.isPresent()) {
            return value(found.get(), expectedType);
        }
        found = Annotations.findFirst(INT_TYPE, annotations);
        if (found.isPresent()) {
            return found.get().intValues().orElseGet(List::of);
        }
        found = Annotations.findFirst(DOUBLE_TYPE, annotations);
        if (found.isPresent()) {
            return found.get().doubleValues().orElseGet(List::of);
        }
        found = Annotations.findFirst(BOOLEAN_TYPE, annotations);
        if (found.isPresent()) {
            return found.get().booleanValues().orElseGet(List::of);
        }
        found = Annotations.findFirst(LONG_TYPE, annotations);
        if (found.isPresent()) {
            return found.get().longValues().orElseGet(List::of);
        }
        return List.of();
    }

    private List<Object> value(Annotation annotation, GenericType<?> expectedType) {
        List<String> values = annotation.stringValues().orElseGet(List::of);

        return values.stream()
                .map(it -> mappers.get().map(it, GenericType.STRING, expectedType, "defaults"))
                .collect(Collectors.toUnmodifiableList());
    }

    @SuppressWarnings({"UnnecessaryLocalVariable", "unchecked", "rawtypes"})
    private List<Object> provider(Annotation annotation, GenericType<?> expectedType, String name, Object context) {
        // there may be a provider
        TypeName valueProviderType = annotation.typeValue()
                .orElseThrow(() -> new IllegalStateException("Invalid definition of " + PROVIDER_TYPE.fqName()
                                                                     + " annotation, missing value."));

        Object provider = registry.first(valueProviderType)
                .orElseThrow(() -> new ServiceRegistryException(
                        "Default value provider: " + valueProviderType.fqName()
                                + " must be available through service registry."
                                + "Either a service annotation is missing (such as @Service.Singleton), "
                                + "or annotation processor was not run."));

        if (!(provider instanceof DefaultValueProvider valueProviderInstance)) {
            throw new IllegalArgumentException("Default value provider must implement "
                                                       + DefaultValueProvider.class.getName()
                                                       + " but " + valueProviderType.fqName() + " does not, yet it "
                                                       + "is used as default value provider.");
        }

        Object result;
        try {
            result = valueProviderInstance.apply(expectedType,
                                                 name,
                                                 context);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Default value provider "
                                                       + valueProviderType.fqName()
                                                       + " context parameter does not match the context as provided by the "
                                                       + "component resolving the default value."
                                                       + " Provided context type: " + context.getClass().getName(),
                                               e);
        }

        if (result instanceof List) {
            List<Object> list = (List<Object>) result;
            return list;
        }
        return List.of(result);
    }
}
