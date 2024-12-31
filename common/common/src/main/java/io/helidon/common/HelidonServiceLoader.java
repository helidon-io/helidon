/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.common;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.System.Logger.Level.TRACE;

/**
 * Helidon specific support for Java Service Loaders.
 * <p>
 * This service loader:
 * <ul>
 * <li>Can have additional implementations added</li>
 * <li>Uses weights defined either by {@link Weighted}
 * or by {@link Weight}</li>
 * <li>Can have exclusions defined by an exact implementation class name, either
 * in {@link HelidonServiceLoader.Builder#addExcludedClass(Class)} or
 * {@link HelidonServiceLoader.Builder#addExcludedClassName(String)} or
 * by a system property {@value #SYSTEM_PROPERTY_EXCLUDE} that defines
 * a comma separated list of fully qualified class names to be excluded.
 * Note that if a class implements more than one service, it would be excluded from all.</li>
 * </ul>
 * <p>
 * <i>Note on weight handling</i>
 * <p>
 * Service weight is defined by:
 * <ul>
 * <li>Value provided in {@link io.helidon.common.HelidonServiceLoader.Builder#addService(Object, double)} (if used)</li>
 * <li>then by {@link Weighted#weight()} if service implements it</li>
 * <li>then by {@link Weight} annotation if present</li>
 * <li>otherwise a default weight {@value Weighted#DEFAULT_WEIGHT} from {@link Weighted#DEFAULT_WEIGHT} is used</li>
 * </ul>
 * Example:
 * <pre>
 * {@literal @}Weight(4500)
 * public class MyServiceImpl implements Service, Weighted {
 *     public double weight() {
 *         return 6200;
 *     }
 * }
 * </pre>
 * Such a service would have a weight of {@code 6200} as that is more significant than the annotation.
 * <p>
 * A service with higher weight is returned before a service with a lower weight number.
 * Services with the same weight have order defined by the order they are in the configured services
 * and then as they are loaded from the {@link java.util.ServiceLoader}.
 * Negative weights are not allowed.
 * A service with weight {@code 2} will be returned before a service with weight {@code 2}.
 *
 * @param <T> Type of the service to be loaded
 * @see java.util.ServiceLoader
 * @see #builder(java.util.ServiceLoader)
 */
public final class HelidonServiceLoader<T> implements Iterable<T> {
    /**
     * System property used to exclude some implementation from the list of services that are configured for Java Service
     * loader or services that are registered using {@link io.helidon.common.HelidonServiceLoader.Builder}.
     */
    public static final String SYSTEM_PROPERTY_EXCLUDE = "io.helidon.common.serviceloader.exclude";

    private static final System.Logger LOGGER = System.getLogger(HelidonServiceLoader.class.getName());

    private final List<T> services;

    private HelidonServiceLoader(List<T> services) {
        this.services = new LinkedList<>(services);
    }

    /**
     * Create a builder for customizable service loader.
     *
     * @param serviceLoader the Java Service loader used to get service implementations
     * @param <T>           type of the service
     * @return a new fluent API builder
     */
    public static <T> Builder<T> builder(ServiceLoader<T> serviceLoader) {
        return new Builder<>(serviceLoader);
    }

    /**
     * A shortcut method to create a service loader based on the provider interface directly.
     *
     * @param theProviderInterface provider interface
     * @return service loader
     * @param <T> type of the service
     */
    public static <T> HelidonServiceLoader<T> create(Class<T> theProviderInterface) {
        HelidonServiceLoader.class.getModule()
                .addUses(theProviderInterface);
        return create(ServiceLoader.load(theProviderInterface));
    }

    /**
     * Create a weighted service loader from a Java Service loader.
     *
     * @param serviceLoader the Java service loader
     * @param <T>           type of the service
     * @return service loader with exclusions defined by system properties and no custom services
     */
    public static <T> HelidonServiceLoader<T> create(ServiceLoader<T> serviceLoader) {
        Builder<T> builder = builder(serviceLoader);
        return builder.build();
    }

