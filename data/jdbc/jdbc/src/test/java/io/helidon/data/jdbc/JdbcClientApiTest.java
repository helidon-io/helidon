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
package io.helidon.data.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.constant.ClassDesc;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcClientApiTest {
    private static final ClassDesc API_PREVIEW = ClassDesc.of("io.helidon.common.Api$Preview");
    private static final ClassDesc API_INTERNAL = ClassDesc.of("io.helidon.common.Api$Internal");

    @Test
    void keepsThePublicRuntimeSurfaceSmall() {
        Set<String> nestedTypes = Arrays.stream(JdbcClient.class.getDeclaredClasses())
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());

        assertThat(nestedTypes, is(Set.of("Statement", "GeneratedKeys", "Rows", "RowMapper", "Row")));
        assertThat(publicMethodNames(JdbcClient.Statement.class),
                   is(Set.of("bind", "bindNull", "execute", "map", "generatedKeys")));
        assertThat(publicMethodNames(JdbcClient.GeneratedKeys.class), is(Set.of("addColumn", "map")));
        assertThat(publicMethodNames(JdbcClient.Rows.class), is(Set.of("one", "optional", "list")));
        assertThat(publicMethodNames(JdbcClient.Row.class), is(Set.of("required", "optional")));
        assertThat(publicMethodSignatures(JdbcClient.Statement.class),
                   is(Set.of("bind(int,Object):Statement",
                             "bindNull(int,JDBCType):Statement",
                             "execute():long",
                             "map(Class):Rows",
                             "map(RowMapper):Rows",
                             "generatedKeys():GeneratedKeys")));
        assertThat(publicMethodSignatures(JdbcClient.GeneratedKeys.class),
                   is(Set.of("addColumn(String):GeneratedKeys", "map(RowMapper):Rows")));
        assertThat(publicMethodSignatures(JdbcClient.Rows.class),
                   is(Set.of("one():Object", "optional():Optional", "list():List")));
        assertThat(publicMethodSignatures(JdbcClient.Row.class),
                   is(Set.of("required(int,Class):Object",
                             "required(String,Class):Object",
                             "optional(int,Class):Optional",
                             "optional(String,Class):Optional")));
        assertThat(Stream.concat(
                                   Arrays.stream(JdbcClient.class.getDeclaredMethods()),
                                   Arrays.stream(JdbcClient.class.getDeclaredClasses())
                                           .flatMap(type -> Arrays.stream(type.getDeclaredMethods())))
                           .noneMatch(Method::isVarArgs),
                   is(true));
    }

    @Test
    void exposesTheCompleteClientAsPreviewApi() throws IOException {
        ClassModel client = classModel(JdbcClient.class);

        assertThat(stabilityAnnotations(client), is(Set.of(API_PREVIEW)));
        assertThat(stabilityAnnotations(classModel(JdbcClient.RowMapper.class)), is(Set.of(API_PREVIEW)));
        assertThat(stabilityAnnotations(classModel(JdbcClient.Row.class)), is(Set.of(API_PREVIEW)));
        assertThat(stabilityAnnotations(classModel(JdbcClient.Statement.class)), is(Set.of(API_PREVIEW)));
        assertThat(stabilityAnnotations(classModel(JdbcClient.GeneratedKeys.class)), is(Set.of(API_PREVIEW)));
        assertThat(stabilityAnnotations(classModel(JdbcClient.Rows.class)), is(Set.of(API_PREVIEW)));
        assertThat(stabilityAnnotations(client.methods()
                                                .stream()
                                                .filter(method -> method.methodName().equalsString("create"))
                                                .findFirst()
                                                .orElseThrow()),
                   is(Set.of()));
    }

    /**
     * Collects the public method names declared directly by an API type.
     *
     * @param type API type
     * @return public method names
     */
    private static Set<String> publicMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());
    }

    /**
     * Collects stable erased signatures for public methods declared by an API
     * type.
     *
     * @param type API type
     * @return method signatures
     */
    private static Set<String> publicMethodSignatures(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName()
                        + Arrays.stream(method.getParameterTypes())
                                .map(Class::getSimpleName)
                                .collect(Collectors.joining(",", "(", ")"))
                        + ":" + method.getReturnType().getSimpleName())
                .collect(Collectors.toSet());
    }

    /**
     * Reads the class model for an API type.
     *
     * @param type API type
     * @return parsed class model
     * @throws IOException if the class cannot be read
     */
    private static ClassModel classModel(Class<?> type) throws IOException {
        String resourceName = type.getName().replace('.', '/') + ".class";
        try (InputStream input = Objects.requireNonNull(type.getClassLoader().getResourceAsStream(resourceName))) {
            return ClassFile.of().parse(input.readAllBytes());
        }
    }

    /**
     * Returns the API stability annotations declared on a class-file element.
     *
     * @param element class-file element
     * @return stability annotation types
     */
    private static Set<ClassDesc> stabilityAnnotations(AttributedElement element) {
        return element.findAttribute(Attributes.runtimeInvisibleAnnotations())
                .stream()
                .flatMap(attribute -> attribute.annotations().stream())
                .map(java.lang.classfile.Annotation::classSymbol)
                .filter(annotation -> API_PREVIEW.equals(annotation) || API_INTERNAL.equals(annotation))
                .collect(Collectors.toSet());
    }
}
