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

package io.helidon.builder.test;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.builder.test.testsubjects.ProviderNoImpls;
import io.helidon.common.Api;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class GeneratedAnnotationTest {

    @Test
    void testGeneratedAnnotation() {
        Deprecated annotation = PrototypeAnnotated.class.getAnnotation(Deprecated.class);
        assertThat(annotation, notNullValue());
        assertThat(annotation.forRemoval(), is(true));
        assertThat(annotation.since(), is("4.4.0"));
    }

    @Test
    void testGeneratedOptionAnnotations() throws IOException, NoSuchMethodException {
        assertInternalAnnotation(OptionAnnotated.class, "value", 0);
        assertInternalAnnotation(OptionAnnotated.BuilderBase.class, "value", 0);
        assertInternalAnnotation(OptionAnnotated.BuilderBase.class, "value", 1);

        assertThat(OptionAnnotated.class.getMethod("value").getAnnotation(Deprecated.class), notNullValue());
        assertThat(OptionAnnotated.BuilderBase.class.getMethod("value").getAnnotation(Deprecated.class), notNullValue());
        assertThat(OptionAnnotated.BuilderBase.class.getMethod("value", String.class).getAnnotation(Deprecated.class),
                   notNullValue());
    }

    @Test
    void testGeneratedProviderDiscoveryApiAnnotation() throws IOException, NoSuchMethodException {
        assertInternalAnnotation(ProviderOptionAnnotated.BuilderBase.class, "servicesDiscoverServices", 0);
        assertInternalAnnotation(ProviderOptionAnnotated.BuilderBase.class, "servicesDiscoverServices", 1);

        assertThat(ProviderOptionAnnotated.BuilderBase.class.getMethod("servicesDiscoverServices")
                           .getAnnotation(Deprecated.class),
                   nullValue());
        assertThat(ProviderOptionAnnotated.BuilderBase.class.getMethod("servicesDiscoverServices", boolean.class)
                           .getAnnotation(Deprecated.class),
                   nullValue());
    }

    private static void assertInternalAnnotation(Class<?> type, String methodName, int parameterCount) throws IOException {
        String resourceName = "/" + type.getName().replace('.', '/') + ".class";
        byte[] classBytes;
        try (var inputStream = type.getResourceAsStream(resourceName)) {
            assertThat("Generated class resource is available", inputStream, notNullValue());
            classBytes = inputStream.readAllBytes();
        }

        MethodModel method = ClassFile.of()
                .parse(classBytes)
                .methods()
                .stream()
                .filter(it -> it.methodName().equalsString(methodName))
                .filter(it -> it.methodTypeSymbol().parameterCount() == parameterCount)
                .findFirst()
                .orElseThrow();

        var annotationTypes = method.findAttribute(Attributes.runtimeInvisibleAnnotations())
                .orElseThrow()
                .annotations()
                .stream()
                .map(it -> it.className().stringValue())
                .toList();
        assertThat(annotationTypes, hasItem(Api.Internal.class.descriptorString()));
    }

    @Prototype.Blueprint
    @Prototype.Annotated("java.lang.Deprecated(forRemoval = true, since = \"4.4.0\")")
    interface PrototypeAnnotatedBlueprint {
    }

    @Prototype.Blueprint
    interface OptionAnnotatedBlueprint {
        @Api.Internal
        @Prototype.Annotated("java.lang.Deprecated")
        String value();
    }

    @Prototype.Blueprint
    interface ProviderOptionAnnotatedBlueprint {
        @Api.Internal
        @Prototype.Annotated("java.lang.Deprecated")
        @Option.Provider(ProviderNoImpls.SomeService.class)
        List<ProviderNoImpls.SomeService> services();
    }

}
