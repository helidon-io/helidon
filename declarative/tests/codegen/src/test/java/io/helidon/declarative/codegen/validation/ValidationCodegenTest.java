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
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.validation.Validation;

import org.junit.jupiter.api.Test;

import static io.helidon.codegen.testing.CodegenMatchers.matches;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class ValidationCodegenTest {
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
    void testFieldValidation() throws IOException {
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
                        }
                        """)
                .build()
                .compile();

        assertThat(result.success(), is(true));
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat(Files.exists(validator), is(true));

        var content = Files.readString(validator, StandardCharsets.UTF_8);
        assertThat(content, matches("""
                                            //...
                                            class Foo__Validated implements TypeValidator<Foo> {
                                            //...
                                                        case "id" -> checkId(ctx, (Integer) value);
                                            //...
                                            }
                                            """));
        String diags = String.join("\n", result.diagnostics());
        assertThat(diags, not(containsString("warning:")));
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
    void testNonServiceInterfaceMethodConstraintRequiresValidated() {
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
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString(ValidationTypes.VALIDATION_VALIDATED.fqName()
                                                      + " annotation is required on non-service type"));
    }

    @Test
    void testPrivateFieldValidation() throws IOException {
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

        assertThat("Build should fail, as we do not support constraints on private fields", result.success(), is(false));
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validator.java should not be generated", Files.exists(validator), is(false));

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, containsString("error:"));
        assertThat(diagnostics, containsString("private Integer id"));
        assertThat(diagnostics, containsString("Only non-private fields and getter methods are supported"));
    }
}
