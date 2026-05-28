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

package io.helidon.declarative.codegen.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.GenericType;
import io.helidon.common.Generated;
import io.helidon.common.types.Annotation;
import io.helidon.service.codegen.ServiceCodegenTypes;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.validation.Validation;

import org.junit.jupiter.api.Test;

import static io.helidon.codegen.CodegenUtil.capitalize;
import static io.helidon.codegen.testing.CodegenMatchers.matches;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class ValidationCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Generated.class,
            Validation.Constraint.class,
            Annotation.class,
            GenericType.class,
            Lookup.class,
            Qualifier.class,
            Service.class,
            Prototype.class
    );

    @Test
    void testValidationProviderUsesServiceContractTrigger() {
        var provider = new ValidationExtensionProvider();

        assertThat(provider.supportsServiceContractAnnotations(), is(true));
        assertThat(provider.supportedAnnotations().contains(ValidationTypes.VALIDATION_VALIDATED), is(true));
        assertThat(provider.supportedAnnotations().contains(ValidationTypes.VALIDATION_VALID), is(true));
        assertThat(provider.supportedAnnotations().contains(ServiceCodegenTypes.SERVICE_ANNOTATION_PER_INSTANCE), is(false));
        assertThat(provider.supportedAnnotations().contains(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT), is(false));
        assertThat(provider.supportedMetaAnnotations().contains(ServiceCodegenTypes.SERVICE_ANNOTATION_SCOPE), is(false));
    }

    @Test
    void testSupportedClassElements() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public class Foo {
                            @Validation.Integer.Min(1)
                            Integer id;

                            @Validation.Integer.Min(1)
                            public Integer getAge() {
                                return 42;
                            }

                            @Validation.Integer.Min(1)
                            Integer size() {
                                return 1;
                            }
                        }
                        """)
                .build()
                .compile();

        assertGeneratedAndContainsCases(result, "Foo", "id", "age", "size");
    }

    @Test
    void testValidatedInterfaceWithOnlyParameterConstraints() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("DefaultApi.java", """
                        package com.example;

                        import java.util.Optional;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public interface DefaultApi {
                            String listStoreItems(@Validation.Integer.Min(1)
                                                  @Validation.Integer.Max(100) Optional<Integer> pageSize);
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(diagnostics, not(containsString("unreachable statement")));

        var validator = result.sourceOutput().resolve("com/example/DefaultApi__Validated.java");
        assertThat(Files.exists(validator), is(true));

        var content = Files.readString(validator, StandardCharsets.UTF_8);
        assertThat(content, matches("""
                                            //...
                                            class DefaultApi__Validated implements TypeValidator<DefaultApi> {
                                            //...
                                            }
                                            """));
    }

    @Test
    void testServiceContractConstraintGeneratesInterceptor() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("DefaultApi.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.validation.Validation;

                        @Service.Contract
                        public interface DefaultApi {
                            String validate(@Validation.String.NotBlank String name);
                        }
                        """)
                .addSource("DefaultApiImpl.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class DefaultApiImpl implements DefaultApi {
                            @Override
                            public String validate(String name) {
                                return name;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        var interceptor = result.sourceOutput().resolve("com/example/DefaultApiImpl__ValidationInterceptor_0.java");
        assertThat(Files.exists(interceptor), is(true));
        assertThat(Files.exists(result.sourceOutput().resolve("com/example/DefaultApiImpl__Validated.java")),
                   is(false));

        var content = Files.readString(interceptor, StandardCharsets.UTF_8);
        assertThat(content, containsString("\"com.example.DefaultApiImpl.validate(java.lang.String)\""));
        assertThat(content, containsString("validation__ctx.check("));
    }

    @Test
    void testDescribeServiceContractConstraintGeneratesInterceptor() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("DefaultApi.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.validation.Validation;

                        @Service.Contract
                        public interface DefaultApi {
                            String validate(@Validation.String.NotBlank String name);
                        }
                        """)
                .addSource("DescribedApi.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.Describe
                        class DescribedApi implements DefaultApi {
                            @Override
                            public String validate(String name) {
                                return name;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput()
                                        .resolve("com/example/DescribedApi__ValidationInterceptor_0.java")),
                   is(true));
    }

    @Test
    void testSupplierProvidedContractSameSignatureGeneratesInterceptor() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("ProvidedApi.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.validation.Validation;

                        @Service.Contract
                        public interface ProvidedApi {
                            @Validation.String.NotBlank
                            String get();
                        }
                        """)
                .addSource("ProvidedApiSupplier.java", """
                        package com.example;

                        import java.util.function.Supplier;

                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class ProvidedApiSupplier implements Supplier<ProvidedApi> {
                            @Override
                            public ProvidedApi get() {
                                return () -> "value";
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        var interceptor = result.sourceOutput().resolve("com/example/ProvidedApiSupplier__ValidationInterceptor_0.java");
        assertThat(Files.exists(interceptor), is(true));
        assertThat(Files.readString(interceptor, StandardCharsets.UTF_8),
                   containsString("\"com.example.ProvidedApi.get()\""));
    }

    @Test
    void testInjectionPointFactoryDelegateKeepsCustomizedFirst() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("IpProvidedApi.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.validation.Validation;

                        @Service.Contract
                        public interface IpProvidedApi {
                            @Validation.String.NotBlank
                            String value();
                        }
                        """)
                .addSource("IpProvider.java", """
                        package com.example;

                        import java.util.List;
                        import java.util.Optional;

                        import io.helidon.service.registry.Lookup;
                        import io.helidon.service.registry.Service;
                        import io.helidon.validation.Validation;

                        @Service.Singleton
                        class IpProvider implements Service.InjectionPointFactory<IpProvidedApi> {
                            @Override
                            public Optional<Service.QualifiedInstance<IpProvidedApi>> first(Lookup lookup) {
                                return Optional.of(Service.QualifiedInstance.create(() -> "first"));
                            }

                            @Override
                            @Validation.Collection.Size(min = 1)
                            public List<Service.QualifiedInstance<IpProvidedApi>> list(Lookup lookup) {
                                return List.of(Service.QualifiedInstance.create(() -> "list"));
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        var wrapper = result.sourceOutput().resolve("com/example/IpProvider__Interception_Wrapper.java");
        assertThat(Files.exists(wrapper), is(true));
        assertThat(Files.readString(wrapper, StandardCharsets.UTF_8),
                   containsString("return helidonInject__delegate.first(lookup);"));
    }

    @Test
    void testQualifiedFactoryDelegateKeepsCustomizedFirst() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("TestQualifier.java", """
                        package com.example;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;

                        import io.helidon.service.registry.Service;

                        @Service.Qualifier
                        @Retention(RetentionPolicy.CLASS)
                        @Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD})
                        public @interface TestQualifier {
                            String value() default "";
                        }
                        """)
                .addSource("QualifiedProvidedApi.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.validation.Validation;

                        @Service.Contract
                        public interface QualifiedProvidedApi {
                            @Validation.String.NotBlank
                            String value();
                        }
                        """)
                .addSource("QualifiedProvider.java", """
                        package com.example;

                        import java.util.List;
                        import java.util.Optional;

                        import io.helidon.common.GenericType;
                        import io.helidon.service.registry.Lookup;
                        import io.helidon.service.registry.Qualifier;
                        import io.helidon.service.registry.Service;
                        import io.helidon.validation.Validation;

                        @Service.Singleton
                        @TestQualifier("first")
                        class QualifiedProvider implements Service.QualifiedFactory<QualifiedProvidedApi, TestQualifier> {
                            @Override
                            public Optional<Service.QualifiedInstance<QualifiedProvidedApi>> first(
                                    Qualifier qualifier,
                                    Lookup lookup,
                                    GenericType<QualifiedProvidedApi> type) {
                                return Optional.of(Service.QualifiedInstance.create(() -> "first", qualifier));
                            }

                            @Override
                            @Validation.Collection.Size(min = 1)
                            public List<Service.QualifiedInstance<QualifiedProvidedApi>> list(
                                    Qualifier qualifier,
                                    Lookup lookup,
                                    GenericType<QualifiedProvidedApi> type) {
                                return List.of(Service.QualifiedInstance.create(() -> "list", qualifier));
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        var wrapper = result.sourceOutput().resolve("com/example/QualifiedProvider__Interception_Wrapper.java");
        assertThat(Files.exists(wrapper), is(true));
        assertThat(Files.readString(wrapper, StandardCharsets.UTF_8),
                   containsString("return helidonInject__delegate.first(qualifier, lookup, type);"));
    }

    @Test
    void testValidatedServiceContractDoesNotMarkImplementationAsValidated() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("DefaultApi.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.validation.Validation;

                        @Service.Contract
                        @Validation.Validated
                        public interface DefaultApi {
                            String validate(@Validation.String.NotBlank String name);
                        }
                        """)
                .addSource("DefaultApiImpl.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class DefaultApiImpl implements DefaultApi {
                            @Override
                            public String validate(String name) {
                                return name;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput()
                                        .resolve("com/example/DefaultApiImpl__ValidationInterceptor_0.java")),
                   is(true));
        assertThat(Files.exists(result.sourceOutput()
                                        .resolve("com/example/DefaultApiImpl__Validated.java")),
                   is(false));
    }

    @Test
    void testPlainInterfaceMethodConstraintDoesNotRequireValidated() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("DefaultApi.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        public interface DefaultApi {
                            String validate(@Validation.String.NotBlank String name);
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput()
                                        .resolve("com/example/DefaultApi__ValidationInterceptor_0.java")),
                   is(false));
        assertThat(Files.exists(result.sourceOutput()
                                        .resolve("com/example/DefaultApi__Validated.java")),
                   is(false));
    }

    @Test
    void testInterfaceStaticMethodParameterConstraintRequiresValidated() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("DefaultApi.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        public interface DefaultApi {
                            static String validate(@Validation.String.NotBlank String name) {
                                return name;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString(ValidationTypes.VALIDATION_VALIDATED.fqName()
                                                      + " annotation is required on non-service type"));
    }

    @Test
    void testInterfacePrivateMethodParameterConstraintRequiresValidated() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("DefaultApi.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        public interface DefaultApi {
                            private String validate(@Validation.String.NotBlank String name) {
                                return name;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString(ValidationTypes.VALIDATION_VALIDATED.fqName()
                                                      + " annotation is required on non-service type"));
    }

    @Test
    void testMetaAnnotatedContractConstraintTriggersServiceProcessing() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("CustomConstraint.java", """
                        package com.example;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Target;

                        import io.helidon.validation.Validation;

                        @Target(ElementType.PARAMETER)
                        @Validation.String.NotBlank
                        public @interface CustomConstraint {
                        }
                        """)
                .addSource("DefaultApi.java", """
                        package com.example;

                        public interface DefaultApi {
                            String validate(@CustomConstraint String name);
                        }
                        """)
                .addSource("DefaultApiImpl.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class DefaultApiImpl implements DefaultApi {
                            @Override
                            public String validate(String name) {
                                return name;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput()
                                        .resolve("com/example/DefaultApiImpl__ValidationInterceptor_0.java")),
                   is(true));
    }

    @Test
    void testNestedConstraintUsesAnnotatedTypeForValidatorProvider() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("DefaultApiImpl.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.service.registry.Service;
                        import io.helidon.validation.Validation;

                        @Service.Singleton
                        class DefaultApiImpl {
                            void validate(@Validation.Integer.Min(10) Long count,
                                          List<@Validation.Integer.Min(10) Integer> values) {
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        var interceptor = result.sourceOutput()
                .resolve("com/example/DefaultApiImpl__ValidationInterceptor_0.java");
        assertThat(Files.exists(interceptor), is(true));
        var content = Files.readString(interceptor, StandardCharsets.UTF_8);
        assertThat(content, containsString(".packageName(\"java.lang\")"));
        assertThat(content, containsString(".className(\"Long\")"));
        assertThat(content, containsString(".className(\"Integer\")"));
    }

    @Test
    void testVoidMethodReturnConstraintFailsClearly() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("DefaultApi.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.validation.Validation;

                        @Service.Singleton
                        class DefaultApi {
                            @Validation.NotNull
                            void reset() {
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("Validation annotations cannot constrain a void method return value."));
        assertThat(diagnostics, not(containsString("(void)")));
    }

    @Test
    void testLowerBoundValidTypeUseCompiles() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("ValidatedType.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        record ValidatedType(@Validation.String.NotBlank String name) {
                        }
                        """)
                .addSource("DefaultApiImpl.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.service.registry.Service;
                        import io.helidon.validation.Validation;

                        @Service.Singleton
                        class DefaultApiImpl {
                            void validate(List<? super @Validation.Valid ValidatedType> values) {
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        var interceptor = result.sourceOutput()
                .resolve("com/example/DefaultApiImpl__ValidationInterceptor_0.java");
        assertThat(Files.exists(interceptor), is(true));
        var content = Files.readString(interceptor, StandardCharsets.UTF_8);
        assertThat(content, containsString("instanceof ValidatedType validation__valid"));
        assertThat(content, containsString(".check(validation__ctx, validation__valid"));
        assertThat(content, not(containsString(".check(validation__ctx, validation__element")));
    }

    @Test
    void testSameSignatureInterfaceConstraintsAreMerged() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("LowApi.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        interface LowApi {
                            String validate(@Validation.Integer.Min(1) int count);
                        }
                        """)
                .addSource("HighApi.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        interface HighApi {
                            String validate(@Validation.Integer.Min(10) int count);
                        }
                        """)
                .addSource("DefaultApiImpl.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class DefaultApiImpl implements LowApi, HighApi {
                            @Override
                            public String validate(int count) {
                                return String.valueOf(count);
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        var interceptor = result.sourceOutput().resolve("com/example/DefaultApiImpl__ValidationInterceptor_0.java");
        assertThat(Files.exists(interceptor), is(true));

        var content = Files.readString(interceptor, StandardCharsets.UTF_8);
        assertThat(content.lines().filter(it -> it.contains("validation__ctx.check(")).count(), is(2L));
    }

    @Test
    void testSupportedRecordElements() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public record Foo(@Validation.Integer.Min(1) Integer id,
                                          @Validation.Integer.Min(1) Integer age) {
                        }
                        """)
                .build()
                .compile();

        assertGeneratedAndContainsCases(result, "Foo", "id", "age");
    }

    @Test
    void testSupportedInterfaceElements() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public interface Foo {
                            @Validation.Integer.Min(1)
                            Integer getId();

                            @Validation.Integer.Min(1)
                            Integer size();
                        }
                        """)
                .build()
                .compile();

        assertGeneratedAndContainsCases(result, "Foo", "id", "size");
    }

    @Test
    void testUnsupportedPrivateField() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public class Foo {
                            @Validation.Integer.Min(1)
                            private Integer id;
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Only non-private fields and getter methods are supported",
                               "private Integer id");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (private field)", Files.exists(validator), is(false));
    }

    @Test
    void testUnsupportedStaticField() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public class Foo {
                            @Validation.Integer.Min(1)
                            static Integer id;
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Only non-private fields and getter methods are supported",
                               "static Integer id");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (static field)", Files.exists(validator), is(false));
    }

    @Test
    void testUnsupportedPrivateMethod() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public class Foo {
                            @Validation.Valid
                            private Integer id() {
                                return 1;
                            }
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Only non-private fields and getter methods are supported",
                               "private Integer id()");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (private method)", Files.exists(validator), is(false));
    }

    @Test
    void testUnsupportedStaticMethod() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public class Foo {
                            @Validation.Valid
                            static Integer id() {
                                return 1;
                            }
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Only non-private fields and getter methods are supported",
                               "static Integer id()");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (static method)", Files.exists(validator), is(false));
    }

    @Test
    void testUnsupportedMethodWithArguments() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public class Foo {
                            @Validation.Valid
                            Integer id(Integer value) {
                                return value;
                            }
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Only non-private fields and getter methods are supported",
                               "Integer id(Integer value)");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (method with arguments)",
                   Files.exists(validator),
                   is(false));
    }

    @Test
    void testUnsupportedVoidMethod() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public class Foo {
                            @Validation.Valid
                            void ping() {
                            }
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Only non-private fields and getter methods are supported",
                               "void ping()");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (void method)", Files.exists(validator), is(false));
    }

    @Test
    void testUnsupportedConstructor() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public class Foo {
                            @Validation.Valid
                            Foo() {
                            }
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Only non-private fields and getter methods are supported",
                               "Foo()");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (constructor)", Files.exists(validator), is(false));
    }

    @Test
    void testInterfacePrivateAnnotatedMethodFailsCompilation() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public interface Foo {
                            @Validation.Valid
                            @Validation.Integer.Min(1)
                            private Integer id() {
                                return 1;
                            }
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Validation annotations on private interface methods are not supported",
                               "private Integer id()");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (interface private method)",
                   Files.exists(validator),
                   is(false));
    }

    @Test
    void testInterfacePrivateMethodWithParameterValidationFailsCompilation() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public interface Foo {
                            private Integer id(@Validation.Valid
                                               @Validation.Integer.Min(1) Integer value) {
                                return value;
                            }
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Validation annotations on private interface methods are not supported",
                               "private Integer id(");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (interface private method parameter)",
                   Files.exists(validator),
                   is(false));
    }

    @Test
    void testInterfaceStaticAnnotatedMethodIgnoredByTypeValidator() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public interface Foo {
                            @Validation.Integer.Min(1)
                            static Integer id() {
                                return 1;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Compilation diagnostics: " + diagnostics, result.success(), is(true));
        assertThat(diagnostics, not(containsString("unreachable statement")));

        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat(Files.exists(validator), is(true));
        var content = Files.readString(validator, StandardCharsets.UTF_8);
        assertThat(content, not(containsString("case \"id\" -> checkId(ctx, (Integer) value);")));
    }

    @Test
    void testInterfaceDefaultAnnotatedMethodSupported() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public interface Foo {
                            @Validation.Integer.Min(1)
                            default Integer id() {
                                return 1;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Compilation diagnostics: " + diagnostics, result.success(), is(true));
        assertThat(diagnostics, not(containsString("warning:")));

        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat(Files.exists(validator), is(true));
        var content = Files.readString(validator, StandardCharsets.UTF_8);
        assertThat(content, containsString("case \"id\" -> checkId(ctx, (Integer) value);"));
    }

    @Test
    void testInterfaceMethodParameterConstraintsSupported() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public interface Foo {
                            Integer id(@Validation.Integer.Min(1) Integer first,
                                       @Validation.Integer.Min(1) Integer second);
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Compilation diagnostics: " + diagnostics, result.success(), is(true));
        assertThat(diagnostics, not(containsString("unreachable statement")));

        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat(Files.exists(validator), is(true));
        var content = Files.readString(validator, StandardCharsets.UTF_8);
        assertThat(content, not(containsString("case \"id\" -> checkId(ctx, (Integer) value);")));
    }

    @Test
    void testInterfaceVoidMethodParameterConstraintsSupported() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public interface Foo {
                            void ping(@Validation.Integer.Min(1) Integer id);
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Compilation diagnostics: " + diagnostics, result.success(), is(true));
        assertThat(diagnostics, not(containsString("unreachable statement")));

        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat(Files.exists(validator), is(true));
        var content = Files.readString(validator, StandardCharsets.UTF_8);
        assertThat(content, not(containsString("case \"ping\" -> checkPing(ctx, (Integer) value);")));
    }

    @Test
    void testUnsupportedEnumType() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        enum Foo {
                            INSTANCE
                        }
                        """)
                .build()
                .compile();
        assertCompilationFails(result, "Only record, class, or interface are currently supported");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (enum)", Files.exists(validator), is(false));
    }

    @Test
    void testUnsupportedAnnotationType() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public @interface Foo {
                            @Validation.Integer.Min(1)
                            int value();
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result, "Only record, class, or interface are currently supported");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (annotation type)", Files.exists(validator), is(false));
    }

    private void assertGeneratedAndContainsCases(TestCompiler.Result result,
                                                 String typeName,
                                                 String... propertyNames) throws IOException {
        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Compilation diagnostics: " + diagnostics, result.success(), is(true));
        assertThat(diagnostics, not(containsString("warning:")));

        var validator = result.sourceOutput().resolve("com/example/" + typeName + "__Validated.java");
        assertThat(Files.exists(validator), is(true));

        var content = Files.readString(validator, StandardCharsets.UTF_8);
        for (String propertyName : propertyNames) {
            assertThat(content, containsString("case \"" + propertyName + "\" -> check"
                                                       + capitalize(propertyName) + "(ctx, (Integer) value);"));
        }
    }

    private void assertCompilationFails(TestCompiler.Result result, String... diagnosticParts) {
        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Build should fail", result.success(), is(false));
        assertThat(diagnostics, containsString("error:"));
        for (String diagnosticPart : diagnosticParts) {
            assertThat(diagnostics, containsString(diagnosticPart));
        }
    }

}
