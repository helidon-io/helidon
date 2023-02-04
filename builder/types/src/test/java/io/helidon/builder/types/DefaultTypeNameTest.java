/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.types;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static io.helidon.builder.types.DefaultTypeName.create;
import static io.helidon.builder.types.DefaultTypeName.createFromTypeName;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DefaultTypeNameTest {

    @Test
    void testNameFromString() {
        // simple case
        TypeName name = createFromTypeName("a.b.c.ClassName");
        assertThat(name.packageName(), is("a.b.c"));
        assertThat(name.className(), is("ClassName"));

        // inner class
        name = createFromTypeName("a.b.c.ClassName.Builder");
        assertThat(name.packageName(), is("a.b.c"));
        assertThat(name.className(), is("ClassName.Builder"));

        // double nested
        name = createFromTypeName("a.b.c.ClassName.Builder.Type");
        assertThat(name.packageName(), is("a.b.c"));
        assertThat(name.className(), is("ClassName.Builder.Type"));

        // bad naming
        name = createFromTypeName("a.b.c.myClass");
        assertThat(name.packageName(), is("a.b.c"));
        assertThat(name.className(), is("myClass"));
    }

    @Test
    void primitiveTypes() {
        assertThat(create(boolean.class).toString(), is("boolean"));
        assertThat(create(byte.class).toString(), is("byte"));
        assertThat(create(short.class).toString(), is("short"));
        assertThat(create(int.class).toString(), is("int"));
        assertThat(create(long.class).toString(), is("long"));
        assertThat(create(char.class).toString(), is("char"));
        assertThat(create(float.class).toString(), is("float"));
        assertThat(create(double.class).toString(), is("double"));
        assertThat(create(void.class).toString(), is("void"));

        assertThat(create(boolean.class).packageName(), is("java.lang"));
        assertThat(create(byte.class).packageName(), is("java.lang"));
        assertThat(create(short.class).packageName(), is("java.lang"));
        assertThat(create(int.class).packageName(), is("java.lang"));
        assertThat(create(long.class).packageName(), is("java.lang"));
        assertThat(create(char.class).packageName(), is("java.lang"));
        assertThat(create(float.class).packageName(), is("java.lang"));
        assertThat(create(double.class).packageName(), is("java.lang"));
        assertThat(create(void.class).packageName(), is("java.lang"));

        assertThat(create(boolean.class).className(), is("boolean"));
        assertThat(create(byte.class).className(), is("byte"));
        assertThat(create(short.class).className(), is("short"));
        assertThat(create(int.class).className(), is("int"));
        assertThat(create(long.class).className(), is("long"));
        assertThat(create(char.class).className(), is("char"));
        assertThat(create(float.class).className(), is("float"));
        assertThat(create(double.class).className(), is("double"));
        assertThat(create(void.class).className(), is("void"));

        assertThat(create(boolean.class).primitive(), is(true));
        assertThat(create(byte.class).primitive(), is(true));
        assertThat(create(short.class).primitive(), is(true));
        assertThat(create(int.class).primitive(), is(true));
        assertThat(create(long.class).primitive(), is(true));
        assertThat(create(char.class).primitive(), is(true));
        assertThat(create(float.class).primitive(), is(true));
        assertThat(create(double.class).primitive(), is(true));
        assertThat(create(void.class).primitive(), is(true));

        assertThat(create(boolean.class).array(), is(false));
        assertThat(create(byte.class).array(), is(false));
        assertThat(create(short.class).array(), is(false));
        assertThat(create(int.class).array(), is(false));
        assertThat(create(long.class).array(), is(false));
        assertThat(create(char.class).array(), is(false));
        assertThat(create(float.class).array(), is(false));
        assertThat(create(double.class).array(), is(false));
        assertThat(create(void.class).array(), is(false));
    }

    @Test
    void primitiveArrayTypes() {
        assertThat(create(boolean[].class).toString(), is("boolean[]"));
        assertThat(create(byte[].class).toString(), is("byte[]"));
        assertThat(create(short[].class).toString(), is("short[]"));
        assertThat(create(int[].class).toString(), is("int[]"));
        assertThat(create(long[].class).toString(), is("long[]"));
        assertThat(create(char[].class).toString(), is("char[]"));
        assertThat(create(float[].class).toString(), is("float[]"));
        assertThat(create(double[].class).toString(), is("double[]"));

        assertThat(create(boolean[].class).className(), is("boolean"));
        assertThat(create(byte[].class).className(), is("byte"));
        assertThat(create(short[].class).className(), is("short"));
        assertThat(create(int[].class).className(), is("int"));
        assertThat(create(long[].class).className(), is("long"));
        assertThat(create(char[].class).className(), is("char"));
        assertThat(create(float[].class).className(), is("float"));
        assertThat(create(double[].class).className(), is("double"));

        assertThat(create(boolean[].class).primitive(), is(true));
        assertThat(create(byte[].class).primitive(), is(true));
        assertThat(create(short[].class).primitive(), is(true));
        assertThat(create(int[].class).primitive(), is(true));
        assertThat(create(long[].class).primitive(), is(true));
        assertThat(create(char[].class).primitive(), is(true));
        assertThat(create(float[].class).primitive(), is(true));
        assertThat(create(double[].class).primitive(), is(true));

        assertThat(create(boolean[].class).array(), is(true));
        assertThat(create(byte[].class).array(), is(true));
        assertThat(create(short[].class).array(), is(true));
        assertThat(create(int[].class).array(), is(true));
        assertThat(create(long[].class).array(), is(true));
        assertThat(create(char[].class).array(), is(true));
        assertThat(create(float[].class).array(), is(true));
        assertThat(create(double[].class).array(), is(true));
    }

    @Test
    void nonPrimitiveUsages() {
        assertThat(create(Boolean.class).toString(), is("java.lang.Boolean"));
        assertThat(create(Long.class).toString(), is("java.lang.Long"));
        assertThat(create(Object.class).toString(), is("java.lang.Object"));
        assertThat(create(Void.class).toString(), is("java.lang.Void"));
        assertThat(create(Method.class).toString(), is("java.lang.reflect.Method"));

        assertThat(create(Boolean[].class).toString(), is("java.lang.Boolean[]"));
        assertThat(create(Boolean[].class).name(), is("java.lang.Boolean"));
        assertThat(create(Boolean[].class).fqName(), is("java.lang.Boolean[]"));
        assertThat(create(Long[].class).toString(), is("java.lang.Long[]"));
        assertThat(create(Object[].class).toString(), is("java.lang.Object[]"));
        assertThat(create(Void[].class).toString(), is("java.lang.Void[]"));
        assertThat(create(Method[].class).toString(), is("java.lang.reflect.Method[]"));

        assertThat(create(Boolean[].class).packageName(), is("java.lang"));
        assertThat(create(Long[].class).packageName(), is("java.lang"));
        assertThat(create(Object[].class).packageName(), is("java.lang"));
        assertThat(create(Void[].class).packageName(), is("java.lang"));
        assertThat(create(Method[].class).packageName(), is("java.lang.reflect"));

        assertThat(create(Boolean[].class).className(), is("Boolean"));
        assertThat(create(Long[].class).className(), is("Long"));
        assertThat(create(Object[].class).className(), is("Object"));
        assertThat(create(Void[].class).className(), is("Void"));
        assertThat(create(Method[].class).className(), is("Method"));

        assertThat(create(Boolean[].class).primitive(), is(false));
        assertThat(create(Long[].class).primitive(), is(false));
        assertThat(create(Object[].class).primitive(), is(false));
        assertThat(create(Void[].class).primitive(), is(false));
        assertThat(create(Method[].class).primitive(), is(false));

        assertThat(create(Boolean[].class).array(), is(true));
        assertThat(create(Long[].class).array(), is(true));
        assertThat(create(Object[].class).array(), is(true));
        assertThat(create(Void[].class).array(), is(true));
        assertThat(create(Method[].class).array(), is(true));
    }

    @Test
    void typeArguments() {
        DefaultTypeName typeName = DefaultTypeName.create(List.class)
                .toBuilder()
                .typeArguments(Collections.singletonList(DefaultTypeName.create(String.class)))
                .build();
        assertThat(typeName.fqName(),
                   is("java.util.List<java.lang.String>"));
        assertThat(typeName.toString(),
                                 is("java.util.List<java.lang.String>"));
        assertThat(typeName.name(),
                                 is("java.util.List"));

        typeName = DefaultTypeName.createFromTypeName("? extends pkg.Something");
        assertThat(typeName.wildcard(), is(true));
        assertThat(typeName.fqName(),
                   is("? extends pkg.Something"));
        assertThat(typeName.toString(),
                                 is("? extends pkg.Something"));
        assertThat(typeName.name(),
                                 is("pkg.Something"));
        assertThat(typeName.packageName(),
                                 is("pkg"));
        assertThat(typeName.className(),
                                 is("Something"));

        typeName = DefaultTypeName.createFromTypeName("?");
        assertThat(typeName.wildcard(), is(true));
        assertThat(typeName.fqName(),
                   is("?"));
        assertThat(typeName.toString(),
                   is("?"));
        assertThat(typeName.name(),
                   is(Object.class.getName()));
        assertThat(typeName.packageName(),
                   is(Object.class.getPackageName()));
        assertThat(typeName.className(),
                   is(Object.class.getSimpleName()));

        typeName = DefaultTypeName.create(List.class)
                .toBuilder()
                .typeArguments(Collections.singletonList(DefaultTypeName.createFromTypeName("? extends pkg.Something")))
                .build();
        assertThat(typeName.fqName(),
                                 is("java.util.List<? extends pkg.Something>"));
        assertThat(typeName.toString(),
                                 is("java.util.List<? extends pkg.Something>"));
        assertThat(typeName.name(),
                                 is("java.util.List"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void declaredName() {
        List<Optional<char[]>> list = new ArrayList<>();
        List<Optional<char[]>>[] arrayOfLists = new List[] {};

        assertThat(DefaultTypeName.create(char.class).declaredName(), equalTo("char"));
        assertThat(DefaultTypeName.create(char[].class).declaredName(), equalTo("char[]"));
        assertThat(DefaultTypeName.create(list.getClass()).declaredName(), equalTo("java.util.ArrayList"));
        assertThat(DefaultTypeName.create(arrayOfLists.getClass()).declaredName(), equalTo("java.util.List[]"));
        assertThat(DefaultTypeName.create(List[].class).declaredName(), equalTo("java.util.List[]"));
    }

    @Test
    void genericDecl() {
        DefaultTypeName genericTypeName = DefaultTypeName.createFromGenericDeclaration("CB");
        assertThat(genericTypeName.name(), equalTo("CB"));
        assertThat(genericTypeName.fqName(), equalTo("CB"));
        assertThat(genericTypeName.toString(), equalTo("CB"));
        assertThat(genericTypeName.generic(), is(true));
        assertThat(genericTypeName.wildcard(), is(false));

        DefaultTypeName typeName = DefaultTypeName.builder()
                .packageName(Optional.class.getPackageName())
                .className(Optional.class.getSimpleName())
                .typeArguments(Collections.singletonList(genericTypeName))
                .build();
        assertThat(typeName.name(), equalTo("java.util.Optional"));
        assertThat(typeName.fqName(), equalTo("java.util.Optional<CB>"));
        assertThat(typeName.toString(), equalTo("java.util.Optional<CB>"));

        List<TypeName> typeArguments =
                List.of(DefaultTypeName.createFromGenericDeclaration("K"), genericTypeName);
        typeName = DefaultTypeName.builder()
                .packageName(Map.class.getPackageName())
                .className(Map.class.getSimpleName())
                .typeArguments(typeArguments)
                .build();
        assertThat(typeName.name(), equalTo("java.util.Map"));
        assertThat(typeName.fqName(), equalTo("java.util.Map<K, CB>"));
        assertThat(typeName.toString(), equalTo("java.util.Map<K, CB>"));

        // note: in the future was can always add getBoundedTypeName()
        genericTypeName = DefaultTypeName.createFromGenericDeclaration("CB extends MyClass");
        assertThat(genericTypeName.name(), equalTo("CB extends MyClass"));
        assertThat(genericTypeName.fqName(), equalTo("CB extends MyClass"));
        assertThat(genericTypeName.toString(), equalTo("CB extends MyClass"));
        assertThat(genericTypeName.generic(), is(true));
        assertThat(genericTypeName.wildcard(), is(false));
    }

    @Test
    void builderOfType() {
        TypeName primitiveTypeName = DefaultTypeName.builder().type(boolean[].class).build();
        assertThat(primitiveTypeName.name(), equalTo("boolean"));
        assertThat(primitiveTypeName.fqName(), equalTo("boolean[]"));
        assertThat(primitiveTypeName.declaredName(), equalTo("boolean[]"));
        assertThat(primitiveTypeName.generic(), is(false));
        assertThat(primitiveTypeName.array(), is(true));
        assertThat(primitiveTypeName.primitive(), is(true));
        assertThat(primitiveTypeName.packageName(), equalTo("java.lang"));
        assertThat(primitiveTypeName.className(), equalTo("boolean"));

        TypeName objTypeName = DefaultTypeName.builder().type(Boolean[].class).build();
        assertThat(primitiveTypeName.name(), equalTo("boolean"));
        assertThat(primitiveTypeName.fqName(), equalTo("boolean[]"));
        assertThat(primitiveTypeName.declaredName(), equalTo("boolean[]"));
        assertThat(objTypeName.generic(), is(false));
        assertThat(objTypeName.array(), is(true));
        assertThat(objTypeName.primitive(), is(false));
        assertThat(objTypeName.packageName(), equalTo("java.lang"));
        assertThat(objTypeName.className(), equalTo("Boolean"));
    }

    @Test
    void extendsTypeName() {
        TypeName extendsName = DefaultTypeName.createExtendsTypeName(create(Map.class));
        assertThat(extendsName.fqName(), equalTo("? extends java.util.Map"));
        assertThat(extendsName.declaredName(), equalTo("java.util.Map"));
        assertThat(extendsName.name(), equalTo("java.util.Map"));
    }

}
