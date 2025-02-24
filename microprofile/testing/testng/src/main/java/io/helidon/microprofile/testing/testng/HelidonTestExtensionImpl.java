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
import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.microprofile.testing.HelidonTestExtension;
import io.helidon.microprofile.testing.HelidonTestInfo;
import io.helidon.microprofile.testing.HelidonTestScope;

import static io.helidon.microprofile.testing.Proxies.mirror;

/**
 * An implementation of {@link io.helidon.microprofile.testing.HelidonTestExtension} that supports the deprecated annotations.
 */
@SuppressWarnings("deprecation")
final class HelidonTestExtensionImpl extends HelidonTestExtension {

    private static final Set<Class<? extends Annotation>> TYPE_ANNOTATION_TYPES = Set.of(
            AddConfig.class,
            AddConfigs.class,
            AddConfigBlock.class,
            Configuration.class);

    private static final Set<Class<? extends Annotation>> METHOD_ANNOTATION_TYPES = Set.of(
            AddConfig.class,
            AddConfigs.class,
            AddConfigBlock.class,
            Configuration.class);

    private final Set<Class<? extends Annotation>> typeAnnotationTypes;
    private final Set<Class<? extends Annotation>> methodAnnotationTypes;

    HelidonTestExtensionImpl(HelidonTestInfo<?> testInfo, HelidonTestScope testScope) {
        super(testInfo, testScope);
        this.typeAnnotationTypes = concat(super.typeAnnotationTypes(), TYPE_ANNOTATION_TYPES);
        this.methodAnnotationTypes = concat(super.methodAnnotationTypes(), METHOD_ANNOTATION_TYPES);
    }

    @Override
    protected Set<Class<? extends Annotation>> typeAnnotationTypes() {
        return typeAnnotationTypes;
    }

    @Override
    protected Set<Class<? extends Annotation>> methodAnnotationTypes() {
        return methodAnnotationTypes;
    }

    @Override
    protected void processTypeAnnotation(Annotation annotation) {
        switch (annotation) {
            case Configuration e -> processConfiguration(e);
            case AddConfig e -> processAddConfig(e);
            case AddConfigs e -> processAddConfig(e.value());
            case AddConfigBlock e -> processAddConfigBlock(e);
            default -> super.processTypeAnnotation(annotation);
        }
    }

    @Override
    protected void processTestMethodAnnotation(Annotation annotation, Method method) {
        switch (annotation) {
            case Configuration e -> processConfiguration(e);
            case AddConfig e -> processAddConfig(e);
            case AddConfigs e -> processAddConfig(e.value());
            case AddConfigBlock e -> processAddConfigBlock(e);
            default -> super.processTestMethodAnnotation(annotation, method);
        }
    }

    private void processConfiguration(Configuration annotation) {
        processConfiguration(mirror(io.helidon.microprofile.testing.Configuration.class, annotation));
    }

    private void processAddConfigBlock(AddConfigBlock annotation) {
        processAddConfigBlock(mirror(io.helidon.microprofile.testing.AddConfigBlock.class, annotation));
    }

    private void processAddConfig(AddConfig... annotations) {
        for (AddConfig annotation : annotations) {
            processAddConfig(mirror(io.helidon.microprofile.testing.AddConfig.class, annotation));
        }
    }

    private static Set<Class<? extends Annotation>> concat(Set<Class<? extends Annotation>> set1,
                                                           Set<Class<? extends Annotation>> set2) {

        return Stream.concat(set1.stream(), set2.stream()).collect(Collectors.toSet());
    }
}