    @Override
    public Iterator<T> iterator() {
        return Collections.unmodifiableList(services)
                .iterator();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        this.services.forEach(action);
    }

    /**
     * Provides a list of service implementations in weighted order.
     *
     * @return list of service implementations
     */
    public List<T> asList() {
        return new LinkedList<>(this.services);
    }

    /**
     * Provides a stream of service implementations, in weighted order.
     *
     * @return stream os service implementations
     */
    public Stream<T> stream() {
        return asList().stream();
    }

    /**
     * Fluent api builder for {@link io.helidon.common.HelidonServiceLoader}.
     *
     * @param <T> type of the service to be loaded
     */
    public static final class Builder<T> implements io.helidon.common.Builder<Builder<T>, HelidonServiceLoader<T>> {
        private final ServiceLoader<T> serviceLoader;
        private final List<ServiceWithWeight<T>> customServices = new LinkedList<ServiceWithWeight<T>>();
        private final Set<String> excludedServiceClasses = new HashSet<>();
        private boolean useSysPropExclude = true;
        private boolean useSystemServiceLoader = true;
        private boolean replaceImplementations = true;
        private double defaultWeight = Weighted.DEFAULT_WEIGHT;

        private Builder(ServiceLoader<T> serviceLoader) {
            this.serviceLoader = serviceLoader;
        }

        @Override
        public HelidonServiceLoader<T> build() {
            // first merge the lists together
            List<ServiceWithWeight<T>> services = new LinkedList<>(customServices);
            if (useSystemServiceLoader) {
                Set<String> uniqueImplementations = new HashSet<>();

                if (replaceImplementations) {
                    customServices.stream()
                            .map(ServiceWithWeight::instanceClassName)
                            .forEach(uniqueImplementations::add);
                }

                serviceLoader.forEach(service -> {
                    if (replaceImplementations) {
                        if (!uniqueImplementations.contains(service.getClass().getName())) {
                            services.add(ServiceWithWeight.createFindWeight(service, defaultWeight));
                        }
                    } else {
                        services.add(ServiceWithWeight.createFindWeight(service, defaultWeight));
                    }
                });
            }

            if (useSysPropExclude) {
                addSystemExcludes();
            }
            List<ServiceWithWeight<T>> withoutExclusions = services.stream()
                    .filter(this::notExcluded)
                    .collect(Collectors.toList());

            // order by weight
            return new HelidonServiceLoader<>(orderByWeight(withoutExclusions));
        }

        /**
         * When configured to use system excludes, system property {@value #SYSTEM_PROPERTY_EXCLUDE} is used to get the
         * comma separated list of service implementations to exclude them from the loaded list.
         * <p>
         * This defaults to {@code true}.
         *
         * @param useSysPropExclude whether to use a system property to exclude service implementations
         * @return updated builder instance
         */
        public Builder<T> useSystemExcludes(boolean useSysPropExclude) {
            this.useSysPropExclude = useSysPropExclude;
            return this;
        }

        /**
         * When configured to use Java Service loader, then the result is a combination of all service implementations
         * loaded from the Java Service loader and those added by {@link #addService(Object)} or
         * {@link #addService(Object, double)}.
         * When set to {@code false} the Java Service loader is ignored.
         * <p>
         * This defaults to {@code true}.
         *
         * @param useServiceLoader whether to use the Java Service loader
         * @return updated builder instance
         */
        public Builder<T> useSystemServiceLoader(boolean useServiceLoader) {
            this.useSystemServiceLoader = useServiceLoader;
            return this;
        }

        /**
         * When configured to replace implementations, then a service implementation configured through
         * {@link #addService(Object)}
         * will replace the same implementation loaded from the Java Service loader (compared by fully qualified class name).
         * <p>
         * This defaults to {@code true}.
         *
         * @param replace whether to replace service instances loaded by java service loader with the ones provided
         *                through builder methods
         * @return updated builder instance
         */
        public Builder<T> replaceImplementations(boolean replace) {
            this.replaceImplementations = replace;
            return this;
        }

