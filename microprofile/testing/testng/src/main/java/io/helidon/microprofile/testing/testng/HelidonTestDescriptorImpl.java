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
package io.helidon.microprofile.testing.testng;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import io.helidon.microprofile.testing.HelidonTestDescriptorBase;

import static io.helidon.microprofile.testing.Proxies.mirror;

/**
 * Base descriptor implementation that supports the deprecated annotations.
 */
@SuppressWarnings("deprecation")
class HelidonTestDescriptorImpl<T extends AnnotatedElement> extends HelidonTestDescriptorBase<T> {

    HelidonTestDescriptorImpl(T element) {
        super(element);
    }

    @Override
    protected List<io.helidon.microprofile.testing.AddBean> lookupAddBeans() {
        return lookup(io.helidon.microprofile.testing.AddBean.class, super.lookupAddBeans().stream(),
                AddBean.class, AddBeans.class, AddBeans::value).toList();
    }

    @Override
    protected List<io.helidon.microprofile.testing.AddConfig> lookupAddConfigs() {
        return lookup(io.helidon.microprofile.testing.AddConfig.class, super.lookupAddConfigs().stream(),
                AddConfig.class, AddConfigs.class, AddConfigs::value).toList();
    }

    @Override
    protected List<io.helidon.microprofile.testing.AddConfigBlock> lookupAddConfigBlocks() {
        return Stream.concat(super.lookupAddConfigBlocks().stream(), annotations(AddConfigBlock.class)
                        .map(a -> mirror(io.helidon.microprofile.testing.AddConfigBlock.class, a)))
                .toList();
    }

    @Override
    protected List<io.helidon.microprofile.testing.AddExtension> lookupAddExtensions() {
        return lookup(io.helidon.microprofile.testing.AddExtension.class, super.lookupAddExtensions().stream(),
                AddExtension.class, AddExtensions.class, AddExtensions::value).toList();
    }

    @Override
    protected Optional<io.helidon.microprofile.testing.Configuration> lookupConfiguration() {
        return super.lookupConfiguration().or(() -> annotations(Configuration.class)
                .map(a -> mirror(io.helidon.microprofile.testing.Configuration.class, a))
                .findFirst());
    }

    @Override
    protected boolean lookupAddJaxRs() {
        return super.lookupAddJaxRs() || annotations(AddJaxRs.class)
                .findFirst()
                .isPresent();
    }

    @Override
    protected boolean lookupDisableDiscovery() {
        return super.lookupDisableDiscovery() || annotations(DisableDiscovery.class)
                .findFirst()
                .map(DisableDiscovery::value)
                .orElse(false);
    }

    @Override
    protected boolean lookupResetPerTest() {
        return annotations(HelidonTest.class)
                .findFirst()
                .map(HelidonTest::resetPerTest)
                .orElse(false);
    }

    @Override
    protected boolean lookupPinningDetection() {
        return annotations(HelidonTest.class)
                .findFirst()
                .map(HelidonTest::pinningDetection)
                .orElse(false);
    }

    @Override
    public long pinningThreshold() {
        return annotations(HelidonTest.class)
                .findFirst()
                .map(HelidonTest::pinningThreshold)
                .orElse(20L);
    }

    private <R extends Annotation, A extends Annotation, C extends Annotation> Stream<R> lookup(Class<R> tType,
                                                                                                Stream<R> initial,
                                                                                                Class<A> aType,
                                                                                                Class<C> cType,
                                                                                                Function<C, A[]> function) {

        return Stream.concat(initial, annotations(aType, cType, function)
                .map(a -> mirror(tType, a)));
    }
}
