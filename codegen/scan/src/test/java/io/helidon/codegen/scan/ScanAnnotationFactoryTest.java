/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.codegen.scan;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenFiler;
import io.helidon.codegen.CodegenLogger;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenScope;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.Option;
import io.helidon.codegen.spi.AnnotationMapper;
import io.helidon.codegen.spi.ElementMapper;
import io.helidon.codegen.spi.TypeMapper;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ScanAnnotationFactoryTest {
    @Test
    void preservesClassfileValueProvenance() {
        try (ScanResult scan = new ClassGraph().enableAllInfo()
                .acceptPackages(ScanAnnotationFactoryTest.class.getPackageName()).scan()) {
            ScanContext context = new TestContext(scan);
            Annotation implicit = ScanAnnotationFactory.createAnnotation(context,
                    scan.getClassInfo(Implicit.class.getName()).getAnnotationInfo(Choice.class.getName()));
            Annotation explicit = ScanAnnotationFactory.createAnnotation(context,
                    scan.getClassInfo(Explicit.class.getName()).getAnnotationInfo(Choice.class.getName()));
            assertThat(implicit, is(explicit));
            assertThat(implicit.hashCode(), is(explicit.hashCode()));
            assertThat(implicit.toString(), is(explicit.toString()));
            for (String property : Set.of("mode", "count", "nested")) {
                assertThat(property, implicit.property(property).orElseThrow().explicit(), is(false));
                assertThat(property, explicit.property(property).orElseThrow().explicit(), is(true));
            }
            assertThat(implicit.annotationValue("nested").orElseThrow().property("value").orElseThrow().explicit(), is(false));
            assertThat(explicit.annotationValue("nested").orElseThrow().property("value").orElseThrow().explicit(), is(true));
        }
    }

    enum Mode { NONE }

    @Retention(RetentionPolicy.CLASS)
    @interface Choice {
        Mode mode() default Mode.NONE;
        int count() default 0;
        Nested nested() default @Nested;
    }

    @Retention(RetentionPolicy.CLASS)
    @interface Nested {
        String value() default "default";
    }

    @Choice
    static final class Implicit {
    }

    @Choice(mode = Mode.NONE, count = 0, nested = @Nested("default"))
    static final class Explicit {
    }

    private static final class TestContext implements ScanContext {
        private final ScanResult scan;

        private TestContext(ScanResult scan) {
            this.scan = scan;
        }

        @Override
        public ScanResult scanResult() {
            return scan;
        }

        @Override
        public Optional<ModuleInfo> module() {
            return Optional.empty();
        }

        @Override
        public CodegenFiler filer() {
            throw new UnsupportedOperationException("This operation is outside the annotation test.");
        }

        @Override
        public CodegenLogger logger() {
            throw new UnsupportedOperationException("This operation is outside the annotation test.");
        }

        @Override
        public CodegenScope scope() {
            throw new UnsupportedOperationException("This operation is outside the annotation test.");
        }

        @Override
        public CodegenOptions options() {
            throw new UnsupportedOperationException("This operation is outside the annotation test.");
        }

        @Override
        public Optional<TypeInfo> typeInfo(TypeName typeName) {
            return Optional.empty();
        }

        @Override
        public Optional<TypeInfo> typeInfo(TypeName typeName, Predicate<TypedElementInfo> elementPredicate) {
            return Optional.empty();
        }

        @Override
        public List<ElementMapper> elementMappers() {
            return List.of();
        }

        @Override
        public List<TypeMapper> typeMappers() {
            return List.of();
        }

        @Override
        public List<AnnotationMapper> annotationMappers() {
            return List.of();
        }

        @Override
        public Set<TypeName> mapperSupportedAnnotations() {
            return Set.of();
        }

        @Override
        public Set<String> mapperSupportedAnnotationPackages() {
            return Set.of();
        }

        @Override
        public Set<Option<?>> supportedOptions() {
            return Set.of();
        }

        @Override
        public String uniqueName(TypeInfo typeInfo, TypedElementInfo elementInfo) {
            throw new UnsupportedOperationException("This operation is outside the annotation test.");
        }
    }
}
