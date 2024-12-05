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
        return new ClassInfo(new DefaultClassDescriptor(element));
    }

    /**
     * Create a new class info.
     *
     * @param descriptor class descriptor
     * @return ClassInfo
     */
    static ClassInfo classInfo(ClassDescriptor descriptor) {
        return new ClassInfo(descriptor);
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
    Optional<Method> testMethod();

    /**
     * Get the test class info.
     *
     * @return ClassInfo
     */
    ClassInfo classInfo();

    /**
     * Indicate if the container should be reset.
     * For a class this is resolved via {@code @HelidonTest(resetPerTest = true)}.
     * For a method this is inferred if any of the following annotations is used:
     * <ul>
     *     <li>{@link AddExtension}</li>
     *     <li>{@link AddBean}</li>
     *     <li>{@link AddJaxRs}</li>
     *     <li>{@link DisableDiscovery}</li>
     * </ul>
     *
     * @return {@code true} if reset is required, {@code false} otherwise
     */
    boolean requiresReset();

    /**
     * Get the value of {@link DisableDiscovery}.
     *
     * @return {@link DisableDiscovery#value()}
     */
    default boolean discoveryDisabled() {
        return disableDiscovery()
                .map(DisableDiscovery::value)
                .orElse(false);
    }

    /**
     * Class info.
     */
    final class ClassInfo implements HelidonTestInfo<Class<?>>, ClassDescriptor {

        private final ClassDescriptor descriptor;

        private ClassInfo(ClassDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public String id() {
            return descriptor.element().getName();
        }

        @Override
        public ClassInfo classInfo() {
            return this;
        }

        @Override
        public Class<?> testClass() {
            return descriptor.element();
        }

        @Override
        public Optional<Method> testMethod() {
            return Optional.empty();
        }

        @Override
        public Class<?> element() {
            return descriptor.element();
        }

        @Override
        public List<AddExtension> addExtensions() {
            return descriptor.addExtensions();
        }

        @Override
        public List<AddBean> addBeans() {
            return descriptor.addBeans();
        }

        @Override
        public Optional<AddJaxRs> addJaxRs() {
            return descriptor.addJaxRs();
        }

        @Override
        public Optional<DisableDiscovery> disableDiscovery() {
            return descriptor.disableDiscovery();
        }

        @Override
        public Optional<Configuration> configuration() {
            return descriptor.configuration();
        }

        @Override
        public List<AddConfig> addConfigs() {
            return descriptor.addConfigs();
        }

        @Override
        public List<AddConfigBlock> addConfigBlocks() {
            return descriptor.addConfigBlocks();
        }

        @Override
        public List<Method> addConfigSources() {
            return descriptor.addConfigSources();
        }

        @Override
        public boolean resetPerTest() {
            return descriptor.resetPerTest();
        }

        @Override
        public boolean requiresReset() {
            return descriptor.resetPerTest();
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
            return classInfo.element().getName() + "#" + descriptor.element().getName();
        }

        @Override
        public ClassInfo classInfo() {
            return classInfo;
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
        public Optional<AddJaxRs> addJaxRs() {
            return classInfo.addJaxRs()
                    .or(descriptor::addJaxRs);
        }

        @Override
        public Optional<DisableDiscovery> disableDiscovery() {
            return classInfo.disableDiscovery()
                    .or(descriptor::disableDiscovery);
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
        public boolean requiresReset() {
            return descriptor.disableDiscovery().isPresent()
                   || !descriptor.addBeans().isEmpty()
                   || !descriptor.addExtensions().isEmpty();
        }

        private static <T> List<T> concat(List<T> l1, List<T> l2) {
            return Stream.concat(l1.stream(), l2.stream()).toList();
        }
    }

    /**
     * Default class descriptor.
     */
    final class DefaultClassDescriptor extends HelidonTestDescriptorBase<Class<?>>
            implements ClassDescriptor {

        private DefaultClassDescriptor(Class<?> element) {
            super(element);
        }
    }
}
