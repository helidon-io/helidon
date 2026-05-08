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
package io.helidon.config.metadata.test.codegen;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.List;

import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Builder;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.model.CmModel;
import io.helidon.metadata.hson.Hson;

import org.hamcrest.Matchers;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@code TypeHandler}.
 */
class TypeHandlerTest {

    @Test
    void testImplicitTargetType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME listener configuration.
                         */
                        interface AcmeListenerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeListenerConfig> {
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeListenerConfig",
                                "description": "ACME listener configuration"
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testExternalBuilderTargetType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeListener.java", """
                        package com.acme.server;
                        
                        public interface AcmeListener {
                        }
                        """)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        interface AcmeConfig {
                            /**
                             * ACME configuration builder.
                             */
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, com.acme.server.AcmeListener> {
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig.Builder",
                                "description": "ACME configuration builder"
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testNormalType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME listener configuration.
                         */
                        @Configured
                        interface AcmeListenerConfig {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeListenerConfig",
                                "description": "ACME listener configuration"
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testIgnoreBuildMethod() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        interface AcmeListenerConfig {
                        
                            /**
                             * ACME listener configuration.
                             */
                            @Configured(ignoreBuildMethod = true)
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeListenerConfig> {
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeListenerConfig.Builder",
                                "description": "ACME listener configuration"
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testGenericTargetType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME listener configuration.
                         */
                        interface AcmeListenerConfig {
                        
                            @Configured
                            interface Builder<T extends Builder<T>> extends io.helidon.common.Builder<T, AcmeListenerConfig> {
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeListenerConfig",
                                "description": "ACME listener configuration"
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testParameterizedTargetType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * ACME listener configuration.
                         */
                        interface AcmeListenerConfig {
                        
                            @Configured
                            interface BuilderBase<B extends BuilderBase<B, T>, T extends AcmeListenerConfig>
                                extends io.helidon.common.Builder<B, T> {
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeListenerConfig",
                                "description": "ACME listener configuration"
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testUnresolvedParameterizedTargetType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * ACME builder base.
                         */
                        @Configured
                        interface BuilderBase<B extends BuilderBase<B, T>, T>
                            extends io.helidon.common.Builder<B, T> {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.BuilderBase",
                                "description": "ACME builder base"
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testImplicitTargetTypeOverride() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        interface AcmeListenerConfig {
                        }
                        """)
                .addSource("AcmeServerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * ACME server configuration.
                         */
                        public interface AcmeServerConfig extends AcmeListenerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeListenerConfig> {
                        
                                @Override
                                AcmeServerConfig build();
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeServerConfig",
                                "description": "ACME server configuration"
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testInheritance() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * ACME listener configuration.
                         */
                        interface AcmeListenerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeListenerConfig> {
                            }
                        }
                        """)
                .addSource("AcmeServerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * ACME server configuration.
                         */
                        public interface AcmeServerConfig extends AcmeListenerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeListenerConfig> {
                        
                                @Override
                                AcmeServerConfig build();
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeListenerConfig",
                                "description": "ACME listener configuration"
                            },
                            {
                                "type": "com.acme.AcmeServerConfig",
                                "description": "ACME server configuration"
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testOptions() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME listener configuration.
                         */
                        interface AcmeListenerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeListenerConfig> {
                                /**
                                 * Listen address.
                                 *
                                 * @param host host
                                 * @return this builder
                                 */
                                @ConfiguredOption(value = "0.0.0.0")
                                Builder host(String host);
                        
                                /**
                                 * Listen port.
                                 *
                                 * @param port port
                                 * @return this builder
                                 */
                                @ConfiguredOption(value = "0")
                                Builder port(int port);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeListenerConfig",
                                "description": "ACME listener configuration",
                                "options": [
                                    {
                                        "key": "host",
                                        "description": "Listen address",
                                        "defaultValue": "0.0.0.0"
                                    },
                                    {
                                        "key": "port",
                                        "type": "java.lang.Integer",
                                        "description": "Listen port",
                                        "defaultValue": "0"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testVarargOption() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME listener configuration.
                         */
                        interface AcmeListenerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeListenerConfig> {
                        
                                /**
                                 * Listen addresses.
                                 */
                                @ConfiguredOption
                                Builder host(String... hosts);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeListenerConfig",
                                "description": "ACME listener configuration",
                                "options": [
                                    {
                                        "key": "host",
                                        "description": "Listen addresses",
                                        "kind": "LIST"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeListenerConfig.java:18: warning: Invalid array type: java.lang.String...
                        Builder host(String... hosts);
                                               ^""")));
    }

    @Test
    void testUnconfiguredOption() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * ACME listener configuration.
                         */
                        interface AcmeListenerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeListenerConfig> {
                        
                                Builder host(String host);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeListenerConfig",
                                "description": "ACME listener configuration"
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testOverrides() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME listener configuration.
                         */
                        interface AcmeListenerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeListenerConfig> {
                                /**
                                 * Listen address.
                                 *
                                 * @param host host
                                 * @return this builder
                                 */
                                @ConfiguredOption(value = "0.0.0.0")
                                Builder host(String host);
                        
                                /**
                                 * Listen port.
                                 *
                                 * @param port port
                                 * @return this builder
                                 */
                                @ConfiguredOption(value = "0")
                                Builder port(int port);
                            }
                        }
                        """)
                .addSource("AcmeServerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME server configuration.
                         */
                        public interface AcmeServerConfig extends AcmeListenerConfig {
                        
                            @Configured
                            interface Builder extends AcmeListenerConfig.Builder {
                                /**
                                 * {@inheritDoc}
                                 */
                                @ConfiguredOption(value = "localhost")
                                @Override
                                Builder host(String host);
                        
                                @Override
                                @ConfiguredOption(value = "8080")
                                Builder port(int port);
                        
                                @Override
                                AcmeServerConfig build();
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeListenerConfig",
                                "description": "ACME listener configuration",
                                "options": [
                                    {
                                        "key": "host",
                                        "description": "Listen address",
                                        "defaultValue": "0.0.0.0"
                                    },
                                    {
                                        "key": "port",
                                        "type": "java.lang.Integer",
                                        "description": "Listen port",
                                        "defaultValue": "0"
                                    }
                                ]
                            },
                            {
                                "type": "com.acme.AcmeServerConfig",
                                "description": "ACME server configuration",
                                "inherits": [
                                    "com.acme.AcmeListenerConfig.Builder"
                                ],
                                "options": [
                                    {
                                        "key": "host",
                                        "description": "Listen address",
                                        "defaultValue": "localhost"
                                    },
                                    {
                                        "key": "port",
                                        "type": "java.lang.Integer",
                                        "description": "Listen port",
                                        "defaultValue": "8080"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testProvider() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("SecurityProvider.java", """
                        package com.acme;
                        
                        interface SecurityProvider {
                        }
                        """)
                .addSource("AcmeBasicAuthProvider.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * ACME basic auth security provider.
                         */
                        interface AcmeBasicAuthProvider extends SecurityProvider {
                        
                            @Configured(provides = SecurityProvider.class, prefix = "basic-auth")
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeBasicAuthProvider> {
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeBasicAuthProvider",
                                "description": "ACME basic auth security provider",
                                "prefix": "basic-auth",
                                "provides": [
                                    "com.acme.SecurityProvider"
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testStandalone() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeServerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * ACME server configuration.
                         */
                        interface AcmeServerConfig {
                        
                            @Configured(root = true, prefix = "server")
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeServerConfig> {
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeServerConfig",
                                "standalone": true,
                                "description": "ACME server configuration",
                                "prefix": "server"
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testModule() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addModulepath(List.of(Configured.class, Builder.class))
                .addProcessor(AptProcessor::new)
                .addSource("module-info.java", """
                        module com.acme {
                            exports com.acme;
                            requires io.helidon.common;
                            requires io.helidon.config.metadata;
                        }
                        """)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "com.acme",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration"
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testInheritsFromClasspath() throws IOException {
        var compiler = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .build();
        var result1 = TestCompiler.builder()
                .from(compiler)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * ACME listener configuration.
                         */
                        interface AcmeListenerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeListenerConfig> {
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result1.success(), is(true));

        var result2 = TestCompiler.builder()
                .from(compiler)
                .addClasspathEntry(result1.classOutput())
                .addProcessor(AptProcessor::new)
                .addSource("AcmeServerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * ACME server configuration.
                         */
                        interface AcmeServerConfig extends AcmeListenerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeServerConfig> {
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result2.success(), is(true));

        var schema = result2.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeServerConfig",
                                "description": "ACME server configuration"
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testMerge() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeServerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME server configuration.
                         */
                        interface AcmeServerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeServerConfig> {
                                @ConfiguredOption(mergeWithParent = true)
                                Builder host(AcmeListenerConfig listener);
                            }
                        }
                        """)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME listener config.
                         */
                        interface AcmeListenerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeListenerConfig> {
                                /**
                                 * Listen address.
                                 *
                                 * @param host host
                                 */
                                @ConfiguredOption(value = "0.0.0.0")
                                void host(String host);
                        
                                /**
                                 * Listen port.
                                 *
                                 * @param port port
                                 */
                                @ConfiguredOption(value = "0")
                                void port(int port);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeListenerConfig",
                                "description": "ACME listener config",
                                "options": [
                                    {
                                        "key": "host",
                                        "description": "Listen address",
                                        "defaultValue": "0.0.0.0"
                                    },
                                    {
                                        "key": "port",
                                        "type": "java.lang.Integer",
                                        "description": "Listen port",
                                        "defaultValue": "0"
                                    }
                                ]
                            },
                            {
                                "type": "com.acme.AcmeServerConfig",
                                "description": "ACME server configuration",
                                "options": [
                                    {
                                        "type": "com.acme.AcmeListenerConfig",
                                        "merge": true
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testMergeValueType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeServerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME server configuration.
                         */
                        interface AcmeServerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeServerConfig> {
                                /**
                                 * Listen port.
                                 * @param port port
                                 */
                                @ConfiguredOption(mergeWithParent = true)
                                void port(int port);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeServerConfig",
                                "description": "ACME server configuration",
                                "options": [
                                    {
                                        "key": "port",
                                        "type": "java.lang.Integer",
                                        "description": "Listen port"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeServerConfig.java:18: warning: Invalid merge option type: java.lang.Integer
                        void port(int port);
                                      ^""")));
    }

    @Test
    void testMergeFromClasspath() throws IOException {
        var compiler = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .build();
        var result1 = TestCompiler.builder()
                .from(compiler)
                .addSource("AcmeListenerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME listener config.
                         */
                        interface AcmeListenerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeListenerConfig> {
                                /**
                                 * Listen address.
                                 *
                                 * @param host host
                                 */
                                @ConfiguredOption(value = "0.0.0.0")
                                Builder host(String host);
                        
                                /**
                                 * Listen port.
                                 *
                                 * @param port port
                                 */
                                @ConfiguredOption(value = "0")
                                Builder port(int port);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result1.success(), is(true));
        assertThat(result1.diagnostics(), is(Matchers.empty()));

        var result2 = TestCompiler.builder()
                .from(compiler)
                .addClasspathEntry(result1.classOutput())
                .addSource("AcmeServerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME server configuration.
                         */
                        interface AcmeServerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeServerConfig> {
                                @ConfiguredOption(mergeWithParent = true)
                                Builder host(AcmeListenerConfig listener);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result2.success(), is(true));

        var schema = result2.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeServerConfig",
                                "description": "ACME server configuration",
                                "options": [
                                    {
                                        "type": "com.acme.AcmeListenerConfig",
                                        "merge": true
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testAllowedValuesWithoutEnum() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeLoggerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        import io.helidon.config.metadata.ConfiguredValue;
                        
                        /**
                         * ACME logger configuration.
                         */
                        interface AcmeLoggerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeLoggerConfig> {
                                /**
                                 * Logger level.
                                 *
                                 * @param level host
                                 * @return this builder
                                 */
                                @ConfiguredOption(allowedValues = {
                                        @ConfiguredValue(value = "DEBUG", description = "Debug level"),
                                        @ConfiguredValue(value = "INFO", description = "Info level"),
                                        @ConfiguredValue(value = "WARNING", description = "Warning level"),
                                        @ConfiguredValue(value = "ERROR", description = "Error level")
                                })
                                Builder level(String level);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeLoggerConfig",
                                "description": "ACME logger configuration",
                                "options": [
                                    {
                                        "key": "level",
                                        "description": "Logger level",
                                        "allowedValues": [
                                            {
                                                "value": "DEBUG",
                                                "description": "Debug level"
                                            },
                                            {
                                                "value": "INFO",
                                                "description": "Info level"
                                            },
                                            {
                                                "value": "WARNING",
                                                "description": "Warning level"
                                            },
                                            {
                                                "value": "ERROR",
                                                "description": "Error level"
                                            }
                                        ]
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeLoggerConfig.java:20: warning: Allowed values not backed by enum
                        @ConfiguredOption(allowedValues = {
                        ^""")));
    }

    @Test
    void testEnumOption() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeLoggerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        import io.helidon.config.metadata.ConfiguredValue;
                        
                        /**
                         * ACME logger configuration.
                         */
                        interface AcmeLoggerConfig {
                        
                            /**
                             * ACME logger level.
                             */
                            enum Level {
                                /**
                                 * Debug level.
                                 */
                                DEBUG,
                                /**
                                 * Info level.
                                 */
                                INFO,
                                /**
                                 * Warning level.
                                 */
                                WARNING,
                                /**
                                 * Error level.
                                 */
                                ERROR
                            }
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeLoggerConfig> {
                                /**
                                 * Logger level.
                                 *
                                 * @param level host
                                 * @return this builder
                                 */
                                @ConfiguredOption
                                Builder level(Level level);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeLoggerConfig",
                                "description": "ACME logger configuration",
                                "options": [
                                    {
                                        "key": "level",
                                        "type": "com.acme.AcmeLoggerConfig.Level",
                                        "description": "Logger level",
                                        "allowedValues": [
                                            {
                                                "value": "DEBUG",
                                                "description": "Debug level"
                                            },
                                            {
                                                "value": "INFO",
                                                "description": "Info level"
                                            },
                                            {
                                                "value": "WARNING",
                                                "description": "Warning level"
                                            },
                                            {
                                                "value": "ERROR",
                                                "description": "Error level"
                                            }
                                        ]
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testEnumOptionFromClasspath() throws IOException {
        var compiler = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .build();
        var result1 = TestCompiler.builder()
                .from(compiler)
                .addSource("AcmeLoggerConfig.java", """
                        package com.acme;
                        
                        /**
                         * ACME logger level.
                         */
                        enum Level {
                            /**
                             * Debug level.
                             */
                            DEBUG,
                            /**
                             * Info level.
                             */
                            INFO,
                            /**
                             * Warning level.
                             */
                            WARNING,
                            /**
                             * Error level.
                             */
                            ERROR
                        }
                        """)
                .build()
                .compile();
        assertThat(result1.success(), is(true));

        var result2 = TestCompiler.builder()
                .from(compiler)
                .addClasspathEntry(result1.classOutput())
                .addSource("AcmeLoggerConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        import io.helidon.config.metadata.ConfiguredValue;
                        
                        /**
                         * ACME logger configuration.
                         */
                        interface AcmeLoggerConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeLoggerConfig> {
                                /**
                                 * Logger level.
                                 *
                                 * @param level host
                                 * @return this builder
                                 */
                                @ConfiguredOption(allowedValues = {
                                        @ConfiguredValue(value = "DEBUG", description = "Debug level"),
                                        @ConfiguredValue(value = "INFO", description = "Info level"),
                                        @ConfiguredValue(value = "WARNING", description = "Warning level"),
                                        @ConfiguredValue(value = "ERROR", description = "Error level")
                                })
                                Builder level(Level level);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result2.success(), is(true));

        var schema = result2.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeLoggerConfig",
                                "description": "ACME logger configuration",
                                "options": [
                                    {
                                        "key": "level",
                                        "type": "com.acme.Level",
                                        "description": "Logger level",
                                        "allowedValues": [
                                            {
                                                "value": "DEBUG",
                                                "description": "Debug level"
                                            },
                                            {
                                                "value": "INFO",
                                                "description": "Info level"
                                            },
                                            {
                                                "value": "WARNING",
                                                "description": "Warning level"
                                            },
                                            {
                                                "value": "ERROR",
                                                "description": "Error level"
                                            }
                                        ]
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testRequiredOption() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                                /**
                                 * Name.
                                 *
                                 * @param name name
                                 * @return this builder
                                 */
                                @ConfiguredOption
                                Builder name(String name);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "name",
                                        "description": "Name"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testOptionDefaultValue() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                                /**
                                 * Name.
                                 *
                                 * @param name name
                                 * @return this builder
                                 */
                                @ConfiguredOption("Acme")
                                Builder name(String name);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "name",
                                        "description": "Name",
                                        "defaultValue": "Acme"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testOptionBadDefaultValues() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        @Configured(
                            description = "ACME configuration",
                            options = {
                                @ConfiguredOption(
                                        key = "double",
                                        description = "Double",
                                        type = Double.class,
                                        value = "abc"),
                                @ConfiguredOption(
                                        key = "boolean",
                                        description = "Boolean",
                                        type = Boolean.class,
                                        value = "abc"),
                                @ConfiguredOption(
                                        key = "byte",
                                        description = "Byte",
                                        type = Byte.class,
                                        value = "abc"),
                                @ConfiguredOption(
                                        key = "short",
                                        description = "Short",
                                        type = Short.class,
                                        value = "abc"),
                                @ConfiguredOption(
                                        key = "float",
                                        description = "Float",
                                        type = Float.class,
                                        value = "abc"),
                                @ConfiguredOption(
                                        key = "long",
                                        description = "Long",
                                        type = Long.class,
                                        value = "abc"),
                                @ConfiguredOption(
                                        key = "int",
                                        description = "Int",
                                        type = Integer.class,
                                        value = "abc")
                            })
                        interface AcmeConfig {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "boolean",
                                        "type": "java.lang.Boolean",
                                        "description": "Boolean",
                                        "defaultValue": "abc"
                                    },
                                    {
                                        "key": "byte",
                                        "type": "java.lang.Byte",
                                        "description": "Byte",
                                        "defaultValue": "abc"
                                    },
                                    {
                                        "key": "double",
                                        "type": "java.lang.Double",
                                        "description": "Double",
                                        "defaultValue": "abc"
                                    },
                                    {
                                        "key": "float",
                                        "type": "java.lang.Float",
                                        "description": "Float",
                                        "defaultValue": "abc"
                                    },
                                    {
                                        "key": "int",
                                        "type": "java.lang.Integer",
                                        "description": "Int",
                                        "defaultValue": "abc"
                                    },
                                    {
                                        "key": "long",
                                        "type": "java.lang.Long",
                                        "description": "Long",
                                        "defaultValue": "abc"
                                    },
                                    {
                                        "key": "short",
                                        "type": "java.lang.Short",
                                        "description": "Short",
                                        "defaultValue": "abc"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:9: warning: Unable to parse "abc" as java.lang.Double
                        @ConfiguredOption(
                        ^""", """
                /AcmeConfig.java:19: warning: Unable to parse "abc" as java.lang.Byte
                        @ConfiguredOption(
                        ^""", """
                /AcmeConfig.java:24: warning: Unable to parse "abc" as java.lang.Short
                        @ConfiguredOption(
                        ^""", """
                /AcmeConfig.java:29: warning: Unable to parse "abc" as java.lang.Float
                        @ConfiguredOption(
                        ^""", """
                /AcmeConfig.java:34: warning: Unable to parse "abc" as java.lang.Long
                        @ConfiguredOption(
                        ^""", """
                /AcmeConfig.java:39: warning: Unable to parse "abc" as java.lang.Integer
                        @ConfiguredOption(
                        ^""")));
    }

    @Test
    void testOptionDescription() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                                @ConfiguredOption(value = "Acme", description = "Name")
                                Builder name(String name);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "name",
                                        "description": "Name",
                                        "defaultValue": "Acme"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testOptionList() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.List;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * Names.
                                 * @param names names
                                 * @return this builder
                                 */
                                @ConfiguredOption
                                Builder names(List<String> names);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "names",
                                        "description": "Names",
                                        "kind": "LIST"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testOptionPathType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.nio.file.Path;
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        @Configured
                        interface AcmeConfig {
                            /**
                             * Option1.
                             */
                            @ConfiguredOption
                            void option1(Path it);
                        }
                        """)
                .build()
                .compile();

        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "java.nio.file.Path",
                                        "description": "Option1"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testOptionMapStringInt() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * Option1.
                                 * @param option1 option1
                                 * @return this builder
                                 */
                                @ConfiguredOption
                                Builder option1(Map<String, Integer> option1);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "java.lang.Integer",
                                        "description": "Option1",
                                        "kind": "MAP"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testOptionMapStringString() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * Option1.
                                 * @param option1 option1
                                 * @return this builder
                                 */
                                @ConfiguredOption
                                Builder option1(Map<String, String> option1);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "description": "Option1",
                                        "kind": "MAP"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testExplicitKind() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        import io.helidon.config.metadata.ConfiguredOption.Kind;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * Option1.
                                 * @param option1 option1
                                 * @return this builder
                                 */
                                @ConfiguredOption(kind = Kind.LIST)
                                Builder option1(Map<String, String> option1);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "description": "Option1",
                                        "kind": "LIST"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testExplicitOptionType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * Option1.
                                 * @param str str
                                 * @return this builder
                                 */
                                @ConfiguredOption(key = "option1",
                                                  type = Boolean.class)
                                Builder option1(String str);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "java.lang.Boolean",
                                        "description": "Option1"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testMultipleMethodOptions() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * Option1.
                                 * @param str str
                                 * @return this builder
                                 */
                                @ConfiguredOption(key = "option2",
                                                  description = "Option2",
                                                  type = Boolean.class,
                                                  value = "true")
                                @ConfiguredOption
                                Builder option1(String str);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "description": "Option1"
                                    },
                                    {
                                        "key": "option2",
                                        "type": "java.lang.Boolean",
                                        "description": "Option2",
                                        "defaultValue": "true"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testOptionReturnType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        @Configured
                        interface AcmeConfig {
                            /**
                             * Option1.
                             * @return boolean
                             */
                            @ConfiguredOption
                            boolean option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));

        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "java.lang.Boolean",
                                        "description": "Option1"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testMapOption() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * Option1.
                                 * @param map map
                                 * @return this builder
                                 */
                                @ConfiguredOption
                                Builder option1(Map<String, Integer> map);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "java.lang.Integer",
                                        "description": "Option1",
                                        "kind": "MAP"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testMapValueType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import java.time.Duration;
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * Option1.
                                 * @param map map
                                 * @return this builder
                                 */
                                @ConfiguredOption
                                Builder option1(Map<String, Duration> map);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "java.time.Duration",
                                        "description": "Option1",
                                        "kind": "MAP"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testObjectValue() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * Option1.
                                 * @param object object
                                 * @return this builder
                                 */
                                @ConfiguredOption
                                Builder option1(Object object);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "java.lang.Object",
                                        "description": "Option1"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:20: warning: Invalid option type: java.lang.Object
                        Builder option1(Object object);
                                               ^""")));
    }

    @Test
    void testListObject() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.List;
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * Option1.
                                 * @param list list
                                 * @return this builder
                                 */
                                @ConfiguredOption
                                Builder option1(List<Object> list);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "java.lang.Object",
                                        "description": "Option1",
                                        "kind": "LIST"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:21: warning: Invalid option type: java.lang.Object
                        Builder option1(List<Object> list);
                                                     ^""")));
    }

    @Test
    void testMapObjectValue() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * Option1.
                                 * @param map map
                                 * @return this builder
                                 */
                                @ConfiguredOption
                                Builder option1(Map<String, Object> map);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "java.lang.Object",
                                        "description": "Option1",
                                        "kind": "MAP"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:21: warning: Invalid option type: java.lang.Object
                        Builder option1(Map<String, Object> map);
                                                            ^""")));
    }

    @Test
    void testObjectValueLiteral() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        import io.helidon.config.metadata.ConfiguredOption.Kind;
                        
                        @Configured(description = "ACME configuration",
                                    options = {
                                        @ConfiguredOption(key = "option1",
                                                          description = "Option1",
                                                          type = Object.class)
                                    })
                        interface AcmeConfig {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "java.lang.Object",
                                        "description": "Option1"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:10: warning: Invalid option type: java.lang.Object
                                @ConfiguredOption(key = "option1",
                                ^""")));
    }

    @Test
    void testListObjectLiteral() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        import io.helidon.config.metadata.ConfiguredOption.Kind;
                        
                        @Configured(description = "ACME configuration",
                                    options = {
                                        @ConfiguredOption(key = "option1",
                                                          description = "Option1",
                                                          type = Object.class,
                                                          kind = Kind.LIST)
                                    })
                        interface AcmeConfig {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "java.lang.Object",
                                        "description": "Option1",
                                        "kind": "LIST"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:10: warning: Invalid option type: java.lang.Object
                                @ConfiguredOption(key = "option1",
                                ^""")));
    }

    @Test
    void testMapObjectLiteral() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        import io.helidon.config.metadata.ConfiguredOption.Kind;
                        
                        @Configured(description = "ACME configuration",
                                    options = {
                                        @ConfiguredOption(key = "option1",
                                                          description = "Option1",
                                                          type = Object.class,
                                                          kind = Kind.MAP)
                                    })
                        interface AcmeConfig {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "java.lang.Object",
                                        "description": "Option1",
                                        "kind": "MAP"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:10: warning: Invalid option type: java.lang.Object
                                @ConfiguredOption(key = "option1",
                                ^""")));
    }

    @Test
    void testOptionLiterals() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        @Configured(description = "ACME configuration",
                                    options = {
                                        @ConfiguredOption(key = "option1",
                                                          description = "Option1",
                                                          type = Boolean.class),
                                        @ConfiguredOption(key = "option2",
                                                          description = "Option2",
                                                          type = Integer.class)
                                    })
                        interface AcmeConfig {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "java.lang.Boolean",
                                        "description": "Option1"
                                    },
                                    {
                                        "key": "option2",
                                        "type": "java.lang.Integer",
                                        "description": "Option2"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testNonCapitalizedTypeDescription() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * acme configuration.
                         */
                        @Configured
                        interface AcmeConfig {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "acme configuration"
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:8: warning: Description is not capitalized
                @Configured
                ^""")));
    }

    @Test
    void testNonCapitalizedOptionDescription() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * option1.
                                 * @param str str
                                 * @return this builder
                                 */
                                @ConfiguredOption
                                Builder option1(String str);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "description": "option1"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:19: warning: Description is not capitalized
                        @ConfiguredOption
                        ^""")));
    }

    @Test
    void testStandaloneTypeWithoutPrefix() {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * ACME configuration.
                         */
                        @Configured(root = true)
                        interface AcmeConfig {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(false));
        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:8: error: Standalone type does not have a prefix
                @Configured(root = true)
                ^""")));
    }

    @Test
    void testStandaloneProviderType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeContract.java", """
                        package com.acme;
                        
                        interface AcmeContract {
                        }
                        """)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * ACME configuration.
                         */
                        @Configured(root = true,
                                    prefix = "acme",
                                    provides = AcmeContract.class)
                        interface AcmeConfig {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "standalone": true,
                                "description": "ACME configuration",
                                "prefix": "acme",
                                "provides": [
                                    "com.acme.AcmeContract"
                                ]
                            }
                        ]
                    }
                ]""")));
    }

    @Test
    void testProviderTypeWithoutPrefix() {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeContract.java", """
                        package com.acme;
                        
                        interface AcmeContract {
                        }
                        """)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        /**
                         * ACME configuration.
                         */
                        @Configured(provides = AcmeContract.class)
                        interface AcmeConfig {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(false));
        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:8: error: Provider type does not have a prefix
                @Configured(provides = AcmeContract.class)
                ^""")));
    }

    @Test
    void testRequiredOptionWithNoDefaultValue() {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * Option1.
                                 * @param str str
                                 * @return this builder
                                 */
                                @ConfiguredOption(required = true, value = "abc")
                                Builder option1(String str);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(false));
        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:19: error: Required option cannot have a default value
                        @ConfiguredOption(required = true, value = "abc")
                        ^""")));
    }

    @Test
    void testMissingTypeJavadoc() {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        
                        @Configured
                        interface AcmeConfig {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:5: warning: Missing javadoc
                @Configured
                ^""")));
    }

    @Test
    void testMissingOptionJavadoc() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                @ConfiguredOption
                                Builder option1(String str);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "description": "<code>N/A</code>"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:14: warning: Missing javadoc
                        @ConfiguredOption
                        ^""")));
    }

    @Test
    void testMapInvalidKeyType() {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * Option1.
                                 * @param map map
                                 * @return this builder
                                 */
                                @ConfiguredOption
                                Builder option1(Map<Integer, String> map);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(false));
        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:21: error: Map key type must be String
                        Builder option1(Map<Integer, String> map);
                                                             ^""")));
    }

    @Test
    void testAllowedValueInvalidDescription() {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addClasspath(Builder.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeValue.java", """
                        package com.acme;
                        
                        enum AcmeValue {
                            ABC
                        }
                        """)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import java.time.Duration;
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        import io.helidon.config.metadata.ConfiguredValue;
                        
                        /**
                         * ACME configuration.
                         */
                        interface AcmeConfig {
                        
                            @Configured
                            interface Builder extends io.helidon.common.Builder<Builder, AcmeConfig> {
                        
                                /**
                                 * Option1.
                                 * @param str str
                                 * @return this builder
                                 */
                                @ConfiguredOption(allowedValues = {
                                        @ConfiguredValue(value = "ABC", description = " ")
                                })
                                Builder option1(AcmeValue str);
                            }
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(false));
        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:23: error: Invalid description
                                @ConfiguredValue(value = "ABC", description = " ")
                                ^""")));
    }

    @Test
    void testInvalidOptionReturnType() {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        @Configured
                        interface AcmeConfig {
                            /**
                             * Option1.
                             */
                            @ConfiguredOption
                            void option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(false));
        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:15: error: Unable to infer option type
                    void option1();
                         ^""")));
    }

    @Test
    void testOptionLiteralMapType() {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        @Configured(
                                description = "ACME configuration",
                                options = {
                                    @ConfiguredOption(
                                            key = "option1",
                                            description = "Option1",
                                            type = Map.class)
                                })
                        interface AcmeConfig {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(false));
        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:10: error: Invalid literal option type: java.util.Map
                            @ConfiguredOption(
                            ^""")));
    }

    @Test
    void testOptionLiteralCollectionType() {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.Collection;
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        @Configured(
                                description = "ACME configuration",
                                options = {
                                    @ConfiguredOption(
                                            key = "option1",
                                            description = "Option1",
                                            type = Collection.class)
                                })
                        interface AcmeConfig {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:10: warning: Invalid iterable type: java.util.Collection
                            @ConfiguredOption(
                            ^""")));
    }

    @Test
    void testOptionLiteralIterableType() {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        @Configured(
                                description = "ACME configuration",
                                options = {
                                    @ConfiguredOption(
                                            key = "option1",
                                            description = "Option1",
                                            type = Iterable.class)
                                })
                        interface AcmeConfig {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(false));
        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:9: error: Invalid literal option type: java.lang.Iterable
                            @ConfiguredOption(
                            ^""")));
    }

    @Test
    void testOptionInvalidMapType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addProcessor(AptProcessor::new)
                .addSource("JsonValue.java", """
                        package com.acme;
                        
                        interface JsonValue {
                        }
                        """)
                .addSource("JsonObject.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        
                        interface JsonObject extends Map<String, JsonValue> {
                        }
                        """)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import java.util.HashMap;
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        @Configured
                        interface AcmeConfig {
                            /**
                             * Option1.
                             */
                            @ConfiguredOption
                            void option1(JsonObject jsonObject);
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "com.acme.JsonObject",
                                        "description": "Option1"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:16: warning: Invalid map type: com.acme.JsonObject
                    void option1(JsonObject jsonObject);
                                            ^""")));
    }

    @Test
    void testOptionRawCustomIterableType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeIterable.java", """
                        package com.acme;
                        
                        interface AcmeIterable<X, Z> extends Iterable<Z> {
                        }
                        """)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        @Configured
                        interface AcmeConfig {
                            /**
                             * Option1.
                             */
                            @ConfiguredOption
                            @SuppressWarnings("rawtypes")
                            void option1(AcmeIterable<?, ?> it);
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "com.acme.AcmeIterable",
                                        "description": "Option1"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:16: warning: Invalid iterable type: com.acme.AcmeIterable<?, ?>
                    void option1(AcmeIterable<?, ?> it);
                                                    ^""")));
    }

    @Test
    void testOptionRawCustomMapType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(Configured.class)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeMap.java", """
                        package com.acme;
                        
                        interface AcmeMap<X, Z> extends java.util.Map<String, Z> {
                        }
                        """)
                .addSource("AcmeConfig.java", """
                        package com.acme;
                        
                        import io.helidon.config.metadata.Configured;
                        import io.helidon.config.metadata.ConfiguredOption;
                        
                        /**
                         * ACME configuration.
                         */
                        @Configured
                        interface AcmeConfig {
                            /**
                             * Option1.
                             */
                            @ConfiguredOption
                            @SuppressWarnings("rawtypes")
                            void option1(AcmeMap<?, ?> it);
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.classOutput().resolve(CmModel.LOCATION);
        assertThat(Files.exists(schema), is(true));

        var actual = formatJson(Files.readString(schema));
        assertThat(actual, is(formatJson("""
                [
                    {
                        "module": "unnamed module",
                        "types": [
                            {
                                "type": "com.acme.AcmeConfig",
                                "description": "ACME configuration",
                                "options": [
                                    {
                                        "key": "option1",
                                        "type": "com.acme.AcmeMap",
                                        "description": "Option1"
                                    }
                                ]
                            }
                        ]
                    }
                ]""")));

        assertThat(result.diagnostics(), is(List.of("""
                /AcmeConfig.java:16: warning: Invalid map type: com.acme.AcmeMap<?, ?>
                    void option1(AcmeMap<?, ?> it);
                                               ^""")));
    }

    static String formatJson(@Language("json") String str) {
        var baos = new ByteArrayOutputStream();
        try (var printer = new PrintWriter(baos)) {
            var is = new ByteArrayInputStream(str.getBytes());
            Hson.parse(is).write(printer, true);
        }
        return baos.toString();
    }
}
