/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.types;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.common.types.TypeName.builder;
import static io.helidon.common.types.TypeName.create;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.lessThan;

class TypeNameTest {
    @Test
    void testNested() {
        Class<?> type = TestType.class;

        TypeName name = create(type);
        assertThat(name.packageName(), is(type.getPackageName()));
        assertThat(name.className(), is(type.getSimpleName()));
        assertThat(name.name(), is(type.getTypeName()));

        type = TestType.NestedType.class;
        name = create(type);
        assertThat(name.packageName(), is(type.getPackageName()));
        assertThat(name.className(), is(type.getSimpleName()));
        assertThat(name.name(), is(type.getTypeName()));

        type = TestType.NestedType.DoubleNestedType.class;
        name = create(type);
        assertThat(name.packageName(), is(type.getPackageName()));
        assertThat(name.className(), is(type.getSimpleName()));
        assertThat(name.name(), is(type.getTypeName()));
        assertThat(name.classNameWithEnclosingNames(), is("TestType.NestedType.DoubleNestedType"));
    }

    @Test
    void testGenericInnerType() {
        String resolved =
                "io.helidon.service.registry.Service.ScopeHandler<io.helidon.examples.inject.CustomScopeExample.MyScope>";

        TypeName typeName = TypeName.builder()
                .packageName("io.helidon.service.registry")
                .className("ScopeHandler")
                .addEnclosingName("Service")
                .addTypeArgument(TypeName.create("io.helidon.examples.inject.CustomScopeExample.MyScope"))
                .build();

        assertThat(typeName.resolvedName(), is(resolved));
        assertThat(typeName.fqName(), is("io.helidon.service.registry.Service.ScopeHandler"));

        ResolvedType rt = ResolvedType.create(typeName);
        assertThat(rt.type().resolvedName(), is(resolved));
        assertThat(rt.type().fqName(), is("io.helidon.service.registry.Service.ScopeHandler"));
    }

    @Test
    void testNestedEquality() {
        TypeName first = create(TestType.NestedType.class);
        TypeName second = create(TestType2.NestedType.class);

        assertThat("Nested classes of different top level classes must not be equal", first, not(second));
    }

