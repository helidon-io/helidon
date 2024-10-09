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

package io.helidon.testing;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Stream;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigSource;
import io.helidon.service.registry.ExistingInstanceDescriptor;
import io.helidon.service.registry.ServiceRegistryConfig;

/**
 * Used by Helidon test extension to set up configuration with service registry.
 */
public final class ConfigRegistrySupport {
    private ConfigRegistrySupport() {
    }

    /**
     * Set up the service registry with configuration from the test class' annotations.
     *
     * @param serviceRegistryConfig configuration of registry
     * @param testClass             test class to process
     */
    public static void setUp(ServiceRegistryConfig.Builder serviceRegistryConfig, Class<?> testClass) {
        SetUpUtil.profile(testClass);

        SetUpUtil.value(testClass)
                .forEach(it -> {
                    ConfigSource source = SetUpUtil.createValueSource(it);
                    serviceRegistryConfig.addServiceDescriptor(ExistingInstanceDescriptor.create(source,
                                                                                                 Set.of(ConfigSource.class),
                                                                                                 it.weight()));
                });
        values(serviceRegistryConfig, testClass);
        SetUpUtil.file(testClass)
                .forEach(it -> {
                    SetUpUtil.createFileSource(it)
                            .forEach(source -> {
                                var desc = ExistingInstanceDescriptor.create(source,
                                                                             Set.of(ConfigSource.class),
                                                                             it.weight());
                                serviceRegistryConfig.addServiceDescriptor(desc);
                            });

                });
        sourceMethods(serviceRegistryConfig, testClass);
    }

    private static void sourceMethods(ServiceRegistryConfig.Builder serviceRegistryConfig, Class<?> testClass) {
        Stream.of(testClass.getDeclaredMethods())
                .filter(it -> it.getAnnotation(TestConfig.Source.class) != null)
                .forEach(method -> {
                    TestConfig.Source annotation = method.getAnnotation(TestConfig.Source.class);
                    // non-private static method, return type must be ConfigSource
                    int modifiers = method.getModifiers();
                    if (!Modifier.isStatic(modifiers)) {
                        throw new TestException("Method " + method.getName() + " is annotated with "
                                                        + TestConfig.Source.class.getName() + " but it is not static.");
                    }
                    if (Modifier.isPrivate(modifiers)) {
                        throw new TestException("Method " + method.getName() + " is annotated with "
                                                        + TestConfig.Source.class.getName() + " but it is private.");
                    }
                    if (!ConfigSource.class.isAssignableFrom(method.getReturnType())) {
                        throw new TestException("Method " + method.getName() + " is annotated with "
                                                        + TestConfig.Source.class.getName() + " but it does not return "
                                                        + ConfigSource.class.getName());
                    }
                    if (method.getParameterCount() != 0) {
                        throw new TestException("Method " + method.getName() + " is annotated with "
                                                        + TestConfig.Source.class.getName() + " but it has parameters");
                    }

                    // now we have a candidate that is good
                    try {
                        method.setAccessible(true);
                        ConfigSource source = (ConfigSource) method.invoke(null);
                        serviceRegistryConfig.addServiceDescriptor(ExistingInstanceDescriptor.create(source,
                                                                                                     Set.of(ConfigSource.class),
                                                                                                     annotation.weight()));
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new TestException("Failed to invoke " + method.getName() + " method to obtain a config source");
                    }
                });
    }

    private static void values(ServiceRegistryConfig.Builder serviceRegistryConfig, Class<?> testClass) {
        TestConfig.Block annotation = testClass.getAnnotation(TestConfig.Block.class);
        if (annotation == null) {
            return;
        }
        var mediaType = MediaTypes.detectExtensionType(annotation.type());
        if (mediaType.isEmpty()) {
            throw new TestException("No extension media type found for extension " + annotation.type() + ", for "
                                            + "annotation " + annotation.annotationType().getName());
        }
        var source = ConfigSources.create(annotation.value(), mediaType.get());
        serviceRegistryConfig.addServiceDescriptor(ExistingInstanceDescriptor.create(source,
                                                                                     Set.of(ConfigSource.class),
                                                                                     annotation.weight()));
    }

}
