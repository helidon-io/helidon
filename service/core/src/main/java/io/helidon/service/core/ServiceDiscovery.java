package io.helidon.service.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.common.types.TypeName;

class ServiceDiscovery {
    private static final System.Logger LOGGER = System.getLogger(ServiceDiscovery.class.getName());
    private static final String SERVICES_RESOURCE = "META-INF/helidon/service/services.txt";

    // map of contract to declared implementations
    private static final Map<TypeName, Set<WeightedProvider>> ALL_SERVICE_PROVIDER_TYPES = new LinkedHashMap<>();

    static {
        classLoader().resources(SERVICES_RESOURCE)
                .flatMap(ServiceDiscovery::loadLines)
                .filter(Predicate.not(Line::isEmpty))
                .filter(Predicate.not(Line::isComment))
                .flatMap(ServiceDiscovery::parseLine)
                .forEach(ServiceDiscovery::addProvider);
    }

    static <T> Optional<Class<? extends T>> first(Class<T> contract) {
        Set<WeightedProvider> providers = ALL_SERVICE_PROVIDER_TYPES.get(TypeName.create(contract));
        if (providers == null) {
            return Optional.empty();
        }
        return providers.stream()
                .findFirst()
                .map(ServiceDiscovery::toClass);
    }

    static <T> List<Class<? extends T>> all(Class<T> contract) {
        Set<WeightedProvider> providers = ALL_SERVICE_PROVIDER_TYPES.get(TypeName.create(contract));
        if (providers == null) {
            return List.of();
        }
        return providers.stream()
                .map((Function<? super WeightedProvider, Class<? extends T>>) ServiceDiscovery::toClass)
                .toList();
    }

    @SuppressWarnings("unchecked")
    static <T> T descriptorInstance(Class<?> clazz, String methodName, Class<?>[] argumentTypes, Object[] parameters) {
        try {
            Method declaredMethod = clazz.getDeclaredMethod(methodName, argumentTypes);
            declaredMethod.setAccessible(true);
            return (T) declaredMethod.invoke(null, parameters);
        } catch (ReflectiveOperationException e) {
            throw new ServiceRegistryException("Failed to invoke static method \"" + methodName
                                                       + "\" for type \"" + clazz.getName() + "\"",
                                               e);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T instantiate(Class<?> clazz, String methodName, Class<?>[] argumentTypes, Object[] parameters) {
        try {
            Method declaredMethod = clazz.getDeclaredMethod(methodName, argumentTypes);
            declaredMethod.setAccessible(true);
            return (T) declaredMethod.invoke(null, parameters);
        } catch (ReflectiveOperationException e) {
            throw new ServiceRegistryException("Failed to invoke static method \"" + methodName
                                                       + "\" for type \"" + clazz.getName() + "\"",
                                               e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> Class<? extends T> toClass(WeightedProvider weightedProvider) {
        try {
            return (Class) Class.forName(weightedProvider.provider.fqName());
        } catch (ClassNotFoundException e) {
            throw new ServiceRegistryException("Resolution of type \"" + weightedProvider.provider().fqName()
                                                       + "\" to class failed."
                                                       + " Requested through contract \"" + weightedProvider.contract().fqName(),
                                               e);
        }

    }

    private static void addProvider(WeightedProvider weightedProvider) {
        ALL_SERVICE_PROVIDER_TYPES.computeIfAbsent(weightedProvider.contract(),
                                                   it -> new TreeSet<>(providerComparator()))
                .add(weightedProvider);
    }

    private static Stream<WeightedProvider> parseLine(Line line) {
        // io.helidon.common.Contract:io.helidon.common.ContractImpl:100.2
        String[] components = line.line().split(":");
        if (components.length < 3) {
            // allow more, if we need more info in the future, to be backward compatible for libraries
            LOGGER.log(System.Logger.Level.WARNING,
                       "Line " + line.lineNumber() + " of " + line.source()
                               + " is invalid, should be contract:provider:weight");
        }
        try {
            TypeName contract = TypeName.create(components[0]);
            TypeName provider = TypeName.create(components[1]);
            double weight = Double.parseDouble(components[2]);

            return Stream.of(new WeightedProvider(contract, provider, weight));
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING,
                       "Line " + line.lineNumber() + " of " + line.source()
                               + " is invalid, should be contract:provider:weight",
                       e);
            return Stream.empty();
        }
    }

    private static Stream<Line> loadLines(URL url) {
        return null;
    }

    static ClassLoader classLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            return ServiceDiscovery.class.getClassLoader();
        }
        return cl;
    }

    private record Line(URI source, String line, int lineNumber) {
        boolean isEmpty() {
            return line.isEmpty();
        }

        boolean isComment() {
            return line.startsWith("#");
        }
    }

    private static Comparator<WeightedProvider> providerComparator() {
        return Comparator.comparing(WeightedProvider::weight).reversed();
    }

    private record WeightedProvider(TypeName contract, TypeName provider, double weight) {
    }
}
