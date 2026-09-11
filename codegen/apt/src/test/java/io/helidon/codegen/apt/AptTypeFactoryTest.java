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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class AptTypeFactoryTest {
    @Test
    void testImportedGeneratedTypesInNestedSignatures() {
        AtomicReference<TypeInfo> firstRound = new AtomicReference<>();
        var result = TestCompiler.builder()
                .currentRelease()
                .addProcessor(new AbstractProcessor() {
                    @Override
                    public Set<String> getSupportedAnnotationTypes() {
                        return Set.of("*");
                    }

                    @Override
                    public SourceVersion getSupportedSourceVersion() {
                        return SourceVersion.latestSupported();
                    }

                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        if (roundEnv.processingOver() || firstRound.get() != null) {
                            return false;
                        }
                        AptContext ctx = AptContext.create(processingEnv, Set.of());
                        TypeElement source = processingEnv.getElementUtils().getTypeElement("com.acme.Caller");
                        firstRound.set(AptTypeInfoFactory.create(ctx, source, ElementInfoPredicates.ALL_PREDICATE)
                                               .orElseThrow());
                        try (var config = processingEnv.getFiler().createSourceFile("com.acme.spi.GeneratedConfig")
                                     .openWriter();
                                var container = processingEnv.getFiler().createSourceFile("com.acme.spi.Container")
                                        .openWriter()) {
                            config.write("package com.acme.spi; public class GeneratedConfig {}");
                            container.write("package com.acme.spi; "
                                                    + "public class Container { public interface Nested {} }");
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        return false;
                    }
                })
                .addSource("com/acme/Caller.java", """
                        package com.acme;

                        import java.util.List;
                        import java.util.Map;
                        import com.acme.spi.Container;
                        import com.acme.spi.GeneratedConfig;
                        import com.acme.spi.T;

                        interface Caller<T extends Comparable<T>> {
                            Map<String, List<? extends GeneratedConfig[]>> incoming();
                            List<? super GeneratedConfig> outgoing();
                            List<? extends T[]> genericArrays();
                            GeneratedConfig direct();
                            Container.Nested nested();
                            String resolved();
                            T typeVariable();
                        }
                        """)
                .addSource("com/acme/spi/T.java", """
                        package com.acme.spi;

                        public class T {
                        }
                        """)
                .build()
                .compile();

        assertThat(result.diagnostics().toString(), result.success(), is(true));
        TypeInfo observed = firstRound.get();
        assertThat(observed, notNullValue());
        TypedElementInfo incoming = method(observed, "incoming");
        assertThat(incoming.componentTypes(), is(incoming.typeName().typeArguments()));
        TypeName array = incoming.typeName().typeArguments().get(1).typeArguments().getFirst().upperBounds().getFirst();
        assertThat(array.packageName(), is("com.acme.spi"));
        assertThat(array.className(), is("GeneratedConfig"));
        assertThat(array.array(), is(true));
        assertThat(array.componentType().orElseThrow(), is(TypeName.create("com.acme.spi.GeneratedConfig")));
        TypeName lower = method(observed, "outgoing").typeName().typeArguments().getFirst().lowerBounds().getFirst();
        assertThat(lower, is(TypeName.create("com.acme.spi.GeneratedConfig")));
        assertThat(method(observed, "direct").typeName(), is(TypeName.create("com.acme.spi.GeneratedConfig")));
        assertThat(method(observed, "nested").typeName(), is(TypeName.create("com.acme.spi.Container.Nested")));
        assertThat(method(observed, "resolved").typeName(), is(TypeName.create(String.class)));
        TypeName typeVariable = method(observed, "typeVariable").typeName();
        assertThat(typeVariable.className(), is("T"));
        assertThat(typeVariable.packageName(), is(""));
        assertThat(typeVariable.generic(), is(true));
        TypeName genericArray = method(observed, "genericArrays").typeName()
                .typeArguments().getFirst().upperBounds().getFirst();
        assertThat(genericArray.packageName(), is(""));
        assertThat(genericArray.componentType().orElseThrow().generic(), is(true));
    }

    private static TypedElementInfo method(TypeInfo type, String name) {
        return type.elementInfo().stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(it -> it.elementName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
