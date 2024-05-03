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
package io.helidon.microprofile.testing.junit5;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.common.LazyValue;
import io.helidon.microprofile.testing.junit5.ReflectionHelper.Annotated;

import jakarta.enterprise.inject.spi.Extension;

import static io.helidon.microprofile.testing.junit5.ReflectionHelper.annotated;
import static io.helidon.microprofile.testing.junit5.ReflectionHelper.filterAnnotations;

/**
 * Helidon test info.
 * Metadata of the test class or method that triggers the creation of a CDI container.
 */
abstract sealed class HelidonTestInfo permits HelidonTestInfo.ClassInfo, HelidonTestInfo.MethodInfo {

    private final List<Annotated<?>> annotated;
    private final LazyValue<List<AddExtension>> addExtensions = LazyValue.create(this::initAddExtensions);
    private final LazyValue<List<AddBean>> addBeans = LazyValue.create(this::initAddBeans);
    private final LazyValue<Boolean> addJaxRs = LazyValue.create(this::initAddJaxRs);
    private final LazyValue<Boolean> disableDiscovery = LazyValue.create(this::initDisableDiscovery);

    private HelidonTestInfo(List<Annotated<?>> annotated) {
        this.annotated = annotated;
    }

    private List<AddExtension> initAddExtensions() {
        return filterAnnotations(annotated, AddExtensions.class, AddExtension.class, AddExtensions::value);
    }

    private List<AddBean> initAddBeans() {
        return filterAnnotations(annotated, AddBeans.class, AddBean.class, AddBeans::value);
    }

    private boolean initAddJaxRs() {
        return filterAnnotations(annotated, AddJaxRs.class).findFirst()
                .isPresent();
    }

    private boolean initDisableDiscovery() {
        return filterAnnotations(annotated, DisableDiscovery.class).findFirst()
                .map(DisableDiscovery::value)
                .orElse(false);
    }

    /**
     * Get the annotations.
     *
     * @return annotations
     */
    List<Annotated<?>> annotations() {
        return annotated;
    }

    /**
     * Get the id.
     *
     * @return id
     */
    abstract String id();

    /**
     * Get the test class.
     *
     * @return test class
     */
    abstract ClassInfo classInfo();

    /**
     * Get the annotated element.
     *
     * @return element
     */
    abstract AnnotatedElement element();

    /**
     * Indicate if the container should be reset.
     * For a class this is resolved via {@link HelidonTest#resetPerTest()}.
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
    abstract boolean requiresReset();

    /**
     * Get the discovered {@link AddExtension} annotations.
     *
     * @return list of {@link AddExtension} annotations
     */
    List<AddExtension> addExtensions() {
        return addExtensions.get();
    }

    /**
     * Get the discovered {@link AddBean} annotations.
     *
     * @return list of {@link AddBean} annotations
     */
    List<AddBean> addBeans() {
        return addBeans.get();
    }

    /**
     * Get the discovered value of {@link AddJaxRs}.
     *
     * @return discovered value or {@code false} if not found
     */
    boolean addJaxRs() {
        return addJaxRs.get();
    }

    /**
     * Get the discovered value of {@link DisableDiscovery}.
     *
     * @return discovered value or {@code false} if not found
     */
    boolean disableDiscovery() {
        return disableDiscovery.get();
    }

    /**
     * Test if the given extension is configured.
     *
     * @param type extension type
     * @return {@code true} if configured, {@code false} otherwise
     */
    @SuppressWarnings("SameParameterValue")
    boolean containsExtension(Class<? extends Extension> type) {
        return addExtensions().stream()
                .map(AddExtension::value)
                .anyMatch(Predicate.isEqual(type));
    }

    /**
     * Class info.
     */
    static final class ClassInfo extends HelidonTestInfo {

        private final Class<?> element;
        private final LazyValue<Boolean> resetPerTest = LazyValue.create(this::initResetPerTest);

        /**
         * Create a new instance.
         *
         * @param element   test class
         */
        ClassInfo(Class<?> element) {
            super(annotated(element));
            this.element = element;
        }

        private boolean initResetPerTest() {
            return filterAnnotations(super.annotated, HelidonTest.class)
                    .findFirst()
                    .map(HelidonTest::resetPerTest)
                    .orElse(false);
        }

        @Override
        String id() {
            return element.getName();
        }

        @Override
        ClassInfo classInfo() {
            return this;
        }

        @Override
        Class<?> element() {
            return element;
        }

        @Override
        boolean requiresReset() {
            return resetPerTest.get();
        }
    }

    /**
     * Method info.
     */
    static final class MethodInfo extends HelidonTestInfo {

        private final Method element;
        private final ClassInfo classInfo;

        MethodInfo(Method method, ClassInfo classInfo) {
            super(annotated(method));
            this.element = method;
            this.classInfo = classInfo;
        }

        @Override
        String id() {
            return classInfo.element().getName() + "#" + element.getName();
        }

        @Override
        ClassInfo classInfo() {
            return classInfo;
        }

        @Override
        Method element() {
            return element;
        }

        @Override
        List<AddExtension> addExtensions() {
            return Stream.of(classInfo.addExtensions(), super.addExtensions())
                    .flatMap(Collection::stream)
                    .toList();
        }

        @Override
        List<AddBean> addBeans() {
            return Stream.of(classInfo.addBeans(), super.addBeans())
                    .flatMap(Collection::stream)
                    .toList();
        }

        @Override
        boolean addJaxRs() {
            return classInfo.addJaxRs() || super.addJaxRs();
        }

        @Override
        boolean disableDiscovery() {
            return classInfo.disableDiscovery() || super.disableDiscovery();
        }

        @Override
        boolean requiresReset() {
            return classInfo.requiresReset()
                   || super.disableDiscovery()
                   || !super.addBeans().isEmpty()
                   || !super.addExtensions().isEmpty();
        }
    }
}