        /**
         * Add a custom service implementation to the list of services.
         *
         * @param service a new service instance
         * @return updated builder instance
         */
        public Builder<T> addService(T service) {
            this.customServices.add(ServiceWithWeight.createFindWeight(service, defaultWeight));
            return this;
        }

        /**
         * Add a custom service implementation to the list of services with a custom weight.
         *
         * @param service a new service instance
         * @param weight  weight to use when ordering service instances
         * @return updated builder instance
         */
        public Builder<T> addService(T service, double weight) {
            this.customServices.add(ServiceWithWeight.create(service, weight));
            return this;
        }

        /**
         * Add an excluded implementation class - if such a service implementation is configured (either through
         * Java Service loader or through {@link #addService(Object)}), it would be ignored.
         *
         * @param excluded excluded implementation class
         * @return updated builder instance
         */
        public Builder<T> addExcludedClass(Class<? extends T> excluded) {
            excludedServiceClasses.add(excluded.getName());
            return this;
        }

        /**
         * Add an excluded implementation class - if such a service implementation is configured (either through
         * Java Service loader or through {@link #addService(Object)}), it would be ignored.
         *
         * @param excludeName excluded implementation class name
         * @return updated builder instance
         */
        public Builder<T> addExcludedClassName(String excludeName) {
            excludedServiceClasses.add(excludeName);
            return this;
        }

        /**
         * Configure default weight for services that do not have any.
         *
         * @param defaultWeight default weight to use, defaults to {@link Weighted#DEFAULT_WEIGHT}
         * @return updated builder instance
         */
        public Builder<T> defaultWeight(double defaultWeight) {
            this.defaultWeight = defaultWeight;
            return this;
        }

        private boolean notExcluded(ServiceWithWeight<T> service) {
            String className = service.instance.getClass().getName();
            if (excludedServiceClasses.contains(className)) {
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "Excluding service implementation " + className);
                }
                return false;
            }
            return true;
        }

        private List<T> orderByWeight(List<ServiceWithWeight<T>> services) {
            Collections.sort(services);

            List<T> result = services.stream()
                    .map(ServiceWithWeight::instance)
                    .collect(Collectors.toList());

            if (LOGGER.isLoggable(TRACE)) {
                List<String> names = result.stream()
                        .map(Object::getClass)
                        .map(Class::getName)
                        .collect(Collectors.toList());
                LOGGER.log(TRACE, "Final order of enabled service implementations for service: " + serviceLoader + "\n"
                        + String.join("\n", names));
            }

            return result;
        }

        private void addSystemExcludes() {
            String excludes = System.getProperty(SYSTEM_PROPERTY_EXCLUDE);
            if (null == excludes) {
                return;
            }

            for (String exclude : excludes.split(",")) {
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "Adding exclude from system properties: " + exclude);
                }
                addExcludedClassName(exclude);
            }
        }

        private static final class ServiceWithWeight<T> implements Weighted {
            private final T instance;
            private final double weight;

            private ServiceWithWeight(T instance, double weight) {
                this.instance = instance;
                this.weight = weight;

                if (weight < 0) {
                    throw new IllegalArgumentException("Service: "
                                                               + instance.getClass().getName()
                                                               + " declares a negative weight, which is not allowed. Weight: "
                                                               + weight);
                }
            }

            @Override
            public String toString() {
                return instance.toString();
            }

            @Override
            public double weight() {
                return weight;
            }

            private static <T> ServiceWithWeight<T> create(T instance, double weight) {
                return new ServiceWithWeight<>(instance, weight);
            }

            private static <T> ServiceWithWeight<T> createFindWeight(T instance, double defaultWeight) {
                return new ServiceWithWeight<>(instance, Weights.find(instance, defaultWeight));
            }

            private T instance() {
                return instance;
            }

            private String instanceClassName() {
                return instance.getClass().getName();
            }
        }
    }
}
