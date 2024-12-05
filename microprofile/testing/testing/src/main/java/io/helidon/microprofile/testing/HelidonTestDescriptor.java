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
package io.helidon.microprofile.testing;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import jakarta.enterprise.inject.spi.Extension;

/**
 * Describes annotations for a test class or method.
 *
 * @param <T> element type
 */
public interface HelidonTestDescriptor<T extends AnnotatedElement> {

    /**
     * Get the annotated element.
     *
     * @return element
     */
    T element();

    /**
     * Get the discovered {@link AddExtension} annotations.
     *
     * @return annotations
     */
    List<AddExtension> addExtensions();

    /**
     * Get the discovered {@link AddBean} annotations.
     *
     * @return annotations
     */
    List<AddBean> addBeans();

    /**
     * Get the discovered {@link AddJaxRs} annotation.
     *
     * @return annotation
     */
    Optional<AddJaxRs> addJaxRs();

    /**
     * Get the discovered {@link DisableDiscovery} annotation.
     *
     * @return annotation
     */
    Optional<DisableDiscovery> disableDiscovery();

    /**
     * Get the discovered {@link Configuration} annotation.
     *
     * @return annotation
     */
    Optional<Configuration> configuration();

    /**
     * Get the discovered {@link AddConfig} annotations.
     *
     * @return annotations
     */
    List<AddConfig> addConfigs();

    /**
     * Get the discovered {@link AddConfigBlock} annotations.
     *
     * @return annotations
     */
    List<AddConfigBlock> addConfigBlocks();

    /**
     * Test if the given extension is configured.
     *
     * @param type extension type
     * @return {@code true} if configured, {@code false} otherwise
     */
    default boolean containsExtension(Class<? extends Extension> type) {
        return addExtensions().stream()
                .map(AddExtension::value)
                .anyMatch(Predicate.isEqual(type));
    }

    /**
     * Class descriptor.
     */
    interface ClassDescriptor extends HelidonTestDescriptor<Class<?>> {

        /**
         * Get the discovered value of {@code @HelidonTest(resetPerTest = true)}.
         *
         * @return {@code resetPerTest} value
         */
        boolean resetPerTest();

        /**
         * Get the discovered {@link AddConfigSource} methods.
         *
         * @return annotations
         */
        List<Method> addConfigSources();
    }
}
