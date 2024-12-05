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
import java.util.stream.Stream;

/**
 * Metadata of the test class or method that triggers the creation of a CDI container.
 *
 * @param <T> element type
 */
public sealed interface HelidonTestInfo<T extends AnnotatedElement> extends HelidonTestDescriptor<T>
        permits HelidonTestInfo.ClassInfo,
                HelidonTestInfo.MethodInfo {

    /**
     * Create a new class info.
     *
     * @param element class
     * @return ClassInfo
     */
    static ClassInfo classInfo(Class<?> element) {
        return new ClassInfo(new HelidonTestDescriptorImpl<>(element));
    }

    /**
     * Create a new class info.
     *
     * @param descriptor class descriptor
     * @return ClassInfo
     */
    static ClassInfo classInfo(HelidonTestDescriptor<Class<?>> descriptor) {
        return new ClassInfo(descriptor);
    }

    /**
     * Create a new method info.
     *
     * @param element   method
     * @param classInfo class info
     * @return MethodInfo
     */
    static MethodInfo methodInfo(Method element, ClassInfo classInfo) {
        return new MethodInfo(new HelidonTestDescriptorImpl<>(element), classInfo);
    }

    /**
     * Create a new method info.
     *
     * @param descriptor method descriptor
     * @param classInfo  class info
     * @return MethodInfo
     */
    static MethodInfo methodInfo(HelidonTestDescriptor<Method> descriptor, ClassInfo classInfo) {
        return new MethodInfo(descriptor, classInfo);
    }

    /**
     * Get the id.
     *
     * @return id
     */
    String id();

    /**
     * Get the test class.
     *
     * @return test class
     */
    Class<?> testClass();

    /**
     * Get the test method.
     *
     * @return test method
     */
    default Optional<Method> testMethod() {
        return Optional.empty();
    }

    /**
     * Class info.
     */
    final class ClassInfo extends HelidonTestDescriptorDelegate<Class<?>> implements HelidonTestInfo<Class<?>> {

        private ClassInfo(HelidonTestDescriptor<Class<?>> descriptor) {
            super(descriptor);
        }

        @Override
        public String id() {
            return element().getName();
        }

        @Override
        public Class<?> testClass() {
            return element();
        }
    }

    /**
     * Method info.
     */
    final class MethodInfo implements HelidonTestInfo<Method> {

        private final HelidonTestDescriptor<Method> descriptor;
        private final ClassInfo classInfo;

        private MethodInfo(HelidonTestDescriptor<Method> descriptor, ClassInfo classInfo) {
            this.descriptor = descriptor;
            this.classInfo = classInfo;
        }

        @Override
        public String id() {
            return classInfo.id() + "#" + element().getName();
        }

        @Override
        public Class<?> testClass() {
            return classInfo.element();
        }

        @Override
        public Optional<Method> testMethod() {
            return Optional.of(descriptor.element());
        }

        @Override
        public Method element() {
            return descriptor.element();
        }

        @Override
        public List<AddExtension> addExtensions() {
            return concat(classInfo.addExtensions(), descriptor.addExtensions());
        }

        @Override
        public List<AddBean> addBeans() {
            return concat(classInfo.addBeans(), descriptor.addBeans());
        }

        @Override
        public boolean addJaxRs() {
            return classInfo.addJaxRs() || descriptor.addJaxRs();
        }

        @Override
        public boolean disableDiscovery() {
            return classInfo.disableDiscovery() || descriptor.disableDiscovery();
        }

        @Override
        public Optional<Configuration> configuration() {
            return descriptor.configuration();
        }

        @Override
        public List<AddConfig> addConfigs() {
            return concat(classInfo.addConfigs(), descriptor.addConfigs());
        }

        @Override
        public List<AddConfigBlock> addConfigBlocks() {
            return concat(classInfo.addConfigBlocks(), descriptor.addConfigBlocks());
        }

        @Override
        public List<Method> addConfigSources() {
            return classInfo.addConfigSources();
        }

        /**
         * Indicate if the container should be reset.
         * For a class this is resolved via {@code HelidonTest#resetPerTest()}.
         * For a method this is inferred if any of the following annotations is used:
         * <ul>
         *     <li>{@link Configuration}</li>
         *     <li>{@link AddExtension}</li>
         *     <li>{@link AddBean}</li>
         *     <li>{@link AddJaxRs}</li>
         *     <li>{@link DisableDiscovery}</li>
         * </ul>
         *
         * @return {@code true} if reset is required, {@code false} otherwise
         */
        public boolean requiresReset() {
            return classInfo.resetPerTest()
                   || descriptor.configuration().isPresent()
                   || descriptor.disableDiscovery()
                   || descriptor.addJaxRs()
                   || !descriptor.addBeans().isEmpty()
                   || !descriptor.addExtensions().isEmpty();
        }

        private static <T> List<T> concat(List<T> l1, List<T> l2) {
            return Stream.concat(l1.stream(), l2.stream()).toList();
        }
    }
}
