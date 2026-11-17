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
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

class ScanTypeInfoFactoryTest {
    @Test
    void externalHierarchyRemainsAvailable() {
        try (ScanResult scan = scanner().acceptClasses(Leaf.class.getName()).scan()) {
            var context = new TestContext(scan);
            TypeInfo leaf = ScanTypeInfoFactory.create(context, scan.getClassInfo(Leaf.class.getName())).orElseThrow();
            TypeInfo parent = leaf.superTypeInfo().orElseThrow();

            assertThat(parent.typeName(), is(TypeName.create(Parent.class)));
            assertThat(parent.elementInfo().stream().map(TypedElementInfo::elementName).toList(), hasItem("parentField"));
            assertThat(parent.superTypeInfo(), is(Optional.empty()));
            assertThat(leaf.interfaceTypeInfo().stream().map(TypeInfo::typeName).toList(),
                       hasItem(TypeName.create(Contract.class)));
            TypeInfo contract = leaf.interfaceTypeInfo().stream()
                    .filter(it -> it.typeName().equals(TypeName.create(Contract.class)))
                    .findFirst()
                    .orElseThrow();
            assertThat(contract.elementInfo().stream().map(TypedElementInfo::elementName).toList(), hasItem("execute"));
        }
    }

    @Test
    void nonpublicMembersAndAnnotationDefaultsAreMapped() {
        try (ScanResult scan = scanner().acceptPackages(getClass().getPackageName()).scan()) {
            var context = new TestContext(scan);
            TypeInfo type = ScanTypeInfoFactory.create(context, scan.getClassInfo(AnnotatedType.class.getName()))
                    .orElseThrow();
            TypedElementInfo field = type.elementInfo().stream()
                    .filter(it -> it.elementName().equals("value"))
                    .findFirst()
                    .orElseThrow();
            assertThat(field.accessModifier(), is(AccessModifier.PRIVATE));
            Annotation annotation = field.annotations().stream()
                    .filter(it -> it.typeName().equals(TypeName.create(Metadata.class)))
                    .findFirst()
                    .orElseThrow();
            assertThat(annotation.stringValue().orElseThrow(), is("default-value"));
            assertThat(annotation.intValues("numbers").orElseThrow(), is(List.of(3, 5)));
            assertThat(annotation.typeValue("type").orElseThrow(), is(TypeName.create(String.class)));
            assertThat(annotation.enumValue("policy", RetentionPolicy.class).orElseThrow(), is(RetentionPolicy.CLASS));
            assertThat(annotation.metaAnnotations().stream().map(Annotation::typeName).toList(),
                       hasItem(TypeName.create(Marker.class)));

            TypedElementInfo nested = type.elementInfo().stream()
                    .filter(it -> it.typeName().equals(TypeName.create(AnnotatedType.Hidden.class)))
                    .findFirst()
                    .orElseThrow();
            assertThat(nested.accessModifier(), is(AccessModifier.PRIVATE));

            TypedElementInfo method = type.elementInfo().stream()
                    .filter(it -> it.elementName().equals("consume"))
                    .findFirst()
                    .orElseThrow();
            assertThat(method.accessModifier(), is(AccessModifier.PRIVATE));
            Annotation parameter = method.parameterArguments().getFirst().annotations().stream()
                    .filter(it -> it.typeName().equals(TypeName.create(Metadata.class)))
                    .findFirst()
                    .orElseThrow();
            assertThat(parameter.stringValue().orElseThrow(), is("parameter"));
        }
    }

    private static ClassGraph scanner() {
        return new ClassGraph()
                .enableClassLoaders(ScanTypeInfoFactoryTest.class.getClassLoader())
                .enableClassInfo()
                .enableFieldInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .enableExternalClasses()
                .ignoreClassVisibility()
                .ignoreFieldVisibility()
                .ignoreMethodVisibility();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Marker {
    }

    @Marker
    @Retention(RetentionPolicy.RUNTIME)
    @interface Metadata {
        String value() default "default-value";

        int[] numbers() default {3, 5};

        Class<?> type() default String.class;

        RetentionPolicy policy() default RetentionPolicy.CLASS;
    }

    interface Contract {
        void execute();
    }

    static class Parent implements Contract {
        private String parentField;

        @Override
        public void execute() {
        }
    }

    static class Leaf extends Parent {
    }

    static class AnnotatedType {
        @Metadata
        private String value;

        private void consume(@Metadata("parameter") String input) {
        }

        private static class Hidden {
        }
    }

    private static class TestContext implements ScanContext {
        private final ScanResult scan;

        TestContext(ScanResult scan) {
            this.scan = scan;
        }

        @Override
        public Optional<ModuleInfo> module() {
            return Optional.empty();
        }

        @Override
        public CodegenFiler filer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CodegenLogger logger() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CodegenScope scope() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CodegenOptions options() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<TypeInfo> typeInfo(TypeName typeName) {
            return ScanTypeInfoFactory.create(this, typeName);
        }

        @Override
        public Optional<TypeInfo> typeInfo(TypeName typeName, Predicate<TypedElementInfo> predicate) {
            return ScanTypeInfoFactory.create(this, typeName, predicate);
        }

        @Override
        public ScanResult scanResult() {
            return scan;
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
        public String uniqueName(TypeInfo type, TypedElementInfo element) {
            throw new UnsupportedOperationException();
        }
    }
}
