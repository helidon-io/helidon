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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import io.helidon.codegen.Codegen;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.TypeHierarchyResolver;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertAll;

class AptTypeHierarchyResolverTest {
    private static final TypeName CONTRACT = TypeName.create("example.GeneratedContract");
    private static final TypeName BOX = TypeName.create("example.GeneratedBox");
    private static final TypeName CONTAINER = TypeName.create("example.Container");

    @Test
    void preservesGeneratedInterfaceMethodsAcrossCompilerRounds() {
        List<TypeInfo> contracts = new ArrayList<>();
        List<TypeInfo> classes = new ArrayList<>();
        List<List<String>> methodNames = new ArrayList<>();
        TypeName implementation = TypeName.create("example.GeneratedClass");
        var processor = new HierarchyProcessor((round, number) -> {
            TypeHierarchyResolver resolver = round.typeHierarchyResolver();
            if (number == 1) {
                round.addGeneratedType(CONTRACT,
                                       ClassModel.builder()
                                               .type(CONTRACT)
                                               .classType(ElementKind.INTERFACE)
                                               .addMethod(method -> method.name("implicit").returnType(TypeNames.STRING))
                                               .addMethod(method -> method.name("explicit")
                                                       .returnType(TypeNames.STRING)
                                                       .isAbstract(true))
                                               .addMethod(method -> method.name("fallback")
                                                       .returnType(TypeNames.STRING)
                                                       .isDefault(true)
                                                       .addContentLine("return \"default\";"))
                                               .addMethod(method -> method.name("factory")
                                                       .returnType(TypeNames.STRING)
                                                       .isStatic(true)
                                                       .addContentLine("return \"static\";")),
                                       CONTRACT);
                round.addGeneratedType(implementation,
                                       ClassModel.builder()
                                               .type(implementation)
                                               .addMethod(method -> method.name("concrete")
                                                       .returnType(TypeNames.STRING)
                                                       .addContentLine("return \"class\";")),
                                       CONTRACT);
            }
            assertThat(round.typeHierarchyResolver(), sameInstance(resolver));
            TypeInfo contract = round.typeInfo(CONTRACT).orElseThrow();
            contracts.add(contract);
            classes.add(round.typeInfo(implementation).orElseThrow());
            methodNames.add(resolver.effectiveInterfaceMethods(contract).stream()
                                    .map(method -> method.method().elementName())
                                    .toList());
        });

        var result = TestCompiler.builder()
                .addProcessor(processor)
                .currentRelease()
                .addSource("example/Trigger.java", "package example; class Trigger {}")
                .build()
                .compile();

        assertThat(result.diagnostics().toString(), result.success(), is(true));
        assertThat(processor.rounds, is(2));
        assertThat(processor.generatedElements, is(List.of(false, true)));
        for (int index = 0; index < contracts.size(); index++) {
            int round = index + 1;
            TypeInfo contract = contracts.get(index);
            TypeInfo generatedClass = classes.get(index);
            List<String> effectiveNames = methodNames.get(index);
            assertAll("processing round " + round,
                      () -> assertThat(element(contract, "implicit").elementModifiers().contains(Modifier.ABSTRACT), is(true)),
                      () -> assertThat(element(contract, "explicit").elementModifiers().contains(Modifier.ABSTRACT), is(true)),
                      () -> assertThat(element(contract, "fallback").elementModifiers().contains(Modifier.DEFAULT), is(true)),
                      () -> assertThat(element(contract, "fallback").elementModifiers().contains(Modifier.ABSTRACT), is(false)),
                      () -> assertThat(element(contract, "factory").elementModifiers().contains(Modifier.STATIC), is(true)),
                      () -> assertThat(element(contract, "factory").elementModifiers().contains(Modifier.ABSTRACT), is(false)),
                      () -> assertThat(element(generatedClass, "concrete").elementModifiers().contains(Modifier.ABSTRACT), is(false)),
                      () -> assertThat(effectiveNames, containsInAnyOrder("implicit", "explicit", "fallback")));
        }
    }