    @Test
    void testNameFromString() {
        // simple case
        TypeName name = create("a.b.c.ClassName");
        assertThat(name.packageName(), is("a.b.c"));
        assertThat(name.className(), is("ClassName"));

        // inner class
        name = create("a.b.c.ClassName.Builder");
        assertThat(name.packageName(), is("a.b.c"));
        assertThat(name.className(), is("Builder"));
        assertThat(name.resolvedName(), is("a.b.c.ClassName.Builder"));
        assertThat(name.enclosingNames(), contains("ClassName"));

        // double nested
        name = create("a.b.c.ClassName.Builder.Type");
        assertThat(name.packageName(), is("a.b.c"));
        assertThat(name.className(), is("Type"));
        assertThat(name.resolvedName(), is("a.b.c.ClassName.Builder.Type"));
        assertThat(name.enclosingNames(), contains("ClassName", "Builder"));

        // bad naming
        name = create("a.b.c.myClass");
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
        assertThat(create(Boolean[].class).resolvedName(), is("java.lang.Boolean[]"));
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
        TypeName typeName = TypeName.builder(TypeName.create(List.class))
                .typeArguments(Collections.singletonList(TypeName.create(String.class)))
                .build();
        assertThat(typeName.resolvedName(),
                   is("java.util.List<java.lang.String>"));
        assertThat(typeName.toString(),
                   equalTo(typeName.resolvedName()));
        assertThat(typeName.name(),
                   is("java.util.List"));

        typeName = TypeName.create("? extends pkg.Something");
        assertThat(typeName.wildcard(), is(true));
        assertThat(typeName.resolvedName(),
                   is("? extends pkg.Something"));
        assertThat(typeName.toString(),
                   is("? extends pkg.Something"));
        assertThat(typeName.name(),
                   is("pkg.Something"));
        assertThat(typeName.packageName(),
                   is("pkg"));
        assertThat(typeName.className(),
                   is("Something"));

        typeName = TypeName.create("?");
        assertThat(typeName.wildcard(), is(true));
        assertThat(typeName.resolvedName(),
                   is("?"));
        assertThat(typeName.toString(),
                   equalTo(typeName.resolvedName()));
        assertThat(typeName.name(),
                   is(Object.class.getName()));
        assertThat(typeName.packageName(),
                   is(Object.class.getPackageName()));
        assertThat(typeName.className(),
                   is(Object.class.getSimpleName()));

        typeName = TypeName.builder(TypeName.create(List.class))
                .typeArguments(Collections.singletonList(TypeName.create("? extends pkg.Something")))
                .build();
        assertThat(typeName.resolvedName(),
                   is("java.util.List<? extends pkg.Something>"));
        assertThat(typeName.toString(),
                   equalTo(typeName.resolvedName()));
        assertThat(typeName.name(),
                   is("java.util.List"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void declaredName() {
        List<Optional<char[]>> list = new ArrayList<>();
        List<Optional<char[]>>[] arrayOfLists = new List[] {};

        assertThat(TypeName.create(char.class).declaredName(), equalTo("char"));
        assertThat(TypeName.create(char[].class).declaredName(), equalTo("char[]"));
        assertThat(TypeName.create(list.getClass()).declaredName(), equalTo("java.util.ArrayList"));
        assertThat(TypeName.create(arrayOfLists.getClass()).declaredName(), equalTo("java.util.List[]"));
        assertThat(TypeName.create(List[].class).declaredName(), equalTo("java.util.List[]"));
    }

    @Test
    void genericDecl() {
        TypeName genericTypeName = TypeName.createFromGenericDeclaration("CB");
        assertThat(genericTypeName.name(), equalTo("CB"));
        assertThat(genericTypeName.resolvedName(), equalTo("CB"));
        assertThat(genericTypeName.toString(), equalTo("CB"));
        assertThat(genericTypeName.generic(), is(true));
        assertThat(genericTypeName.wildcard(), is(false));

        TypeName typeName = TypeName.builder()
                .packageName(Optional.class.getPackageName())
                .className(Optional.class.getSimpleName())
                .typeArguments(Collections.singletonList(genericTypeName))
                .build();
        assertThat(typeName.name(), equalTo("java.util.Optional"));
        assertThat(typeName.resolvedName(), equalTo("java.util.Optional<CB>"));
        assertThat(typeName.toString(), equalTo("java.util.Optional<CB>"));

        List<TypeName> typeArguments =
                List.of(TypeName.createFromGenericDeclaration("K"), genericTypeName);
        typeName = TypeName.builder()
                .packageName(Map.class.getPackageName())
                .className(Map.class.getSimpleName())
                .typeArguments(typeArguments)
                .build();
        assertThat(typeName.name(), equalTo("java.util.Map"));
        assertThat(typeName.resolvedName(), equalTo("java.util.Map<K, CB>"));
        assertThat(typeName.toString(), equalTo(typeName.resolvedName()));

        // note: in the future was can always add getBoundedTypeName()
        genericTypeName = TypeName.createFromGenericDeclaration("CB extends MyClass");
        assertThat(genericTypeName.name(), equalTo("CB extends MyClass"));
        assertThat(genericTypeName.resolvedName(), equalTo("CB extends MyClass"));
        assertThat(genericTypeName.toString(), equalTo(genericTypeName.resolvedName()));
        assertThat(genericTypeName.generic(), is(true));
        assertThat(genericTypeName.wildcard(), is(false));
    }

    @Test
    void genericTypeName() {
        TypeName genericTypeName = TypeName.createFromGenericDeclaration("CB");
        assertThat(genericTypeName.genericTypeName().name(), equalTo("CB"));
        assertThat(genericTypeName.genericTypeName().resolvedName(), equalTo("CB"));
        assertThat(genericTypeName.genericTypeName().declaredName(), equalTo("CB"));

        TypeName typeName = TypeName.builder()
                .packageName(Optional.class.getPackageName())
                .className(Optional.class.getSimpleName())
                .typeArguments(List.of(genericTypeName))
                .array(true)
                .build();
        assertThat(typeName.genericTypeName().name(), equalTo("java.util.Optional"));
        assertThat(typeName.genericTypeName().resolvedName(), equalTo("java.util.Optional"));
        assertThat(typeName.genericTypeName().declaredName(), equalTo("java.util.Optional"));
    }

    @Test
    void generics() {
        TypeName genericTypeName = TypeName.createFromGenericDeclaration("CB");
        assertThat(genericTypeName.generic(), is(true));
        assertThat(genericTypeName.wildcard(), is(false));

        genericTypeName = TypeName.createFromGenericDeclaration("?");
        assertThat(genericTypeName.generic(), is(true));
        assertThat(genericTypeName.wildcard(), is(true));

        TypeName typeName = TypeName.builder()
                .packageName(Optional.class.getPackageName())
                .className(Optional.class.getSimpleName())
                .typeArguments(List.of(genericTypeName))
                .array(true)
                .build();
        assertThat(typeName.generic(), is(false));
        assertThat(typeName.wildcard(), is(false));
    }

    @Test
    void builderOfType() {
        TypeName primitiveTypeName = TypeName.builder().type(boolean[].class).build();
        assertThat(primitiveTypeName.name(), equalTo("boolean"));
        assertThat(primitiveTypeName.resolvedName(), equalTo("boolean[]"));
        assertThat(primitiveTypeName.declaredName(), equalTo("boolean[]"));
        assertThat(primitiveTypeName.generic(), is(false));
        assertThat(primitiveTypeName.array(), is(true));
        assertThat(primitiveTypeName.primitive(), is(true));
        assertThat(primitiveTypeName.packageName(), equalTo("java.lang"));
        assertThat(primitiveTypeName.className(), equalTo("boolean"));

        TypeName objTypeName = TypeName.builder().type(Boolean[].class).build();
        assertThat(primitiveTypeName.name(), equalTo("boolean"));
        assertThat(primitiveTypeName.resolvedName(), equalTo("boolean[]"));
        assertThat(primitiveTypeName.declaredName(), equalTo("boolean[]"));
        assertThat(objTypeName.generic(), is(false));
        assertThat(objTypeName.array(), is(true));
        assertThat(objTypeName.primitive(), is(false));
        assertThat(objTypeName.packageName(), equalTo("java.lang"));
        assertThat(objTypeName.className(), equalTo("Boolean"));
    }

    @Test
    void extendsTypeName() {
        TypeName extendsName = TypeName.builder(create(Map.class)).wildcard(true).build();
        assertThat(extendsName.resolvedName(), equalTo("? extends java.util.Map"));
        assertThat(extendsName.declaredName(), equalTo("java.util.Map"));
        assertThat(extendsName.name(), equalTo("java.util.Map"));
    }

    @Test
    void testDefaultMethods() {
        TypeName typeName = TypeName.create(Optional.class);
        assertThat("isOptional() for: " + typeName.name(), typeName.isOptional(), is(true));
        assertThat("isList() for: " + typeName.name(), typeName.isList(), is(false));
        assertThat("isMap() for: " + typeName.name(), typeName.isMap(), is(false));
        assertThat("isSet() for: " + typeName.name(), typeName.isSet(), is(false));

        typeName = TypeName.create(Set.class);
        assertThat("isOptional() for: " + typeName.name(), typeName.isOptional(), is(false));
        assertThat("isList() for: " + typeName.name(), typeName.isList(), is(false));
        assertThat("isMap() for: " + typeName.name(), typeName.isMap(), is(false));
        assertThat("isSet() for: " + typeName.name(), typeName.isSet(), is(true));

        typeName = TypeName.create(List.class);
        assertThat("isOptional() for: " + typeName.name(), typeName.isOptional(), is(false));
        assertThat("isList() for: " + typeName.name(), typeName.isList(), is(true));
        assertThat("isMap() for: " + typeName.name(), typeName.isMap(), is(false));
        assertThat("isSet() for: " + typeName.name(), typeName.isSet(), is(false));

        typeName = TypeName.create(Map.class);
        assertThat("isOptional() for: " + typeName.name(), typeName.isOptional(), is(false));
        assertThat("isList() for: " + typeName.name(), typeName.isList(), is(false));
        assertThat("isMap() for: " + typeName.name(), typeName.isMap(), is(true));
        assertThat("isSet() for: " + typeName.name(), typeName.isSet(), is(false));

        typeName = TypeName.create(String.class);
        assertThat("isOptional() for: " + typeName.name(), typeName.isOptional(), is(false));
        assertThat("isList() for: " + typeName.name(), typeName.isList(), is(false));
        assertThat("isMap() for: " + typeName.name(), typeName.isMap(), is(false));
        assertThat("isSet() for: " + typeName.name(), typeName.isSet(), is(false));
    }

    @ParameterizedTest
    @MethodSource("equalsAndCompareSource")
    @SuppressWarnings("unchecked")
    void hashEqualsAndCompare(EqualsData data) {
        if (data.equal) {
            assertThat("equals", data.first, equalTo(data.second));
            assertThat("equals", data.second, equalTo(data.first));
            assertThat("has", data.first.hashCode(), is(data.second.hashCode()));
            if (data.canCompare) {
                assertThat("compare", data.first.compareTo(data.second), is(0));
                assertThat("compare", data.second.compareTo(data.first), is(0));
            }
        } else {
            assertThat("equals", data.first, not(equalTo(data.second)));
            assertThat("equals", data.second, not(equalTo(data.first)));
            assertThat("has", data.first.hashCode(), not(data.second.hashCode()));
            if (data.canCompare) {
                int compareOne = data.first.compareTo(data.second);
                int compareTwo = data.second.compareTo(data.first);
                assertThat("compare", compareOne, not(0));
                assertThat("compare", compareTwo, not(0));
                assertThat("compare", compareOne, not(compareTwo));
                // also make sure one is negative and one positive
                assertThat("compare has negative and positive", (compareOne * compareTwo), lessThan(0));
            }
        }
    }

    @Test
    void testGenericWildcards() {
        TypeName f = TypeName.builder(TypeName.create("java.lang.Object"))
                .wildcard(true)
                .build();
        TypeName s = TypeName.createFromGenericDeclaration("?");

        assertThat(f.wildcard(), is(true));
        assertThat(s.wildcard(), is(true));
    }

    @Test
    void testInnerClassNoPackageWildcard() {
        TypeName t = TypeName.create("ServiceRegistryConfig.BuilderBase<?,?>");

        assertThat(t.packageName(), is(""));
        assertThat(t.className(), is("BuilderBase"));
        assertThat(t.enclosingNames(), hasItems("ServiceRegistryConfig"));
        assertThat(t.typeArguments(), hasItems(TypeNames.WILDCARD, TypeNames.WILDCARD));
    }

    private static Stream<EqualsData> equalsAndCompareSource() {
        return Stream.of(
                new EqualsData(create(TypeNameTest.class), create(TypeNameTest.class), true),
                new EqualsData(create(TypeNameTest.class),
                               builder().type(TypeNameTest.class).array(true).build(),
                               false),
                new EqualsData(create(TypeNameTest.class),
                               builder().type(TypeNameTest.class).primitive(true).build(),
                               false),
                new EqualsData(create(TypeNameTest.class),
                               builder().type(TypeNameTest.class).primitive(true).array(true).build(),
                               false),
                new EqualsData(builder().type(TypeNameTest.class).array(true).build(),
                               builder().type(TypeNameTest.class).array(true).build(),
                               true),
                new EqualsData(builder().type(TypeNameTest.class).array(true).build(),
                               builder().type(TypeNameTest.class).array(true).primitive(true).build(),
                               false),
                new EqualsData(builder().type(TypeNameTest.class).primitive(true).build(),
                               builder().type(TypeNameTest.class).primitive(true).build(),
                               true),
                new EqualsData(builder().type(TypeNameTest.class).primitive(true).build(),
                               builder().type(TypeNameTest.class).array(true).primitive(true).build(),
                               false),
                new EqualsData(create(long.class),
                               builder().className("long").primitive(false).build(),
                               false),
                new EqualsData(create(TypeNameTest.class), "Some string", false, false)
        );
    }

    @SuppressWarnings("rawtypes")
    private final static class EqualsData {
        private final Comparable first;
        private final Comparable second;
        private final boolean equal;
        private final boolean canCompare;

        private EqualsData(Comparable<?> first, Comparable<?> second, boolean equal) {
            this(first, second, equal, true);
        }

        private EqualsData(Comparable<?> first, Comparable<?> second, boolean equal, boolean canCompare) {
            this.first = first;
            this.second = second;
            this.equal = equal;
            this.canCompare = canCompare;
        }

        @Override
        public String toString() {
            return first + ", " + second + ", equals: " + equal;
        }
    }
}
