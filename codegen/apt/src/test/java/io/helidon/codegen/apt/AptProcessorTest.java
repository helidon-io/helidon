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

package io.helidon.codegen.apt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenScope;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.api.stability.ApiStabilityProcessor;
import io.helidon.codegen.spi.TypeMapper;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AptProcessorTest {
    private static final TypeName MAPPED = TypeName.create("com.example.Mapped");

    @Test
    void testApiStabilityOptionsDoNotFailInitialization() {
        var result = TestCompiler.builder()
                .addProcessor(new AptProcessor())
                .addProcessor(new ApiStabilityProcessor())
                .currentRelease()
                .addOption("-Xlint:none")
                .addOption("-Ahelidon.api.incubating=ignore")
                .addSource("MyClass.java", """
                        package com.example;

                        class MyClass {
                        }
                        """)
                .build()
                .compile();

        assertTrue(result.success(), "Compilation should succeed when the API stability option is configured");
        assertFalse(result.diagnostics()
                            .stream()
                            .anyMatch(it -> it.contains("Unrecognized/unsupported Helidon option configured")),
                    "APT processor should ignore API stability options handled by a different Helidon processor");
    }

    @Test
    void testRecordComponentUsesDeclaredType() {
        AtomicReference<TypedElementInfo> component = new AtomicReference<>();

        var result = TestCompiler.builder()
                .addProcessor(new AbstractProcessor() {
                    private AptContext ctx;

                    @Override
                    public Set<String> getSupportedAnnotationTypes() {
                        return Set.of("*");
                    }

                    @Override
                    public SourceVersion getSupportedSourceVersion() {
                        return SourceVersion.latestSupported();
                    }

                    @Override
                    public synchronized void init(ProcessingEnvironment processingEnv) {
                        super.init(processingEnv);
                        this.ctx = AptContext.create(processingEnv, Set.of());
                    }

                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        if (roundEnv.processingOver()) {
                            return false;
                        }

                        TypeElement type = processingEnv.getElementUtils().getTypeElement("com.example.ExampleRecord");
                        component.set(AptTypeInfoFactory.create(ctx, type, ElementInfoPredicates.ALL_PREDICATE)
                                              .orElseThrow()
                                              .elementInfo()
                                              .stream()
                                              .filter(it -> it.kind() == ElementKind.RECORD_COMPONENT)
                                              .filter(it -> "value".equals(it.elementName()))
                                              .findFirst()
                                              .orElseThrow());
                        return false;
                    }
                })
                .currentRelease()
                .addSource("com/example/ExampleRecord.java", """
                        package com.example;

                        record ExampleRecord(String value) {
                        }
                        """)
                .build()
                .compile();

        assertTrue(result.success(), "Compilation should succeed for a record source");
        assertNotNull(component.get(), "Record component metadata should be available");
        assertThat(component.get().typeName(), is(TypeName.create(String.class)));
    }

    @Test
    void testTypeElementCreationAppliesTypeMappers() {
        AtomicReference<TypeInfo> typeInfo = new AtomicReference<>();

        var result = TestCompiler.builder()
                .addProcessor(new AbstractProcessor() {
                    private AptContext ctx;

                    @Override
                    public Set<String> getSupportedAnnotationTypes() {
                        return Set.of("*");
                    }

                    @Override
                    public SourceVersion getSupportedSourceVersion() {
                        return SourceVersion.latestSupported();
                    }

                    @Override
                    public synchronized void init(ProcessingEnvironment processingEnv) {
                        super.init(processingEnv);
                        CodegenOptions options = AptOptions.create(processingEnv);
                        this.ctx = new AptContextImpl(processingEnv,
                                                      options,
                                                      Set.of(),
                                                      new AptFiler(processingEnv, options),
                                                      new AptLogger(processingEnv, options),
                                                      CodegenScope.PRODUCTION,
                                                      null) {
                            @Override
                            public List<TypeMapper> typeMappers() {
                                return List.of(new TypeMapper() {
                                    @Override
                                    public boolean supportsType(TypeInfo type) {
                                        return type.hasAnnotation(TypeName.create(Deprecated.class));
                                    }

                                    @Override
                                    public Optional<TypeInfo> map(CodegenContext ctx, TypeInfo type) {
                                        return Optional.of(TypeInfo.builder(type)
                                                                   .addAnnotation(Annotation.create(MAPPED))
                                                                   .build());
                                    }
                                });
                            }
                        };
                    }

                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        if (roundEnv.processingOver()) {
                            return false;
                        }

                        TypeElement type = processingEnv.getElementUtils().getTypeElement("com.example.ExampleType");
                        typeInfo.set(AptTypeInfoFactory.create(ctx, type, ElementInfoPredicates.ALL_PREDICATE)
                                             .orElseThrow());
                        return false;
                    }
                })
                .currentRelease()
                .addSource("com/example/ExampleType.java", """
                        package com.example;

                        @Deprecated
                        class ExampleType {
                        }
                        """)
                .build()
                .compile();

        assertTrue(result.success(), "Compilation should succeed for a source type");
        assertNotNull(typeInfo.get(), "Type metadata should be available");
        assertTrue(typeInfo.get().hasAnnotation(MAPPED), "Source types should be passed through type mappers");
    }

    @Test
    void testAptProcessorDiscoversRecordTypesFromRecordComponentAnnotations() {
        TypeName mappedType = TypeName.create("com.example.RecordMappedExample");
        TestRecordTypeMapperProvider.reset();

        var result = TestCompiler.builder()
                .addProcessor(new AptProcessor())
                .currentRelease()
                .addSource("com/example/RecordMapped.java", """
                        package com.example;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Target;

                        @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
                        @interface RecordMapped {
                        }
                        """)
                .addSource("com/example/RecordMappedExample.java", """
                        package com.example;

                        record RecordMappedExample(String key, @RecordMapped String value) {
                        }
                        """)
                .build()
                .compile();

        assertTrue(result.success(), "Compilation should succeed for a record source");
        assertTrue(TestRecordTypeMapperProvider.sawType(mappedType),
                   "AptProcessor should discover records referenced only through record-component annotations");
        assertTrue(TestRecordTypeMapperProvider.mappedType(mappedType),
                   "Discovered record types should be passed through type mappers");
    }
}