    @Test
    void preservesParameterizedGeneratedTypesAcrossCompilerRounds() {
        TypeName requested = TypeName.builder(BOX).addTypeArgument(TypeNames.STRING).build();
        TypeName simpleRequest = TypeName.builder(requested).packageName("").build();
        TypeName expectedSupertype = TypeName.builder(CONTAINER).addTypeArgument(TypeNames.STRING).build();
        List<TypeInfo> simpleTypes = new ArrayList<>();
        List<TypeInfo> qualifiedTypes = new ArrayList<>();
        List<TypeName> supertypes = new ArrayList<>();
        List<TypeName> memberTypes = new ArrayList<>();
        var processor = new HierarchyProcessor((round, number) -> {
            TypeHierarchyResolver resolver = round.typeHierarchyResolver();
            if (number == 1) {
                TypeArgument parameter = TypeArgument.builder().token("T").bound(CharSequence.class).build();
                TypeName parameterUse = TypeName.createFromGenericDeclaration("T");
                round.addGeneratedType(BOX,
                                       ClassModel.builder()
                                               .type(BOX)
                                               .addGenericArgument(parameter)
                                               .addInterface(TypeName.builder(CONTAINER).addTypeArgument(parameterUse).build())
                                               .addField(field -> field.name("value").type(parameterUse)),
                                       BOX);
            }
            TypeInfo qualified = round.typeInfo(requested).orElseThrow();
            // javac requires a qualified lookup once the generated declaration is available.
            TypeInfo simple = round.typeInfo(number == 1 ? simpleRequest : requested).orElseThrow();
            simpleTypes.add(simple);
            qualifiedTypes.add(qualified);
            TypeName useSite = number == 1 ? simple.typeName() : requested;
            supertypes.add(resolver.resolveSupertype(useSite, CONTAINER).orElseThrow());
            memberTypes.add(resolver.resolveMemberType(simple, useSite, element(simple, "value").typeName()));
        });

        var result = TestCompiler.builder()
                .addProcessor(processor)
                .currentRelease()
                .addSource("example/Container.java", "package example; interface Container<T> {}")
                .build()
                .compile();

        assertThat(result.diagnostics().toString(), result.success(), is(true));
        assertThat(processor.rounds, is(2));
        assertThat(processor.generatedElements, is(List.of(false, true)));
        for (int index = 0; index < simpleTypes.size(); index++) {
            TypeInfo simple = simpleTypes.get(index);
            TypeInfo qualified = qualifiedTypes.get(index);
            int round = index + 1;
            assertAll("processing round " + round,
                      () -> assertThat(simple.typeName().genericTypeName(), is(BOX)),
                      () -> assertThat(qualified.typeName().genericTypeName(), is(BOX)),
                      () -> assertThat(simple.rawType(), is(BOX)),
                      () -> assertThat(simple.declaredType().typeArguments().getFirst().upperBounds(),
                                       is(List.of(TypeName.create(CharSequence.class)))));
        }
        assertThat(simpleTypes.getFirst().typeName().typeArguments(), is(List.of(TypeNames.STRING)));
        assertThat(qualifiedTypes.getFirst().typeName().typeArguments(), is(List.of(TypeNames.STRING)));
        assertThat(supertypes.stream().map(TypeName::resolvedName).toList(),
                   is(List.of(expectedSupertype.resolvedName(), expectedSupertype.resolvedName())));
        assertThat(memberTypes, is(List.of(TypeNames.STRING, TypeNames.STRING)));
    }

    @Test
    void resolvesJavacInheritedGenericSignaturesAndCovariantOverrides() {
        var processor = new HierarchyProcessor((round, _) -> {
            TypeInfo root = round.typeInfo(TypeName.create("example.Root")).orElseThrow();
            List<TypeHierarchyResolver.ResolvedMethod> methods = round.typeHierarchyResolver().effectiveInterfaceMethods(root);
            assertThat(methods.stream().map(method -> method.method().elementName()).toList(),
                       containsInAnyOrder("value", "values", "accept"));
            TypedElementInfo value = method(methods, "value").method();
            TypedElementInfo values = method(methods, "values").method();
            TypedElementInfo accept = method(methods, "accept").method();
            assertThat(value.typeName(), is(TypeNames.STRING));
            assertThat(method(methods, "value").declarations().size(), is(1));
            assertThat(values.typeName().genericTypeName(), is(TypeName.create(List.class)));
            assertThat(values.typeName().typeArguments(), is(List.of(TypeNames.STRING)));
            assertThat(accept.parameterArguments().getFirst().typeName().vararg(), is(true));
            assertThat(accept.parameterArguments().getFirst().typeName().componentType().orElseThrow(), is(TypeNames.STRING));
            assertThat(round.typeHierarchyResolver().resolveSupertype(TypeName.create("example.Root"),
                                                                      TypeName.create("example.Parent"))
                               .orElseThrow().typeArguments(),
                       is(List.of(TypeNames.STRING)));
        });

        var result = TestCompiler.builder()
                .addProcessor(processor)
                .currentRelease()
                .addSource("example/Root.java", """
                        package example;
                        import java.util.List;
                        interface Parent<T> {
                            T value();
                            List<T> values();
                            void accept(T... values);
                        }
                        interface Other {
                            CharSequence value();
                        }
                        interface Root extends Parent<String>, Other {
                            @Override String value();
                        }
                        """)
                .build()
                .compile();

        assertThat(result.diagnostics().toString(), result.success(), is(true));
        assertThat(processor.rounds, is(1));
    }

    private static TypedElementInfo element(TypeInfo type, String name) {
        return type.elementInfo().stream().filter(element -> element.elementName().equals(name)).findFirst().orElseThrow();
    }

    private static TypeHierarchyResolver.ResolvedMethod method(List<TypeHierarchyResolver.ResolvedMethod> methods, String name) {
        return methods.stream().filter(method -> method.method().elementName().equals(name)).findFirst().orElseThrow();
    }

    private static final class HierarchyProcessor extends AbstractProcessor {
        private final BiConsumer<RoundContext, Integer> callback;
        private final List<Boolean> generatedElements = new ArrayList<>();
        private int rounds;

        private HierarchyProcessor(BiConsumer<RoundContext, Integer> callback) {
            this.callback = callback;
        }

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
            if (!roundEnv.processingOver()) {
                rounds++;
                generatedElements.add(processingEnv.getElementUtils().getTypeElement(CONTRACT.fqName()) != null
                                              || processingEnv.getElementUtils().getTypeElement(BOX.fqName()) != null);
                var context = new TestHierarchyExtensionProvider.Context(processingEnv, round -> callback.accept(round, rounds));
                Codegen.create(context, TypeName.create(HierarchyProcessor.class)).process(List.of(), List.of());
            }
            return false;
        }
    }
}
