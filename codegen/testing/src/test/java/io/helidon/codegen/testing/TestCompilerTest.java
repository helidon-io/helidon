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
package io.helidon.codegen.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeNames;

import org.junit.jupiter.api.Test;

import static io.helidon.codegen.testing.CodegenMatchers.matches;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * This test uses APT directly to demonstrate usage of {@link TestCompiler} and {@link CodegenMatchers}.
 * <p>
 * In practice, codegen extensions can be exercised by using {@code io.helidon.codegen.apt.AptProcessor}.
 */
class TestCompilerTest {

    @interface AcmeAnnotation {
    }

    @Test
    void testCodegen() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addProcessor(new ProcessorImpl() {
                    @Override
                    ClassModel handle(Element element) {
                        var annot = element.getAnnotation(AcmeAnnotation.class);
                        if (annot != null) {

                            // add a diagnostic
                            for (var e : element.getEnclosedElements()) {
                                var messager = processingEnv.getMessager();
                                messager.printWarning("Ugh-oh", e);
                            }

                            // generate a file
                            return ClassModel.builder()
                                    .packageName("com.acme")
                                    .name(element.getSimpleName() + "Hello")
                                    .addMethod(m -> m.name("noop"))
                                    .addMethod(m -> m
                                            .name("sayHello")
                                            .content("""
                                                return "Hello World!";
                                                """)
                                            .returnType(TypeNames.STRING))
                                    .build();
                        } else {
                            return null;
                        }
                    }
                })
                .addClasspath(AcmeAnnotation.class)
                .printDiagnostics(false)
                .addSource("AcmeObject.java", """
                        package io.helidon.codegen.testing;
                        
                        import io.helidon.codegen.testing.TestCompilerTest.AcmeAnnotation;
                        
                        @AcmeAnnotation
                        interface AcmeObject {
                            void makeStuff();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var actualFile = result.sourceOutput().resolve("com/acme/AcmeObjectHello.java");
        assertThat(Files.exists(actualFile), is(true));

        var actual = Files.readString(actualFile);
        assertThat(actual, matches("""
                package com.acme;
                //...
                public class AcmeObjectHello {
                //... skip the noop method
                    public String sayHello() {
                        return "Hello World!";
                    }
                //...
                }
                """));

        assertThat(result.diagnostics(), is(hasItems("""
                /AcmeObject.java:7: warning: Ugh-oh
                    void makeStuff();
                         ^""")));
    }

    /**
     * A dummy processor to exercise {@link TestCompiler} and {@link CodegenMatchers}.
     */
    private static abstract class ProcessorImpl extends AbstractProcessor {

        abstract ClassModel handle(Element element);

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            try {
                for (var elt : roundEnv.getRootElements()) {
                    var classModel = handle(elt);
                    if (classModel != null) {
                        var filer = processingEnv.getFiler();
                        var sourceFile = filer.createSourceFile(classModel.typeName().fqName(), elt);
                        try (Writer os = sourceFile.openWriter()) {
                            classModel.write(os, "    ");
                        }
                    }
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            return true;
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.RELEASE_21;
        }
    }
}
