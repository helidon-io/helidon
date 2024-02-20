/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.registry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.LazyValue;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.GeneratedService.Descriptor;

import static java.nio.charset.StandardCharsets.UTF_8;

class CoreServiceDiscovery implements ServiceDiscovery {
    private static final System.Logger LOGGER = System.getLogger(CoreServiceDiscovery.class.getName());
    private static final String SERVICES_RESOURCE = "META-INF/helidon/services/services.txt";

    private final List<DescriptorMetadata> allDescriptors;

    private CoreServiceDiscovery() {
        Map<TypeName, DescriptorMetadata> allDescriptors = new LinkedHashMap<>();

        classLoader().resources(SERVICES_RESOURCE)
                .flatMap(CoreServiceDiscovery::loadLines)
                .filter(Predicate.not(Line::isEmpty))
                .filter(Predicate.not(Line::isComment))
                .flatMap(DescriptorMeta::parse)
                .forEach(it -> {
                    allDescriptors.putIfAbsent(it.descriptorType(), it);
                });

        this.allDescriptors = List.copyOf(allDescriptors.values());
    }

    static ServiceDiscovery create() {
        return new CoreServiceDiscovery();
    }

    static ServiceDiscovery noop() {
        return NoopServiceDiscovery.INSTANCE;
    }

    @Override
    public List<DescriptorMetadata> allMetadata() {
        return allDescriptors;
    }

    private static ClassLoader classLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            return CoreServiceDiscovery.class.getClassLoader();
        }
        return cl;
    }

    private static Stream<Line> loadLines(URL url) {

        try (BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream(), UTF_8))) {
            List<Line> lines = new ArrayList<>();

            String line;
            int lineNumber = 0;

            while ((line = br.readLine()) != null) {
                lineNumber++; // we want to start with 1
                lines.add(new Line(url.toString(), line, lineNumber));
            }

            return lines.stream();
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to read services from " + url, e);
            return Stream.of();
        }
    }

    private record Line(String source, String line, int lineNumber) {
        boolean isEmpty() {
            return line.isEmpty();
        }

        boolean isComment() {
            return line.startsWith("#");
        }
    }

    private record DescriptorMeta(String registryType,
                                  TypeName descriptorType,
                                  double weight,
                                  Set<TypeName> contracts,
                                  LazyValue<Descriptor<?>> descriptorSupplier) implements DescriptorMetadata {
        DescriptorMeta(String registryType, TypeName descriptorType, double weight, Set<TypeName> contract) {
            this(registryType, descriptorType, weight, contract, LazyValue.create(() -> getDescriptorInstance(descriptorType)));
        }

        @Override
        public Descriptor<?> descriptor() {
            return descriptorSupplier.get();
        }

        private static Stream<DescriptorMeta> parse(Line line) {
            // core:io.helidon.ContractImpl__ServiceDescriptor:101.3:io.helidon.Contract,io.helidon.Contract2
            // inject:io.helidon.ContractImpl__ServiceDescriptor:101.3:io.helidon.Contract,io.helidon.Contract2
            String[] components = line.line().split(":");
            if (components.length < 4) {
                // allow more, if we need more info in the future, to be backward compatible for libraries
                LOGGER.log(System.Logger.Level.WARNING,
                           "Line " + line.lineNumber() + " of " + line.source()
                                   + " is invalid, should be registry-type:service-descriptor:weight:contracts");
            }
            try {
                String registryType = components[0];
                TypeName descriptor = TypeName.create(components[1]);
                double weight = Double.parseDouble(components[2]);

                Set<TypeName> contracts = Stream.of(components[3].split(","))
                        .map(String::trim)
                        .map(TypeName::create)
                        .collect(Collectors.toSet());

                return Stream.of(new DescriptorMeta(registryType, descriptor, weight, contracts));
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "Line " + line.lineNumber() + " of " + line.source()
                                   + " is invalid, should be service-descriptor:weight:contracts",
                           e);
                return Stream.empty();
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static <T> Class<? extends T> toClass(TypeName descriptorType) {
            try {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                cl = (cl == null) ? CoreServiceDiscovery.class.getClassLoader() : cl;

                return (Class) cl.loadClass(descriptorType.fqName());
            } catch (ClassNotFoundException e) {
                throw new ServiceRegistryException("Resolution of service descriptor \"" + descriptorType.fqName()
                                                           + "\" to class failed.",
                                                   e);
            }

        }

        private static Descriptor<?> getDescriptorInstance(TypeName descriptorType) {
            Class<?> clazz = toClass(descriptorType);

            try {
                Field field = clazz.getField("INSTANCE");
                return (Descriptor<?>) field.get(null);
            } catch (ReflectiveOperationException e) {
                throw new ServiceRegistryException("Could not obtain the instance of service descriptor "
                                                           + descriptorType.fqName(),
                                                   e);
            }
        }
    }

    static class NoopServiceDiscovery implements ServiceDiscovery {
        private static final ServiceDiscovery INSTANCE = new NoopServiceDiscovery();

        @Override
        public List<DescriptorMetadata> allMetadata() {
            return List.of();
        }
    }
}

