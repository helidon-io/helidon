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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import io.helidon.common.LazyValue;
import io.helidon.microprofile.testing.ReflectionHelper.Annotated;

import static io.helidon.common.testing.virtualthreads.PinningRecorder.DEFAULT_THRESHOLD;
import static io.helidon.microprofile.testing.ReflectionHelper.annotated;
import static io.helidon.microprofile.testing.ReflectionHelper.filterAnnotations;

/**
 * Base implementation.
 *
 * @param <T> annotated element type
 */
public abstract class HelidonTestDescriptorBase<T extends AnnotatedElement> implements HelidonTestDescriptor<T> {

    private final T element;
    private final List<Annotated<?>> annotated;
    private final LazyValue<Boolean> resetPerTest = LazyValue.create(this::lookupResetPerTest);
    private final LazyValue<Boolean> pinningDetection = LazyValue.create(this::lookupPinningDetection);
    private final LazyValue<Long> pinningThreshold = LazyValue.create(this::lookupPinningThreshold);
    private final LazyValue<List<AddExtension>> addExtensions = LazyValue.create(this::lookupAddExtensions);
    private final LazyValue<List<AddBean>> addBeans = LazyValue.create(this::lookupAddBeans);
    private final LazyValue<Boolean> addJaxRs = LazyValue.create(this::lookupAddJaxRs);
    private final LazyValue<Boolean> disableDiscovery = LazyValue.create(this::lookupDisableDiscovery);
    private final LazyValue<List<AddConfig>> addConfigs = LazyValue.create(this::lookupAddConfigs);
    private final LazyValue<List<AddConfigBlock>> addConfigBlocks = LazyValue.create(this::lookupAddConfigBlocks);
    private final LazyValue<Optional<Configuration>> configuration = LazyValue.create(this::lookupConfiguration);

    /**
     * Create a new instance.
     *
     * @param element element
     */
    protected HelidonTestDescriptorBase(T element) {
        this.element = element;
        this.annotated = switch (element) {
            case Class<?> c -> annotated(c);
            case Method m -> annotated(m);
            default -> throw new IllegalArgumentException("Unsupported element: " + element);
        };
    }

    @Override
    public T element() {
        return element;
    }

    @Override
    public boolean resetPerTest() {
        return resetPerTest.get();
    }

    @Override
    public boolean pinningDetection() {
        return pinningDetection.get();
    }

    @Override
    public long pinningThreshold() {
        return pinningThreshold.get();
    }

    @Override
    public List<AddExtension> addExtensions() {
        return addExtensions.get();
    }

    @Override
    public List<AddBean> addBeans() {
        return addBeans.get();
    }

    @Override
    public boolean addJaxRs() {
        return addJaxRs.get();
    }

    @Override
    public boolean disableDiscovery() {
        return disableDiscovery.get();
    }

    @Override
    public Optional<Configuration> configuration() {
        return configuration.get();
    }

    @Override
    public List<AddConfig> addConfigs() {
        return addConfigs.get();
    }

    @Override
    public List<AddConfigBlock> addConfigBlocks() {
        return addConfigBlocks.get();
    }

    /**
     * Lookup the value of {@code @HelidonTest(resetPerTest = true)}.
     *
     * @return {@code resetPerTest} value
     */
    protected boolean lookupResetPerTest() {
        return false;
    }

    /**
     * Lookup the value of {@code @HelidonTest(pinningDetection = true)}.
     *
     * @return {@code pinningDetection} value
     */
    protected boolean lookupPinningDetection() {
        return false;
    }

    /**
     * Lookup the value of {@code @HelidonTest(pinningThreshold = 50)}.
     *
     * @return {@code pinningThreshold} value
     */
    protected long lookupPinningThreshold() {
        return DEFAULT_THRESHOLD;
    }

    /**
     * Lookup the {@link AddExtension} annotations.
     *
     * @return annotations
     */
    protected List<AddExtension> lookupAddExtensions() {
        return annotations(AddExtension.class, AddExtensions.class, AddExtensions::value)
                .toList();
    }

    /**
     * Lookup the {@link AddBean} annotations.
     *
     * @return annotations
     */
    protected List<AddBean> lookupAddBeans() {
        return annotations(AddBean.class, AddBeans.class, AddBeans::value)
                .toList();
    }

    /**
     * Lookup the {@link AddJaxRs} annotation.
     *
     * @return annotation
     */
    protected boolean lookupAddJaxRs() {
        return annotations(AddJaxRs.class)
                .findFirst()
                .isPresent();
    }

    /**
     * Lookup the {@link DisableDiscovery} annotation.
     *
     * @return annotation
     */
    protected boolean lookupDisableDiscovery() {
        return annotations(DisableDiscovery.class)
                .findFirst()
                .map(DisableDiscovery::value)
                .orElse(false);
    }

    /**
     * Lookup the {@link Configuration} annotation.
     *
     * @return annotation
     */
    protected Optional<Configuration> lookupConfiguration() {
        return annotations(Configuration.class)
                .findFirst();
    }

    /**
     * Lookup the {@link AddConfig} annotations.
     *
     * @return annotations
     */
    protected List<AddConfig> lookupAddConfigs() {
        return annotations(AddConfig.class, AddConfigs.class, AddConfigs::value)
                .toList();
    }

    /**
     * Lookup the {@link AddConfigBlock} annotations.
     *
     * @return annotations
     */
    protected List<AddConfigBlock> lookupAddConfigBlocks() {
        return annotations(AddConfigBlock.class, AddConfigBlocks.class, AddConfigBlocks::value)
                .toList();
    }

    @Override
    public <A extends Annotation, C extends Annotation> Stream<A> annotations(Class<A> aType,
                                                                              Class<C> cType,
                                                                              Function<C, A[]> function) {
        return filterAnnotations(annotated, aType, cType, function);
    }

    @Override
    public <A extends Annotation> Stream<A> annotations(Class<A> aType) {
        return filterAnnotations(annotated, aType);
    }
}
