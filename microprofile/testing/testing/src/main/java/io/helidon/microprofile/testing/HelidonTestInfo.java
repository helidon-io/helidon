/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.lang.annotation.Annotation;
import java.lang.ref.SoftReference;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.helidon.microprofile.testing.Instrumented.isInstrumented;
import static java.util.stream.Collectors.joining;

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
     * @param cls class
     * @return ClassInfo
     */
    static ClassInfo classInfo(Class<?> cls) {
        return classInfo(cls, HelidonTestDescriptorImpl::new);
    }

    /**
     * Create a new class info.
     *
     * @param cls      class
     * @param function descriptor factory
     * @return ClassInfo
     */
    static ClassInfo classInfo(Class<?> cls, Function<Class<?>, HelidonTestDescriptor<Class<?>>> function) {
        Class<?> theClass = isInstrumented(cls) ? cls.getSuperclass() : cls;
        return ClassInfo.CACHE.compute(theClass.getName(), (e, r) -> {
            if (r == null || r.get() == null) {
                return new SoftReference<>(new ClassInfo(function.apply(cls)));
            }
            return r;
        }).get();
    }

    /**
     * Create a new class info.
     *
     * @param desc class descriptor
     * @return ClassInfo
     */
    static ClassInfo classInfo(HelidonTestDescriptor<Class<?>> desc) {
        return ClassInfo.CACHE.compute(desc.element().getName(), (e, r) -> {
            if (r == null || r.get() == null) {
                return new SoftReference<>(new ClassInfo(desc));
            }
            return r;
        }).get();
    }

    /**
     * Create a new method info.
     *
     * @param element   method
     * @param classInfo class info
     * @return MethodInfo
     */
    static MethodInfo methodInfo(Method element, ClassInfo classInfo) {
        return methodInfo(element, classInfo, HelidonTestDescriptorImpl::new);
    }

    /**
     * Create a new method info.
     *
     * @param method    method
     * @param classInfo class info
     * @param function  descriptor factory
     * @return MethodInfo
     */
    static MethodInfo methodInfo(Method method, ClassInfo classInfo, Function<Method, HelidonTestDescriptor<Method>> function) {
        return MethodInfo.CACHE.compute(MethodInfo.cacheKey(method, classInfo), (e, r) -> {
            if (r == null || r.get() == null) {
                return new SoftReference<>(new MethodInfo(function.apply(method), classInfo));
            }
            return r;
        }).get();
    }

    /**
     * Create a new method info.
     *
     * @param desc      method descriptor
     * @param classInfo class info
     * @return MethodInfo
     */
    static MethodInfo methodInfo(HelidonTestDescriptor<Method> desc, ClassInfo classInfo) {
        return MethodInfo.CACHE.compute(MethodInfo.cacheKey(desc.element(), classInfo), (e, r) -> {
            if (r == null || r.get() == null) {
                return new SoftReference<>(new MethodInfo(desc, classInfo));
            }
            return r;
        }).get();
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
     * Get the class info.
     *
     * @return ClassInfo
     */
    ClassInfo classInfo();

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
    default boolean requiresReset() {
        return false;
    }

    /**
     * Class info.
     */
    final class ClassInfo extends HelidonTestDescriptorDelegate<Class<?>> implements HelidonTestInfo<Class<?>> {
        private static final Map<String, SoftReference<ClassInfo>> CACHE = new ConcurrentHashMap<>();

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

        @Override
        public ClassInfo classInfo() {
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ClassInfo that)) {
                return false;
            }
            return Objects.equals(element().getName(), that.element().getName());
        }

        @Override
        public int hashCode() {
            return element().getName().hashCode();
        }

        @Override
        public String toString() {
            return new PrettyPrinter()
                    .object(PrettyPrinters.classInfo(this))
                    .toString();
        }
    }

    /**
     * Method info.
     */
    final class MethodInfo implements HelidonTestInfo<Method> {
        private static final Map<String, SoftReference<MethodInfo>> CACHE = new ConcurrentHashMap<>();

        private final HelidonTestDescriptor<Method> descriptor;
        private final ClassInfo classInfo;

        private MethodInfo(HelidonTestDescriptor<Method> descriptor, ClassInfo classInfo) {
            this.descriptor = descriptor;
            this.classInfo = classInfo;
        }

        private static String cacheKey(Method method, ClassInfo classInfo) {
            return classInfo.element().getName() + "#"
                   + method.getName()
                   + Arrays.stream(method.getParameterTypes())
                           .map(Type::getTypeName)
                           .collect(joining(",", "(", ")"));
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
        public ClassInfo classInfo() {
            return classInfo;
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
        public <A extends Annotation, C extends Annotation> Stream<A> annotations(Class<A> aType,
                                                                                  Class<C> cType,
                                                                                  Function<C, A[]> function) {
            return Stream.concat(
                    descriptor.annotations(aType, cType, function),
                    classInfo.annotations(aType, cType, function));
        }

        @Override
        public <A extends Annotation> Stream<A> annotations(Class<A> aType) {
            return Stream.concat(
                    descriptor.annotations(aType),
                    classInfo.annotations(aType));
        }

        @Override
        public boolean requiresReset() {
            return classInfo.resetPerTest()
                   || descriptor.configuration().isPresent()
                   || descriptor.disableDiscovery()
                   || descriptor.addJaxRs()
                   || !descriptor.addBeans().isEmpty()
                   || !descriptor.addExtensions().isEmpty();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MethodInfo that)) {
                return false;
            }
            return Objects.equals(element().getName(), that.element().getName());
        }

        @Override
        public int hashCode() {
            return element().hashCode();
        }

        @Override
        public String toString() {
            return new PrettyPrinter()
                    .object(PrettyPrinters.methodInfo(this))
                    .toString();
        }

        private static <T> List<T> concat(List<T> l1, List<T> l2) {
            return Stream.concat(l1.stream(), l2.stream()).toList();
        }
    }
}
