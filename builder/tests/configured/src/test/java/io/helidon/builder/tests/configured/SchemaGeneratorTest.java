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
package io.helidon.builder.tests.configured;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Builder;
import io.helidon.common.mapper.OptionalValue;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;

import org.junit.jupiter.api.Test;

import static io.helidon.codegen.testing.CodegenMatchers.matches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@code BuilderCodegen}.
 */
@SuppressWarnings("removal")
class SchemaGeneratorTest {

    static final List<Class<?>> CLASSPATH = List.of(
            Option.class,
            Builder.class,
            Config.class,
            io.helidon.common.config.Config.class,
            OptionalValue.class,
            Configured.class);

    static final List<String> OPTS = List.of(
            "-Xlint:-removal",
            "-Xlint:-deprecation");

    @Test
    void testRoot() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured("acme")
                        interface AcmeConfigBlueprint {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(root = true, prefix = "acme", description = "ACME config")
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testNonRoot() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(description = "ACME config")
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testPrefixedNonRoot() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured(value = "acme", root = false)
                        interface AcmeConfigBlueprint {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(prefix = "acme", description = "ACME config")
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testJavadocEscapes() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME "config".
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(description = "ACME \\"config\\"")
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testCharArray() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            char[] option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(key = "option1", description = "Option1", type = String.class, required = true)
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testDefault() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            @Option.Default("value1")
                            String option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(key = "option1", description = "Option1", type = String.class, value = "value1")
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testDefaultBoolean() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            @Option.DefaultBoolean(true)
                            boolean option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(key = "option1", description = "Option1", type = Boolean.class, value = "true")
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testDefaultInt() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            @Option.DefaultInt(1)
                            int option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(key = "option1", description = "Option1", type = Integer.class, value = "1")
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testDefaultLong() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            @Option.DefaultLong(1)
                            long option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(key = "option1", description = "Option1", type = Long.class, value = "1")
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testDefaultDoubleValue() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            @Option.DefaultDouble(1)
                            double option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(key = "option1", description = "Option1", type = Double.class, value = "1.0")
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testList() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.List;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            List<String> option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(
                            key = "option1",
                            description = "Option1",
                            type = String.class,
                            kind = ConfiguredOption.Kind.LIST)
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testSet() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Set;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            Set<String> option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(
                            key = "option1",
                            description = "Option1",
                            type = String.class,
                            kind = ConfiguredOption.Kind.LIST)
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testOptionalList() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.List;
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            Optional<List<String>> option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(
                            key = "option1",
                            description = "Option1",
                            type = String.class,
                            kind = ConfiguredOption.Kind.LIST)
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testOptionalSet() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        import java.util.Set;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            Optional<Set<String>> option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(
                            key = "option1",
                            description = "Option1",
                            type = String.class,
                            kind = ConfiguredOption.Kind.LIST)
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testMap() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            Map<String, Integer> option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(
                            key = "option1",
                            description = "Option1",
                            type = Integer.class,
                            kind = ConfiguredOption.Kind.MAP)
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testOptionalMap() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            Optional<Map<String, Integer>> option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(
                            key = "option1",
                            description = "Option1",
                            type = Integer.class,
                            kind = ConfiguredOption.Kind.MAP)
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testAllowedValuesWithoutEnum() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            @Option.AllowedValue(value = "value1", description = "Value1")
                            @Option.AllowedValue(value = "value2", description = "Value2")
                            String option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(
                            key = "option1",
                            description = "Option1",
                            type = String.class,
                            required = true,
                            allowedValues = {
                                @ConfiguredValue(value = "value1", description = "Value1"),
                                @ConfiguredValue(value = "value2", description = "Value2")
                            })
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testEnumAllowedValues() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeMode.java", """
                        package com.acme;
                        
                        /**
                         * ACME mode.
                         */
                        enum AcmeMode {
                            /**
                             * Mode1.
                             */
                            MODE1,
                            /**
                             * Mode2.
                             */
                            MODE2,
                        }
                        """)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            AcmeMode option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(
                            key = "option1",
                            description = "Option1",
                            type = AcmeMode.class,
                            required = true,
                            allowedValues = {
                                @ConfiguredValue(value = "MODE1", description = "Mode1"),
                                @ConfiguredValue(value = "MODE2", description = "Mode2")
                            })
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testPrefixWithConstant() throws IOException {
        var compiler = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .options(OPTS)
                .addProcessor(AptProcessor::new)
                .build();

        var result1 = TestCompiler.builder()
                .from(compiler)
                .addSource("AcmeConstants.java", """
                        package com.acme;
                        
                        class AcmeConstants {
                            static final String BASE_PREFIX = "acme";
                        }
                        """)
                .build()
                .compile();
        assertThat(result1.success(), is(true));

        var result2 = TestCompiler.builder()
                .from(compiler)
                .addClasspathEntry(result1.classOutput())
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured(AcmeConstants.BASE_PREFIX + ".config")
                        interface AcmeConfigBlueprint {
                        }
                        """)
                .build()
                .compile();
        assertThat(result2.success(), is(true));
        var schema = result2.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(root = true, prefix = "acme.config", description = "ACME config")
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testExternalEnum() throws IOException {
        var compiler = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .options(OPTS)
                .addProcessor(AptProcessor::new)
                .build();

        var result1 = TestCompiler.builder()
                .from(compiler)
                .addSource("AcmeMode.java", """
                        package com.acme;
                        
                        /**
                         * ACME mode.
                         */
                        enum AcmeMode {
                            /**
                             * Mode1.
                             */
                            MODE1,
                            /**
                             * Mode2.
                             */
                            MODE2,
                        }
                        """)
                .build()
                .compile();
        assertThat(result1.success(), is(true));

        var result2 = TestCompiler.builder()
                .from(compiler)
                .printDiagnostics(false)
                .addClasspathEntry(result1.classOutput())
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            AcmeMode option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result2.success(), is(true));
        var schema = result2.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(
                            key = "option1",
                            description = "Option1",
                            type = AcmeMode.class,
                            required = true,
                            allowedValues = {
                                @ConfiguredValue(value = "MODE1", description = "<code>N/A</code>"),
                                @ConfiguredValue(value = "MODE2", description = "<code>N/A</code>")
                            })
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));

        assertThat(result2.diagnostics(), is(hasItems("""
                /AcmeConfigBlueprint.java:21: warning: Missing javadoc: com.acme.AcmeMode.MODE1
                    AcmeMode option1();
                             ^""",
                """
                /AcmeConfigBlueprint.java:21: warning: Missing javadoc: com.acme.AcmeMode.MODE2
                    AcmeMode option1();
                             ^""")));
    }

    @Test
    void testProviderList() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeService.java", """
                        package com.acme;
                        
                        @SuppressWarnings("ALL")
                        interface AcmeService extends io.helidon.common.config.NamedService {
                        }
                        """)
                .addSource("AcmeServiceProvider.java", """
                        package com.acme;
                        
                        @SuppressWarnings("ALL")
                        interface AcmeServiceProvider extends io.helidon.common.config.ConfiguredProvider<AcmeService> {
                        }
                        """)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.List;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            @Option.Provider(AcmeServiceProvider.class)
                            List<AcmeService> option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(
                            key = "option1",
                            description = "Option1",
                            type = AcmeService.class,
                            kind = ConfiguredOption.Kind.LIST,
                            provider = true),
                        @ConfiguredOption(
                            key = "option1-discover-services",
                            description = "Whether to enable automatic service discovery for <code>option1</code>",
                            type = Boolean.class,
                            value = "true")
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testOptionalProvider() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeService.java", """
                        package com.acme;
                        
                        @SuppressWarnings("ALL")
                        interface AcmeService extends io.helidon.common.config.NamedService {
                        }
                        """)
                .addSource("AcmeServiceProvider.java", """
                        package com.acme;
                        
                        @SuppressWarnings("ALL")
                        interface AcmeServiceProvider extends io.helidon.common.config.ConfiguredProvider<AcmeService> {
                        }
                        """)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            @Option.Provider(AcmeServiceProvider.class)
                            Optional<AcmeService> option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(key = "option1", description = "Option1", type = AcmeService.class, provider = true),
                        @ConfiguredOption(
                            key = "option1-discover-services",
                            description = "Whether to enable automatic service discovery for <code>option1</code>",
                            type = Boolean.class,
                            value = "true")
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testProviderType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeService.java", """
                        package com.acme;
                        
                        @SuppressWarnings("ALL")
                        interface AcmeService extends io.helidon.config.NamedService {
                        }
                        """)
                .addSource("AcmeLogging.java", """
                        package com.acme;
                        import io.helidon.builder.api.RuntimeType;
                        
                        @SuppressWarnings("ALL")
                        interface AcmeLogging extends AcmeService, RuntimeType.Api<AcmeLoggingConfig> {
                            static AcmeLogging create(AcmeLoggingConfig config) {
                                throw new UnsupportedOperationException();
                            }
                            static AcmeLogging create(java.util.function.Consumer<AcmeLoggingConfig.Builder> consumer) {
                                throw new UnsupportedOperationException();
                            }
                            static AcmeLoggingConfig.Builder builder() {
                                throw new UnsupportedOperationException();
                            }
                        }
                        """)
                .addSource("AcmeServiceProvider.java", """
                        package com.acme;
                        
                        @SuppressWarnings("ALL")
                        interface AcmeServiceProvider extends io.helidon.config.ConfiguredProvider<AcmeService> {
                        }
                        """)
                .addSource("AcmeLoggingConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME logging config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured(value = "logging", root = false)
                        @Prototype.Provides(AcmeServiceProvider.class)
                        interface AcmeLoggingConfigBlueprint extends Prototype.Factory<AcmeLogging> {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeLoggingConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(prefix = "logging", description = "ACME logging config", provides = AcmeService.class)
                //...
                public interface AcmeLoggingConfig extends AcmeLoggingConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testParameterizedProviderType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeService.java", """
                        package com.acme;
                        
                        @SuppressWarnings("ALL")
                        interface AcmeService extends io.helidon.common.config.NamedService {
                        }
                        """)
                .addSource("AcmeServiceProvider.java", """
                        package com.acme;
                        
                        @SuppressWarnings("ALL")
                        interface AcmeServiceProvider<T extends AcmeService> extends io.helidon.common.config.ConfiguredProvider<T> {
                        }
                        """)
                .addSource("AcmeLoggingServiceConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME logging service config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured(value = "logging", root = false)
                        @Prototype.Provides(AcmeServiceProvider.class)
                        interface AcmeLoggingServiceConfigBlueprint {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeLoggingServiceConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(prefix = "logging", description = "ACME logging service config", provides = AcmeService.class)
                //...
                public interface AcmeLoggingServiceConfig extends AcmeLoggingServiceConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testNamedServiceWithoutProvider() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeService.java", """
                        package com.acme;
                        
                        @SuppressWarnings("ALL")
                        interface AcmeService extends io.helidon.common.config.NamedService {
                        }
                        """)
                .addSource("AcmeLogging.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.RuntimeType;
                        
                        interface AcmeLogging extends AcmeService, RuntimeType.Api<AcmeLoggingConfig> {
                            static AcmeLogging create(AcmeLoggingConfig config) {
                                throw new UnsupportedOperationException();
                            }
                            static AcmeLogging create(java.util.function.Consumer<AcmeLoggingConfig.Builder> consumer) {
                                throw new UnsupportedOperationException();
                            }
                            static AcmeLoggingConfig.Builder builder() {
                                throw new UnsupportedOperationException();
                            }
                        }
                        """)
                .addSource("AcmeLoggingConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME logging config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeLoggingConfigBlueprint extends Prototype.Factory<AcmeLogging> {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeLoggingConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(description = "ACME logging config")
                //...
                public interface AcmeLoggingConfig extends AcmeLoggingConfigBlueprint, Prototype.Api {
                //...
                }
                """));

        assertThat(result.diagnostics(), is(hasItems("""
                /AcmeLoggingConfigBlueprint.java:12: warning: Configured provider not declared for: com.acme.AcmeLogging
                interface AcmeLoggingConfigBlueprint extends Prototype.Factory<AcmeLogging> {
                ^""")));
    }

    @Test
    void testPrototypeForwardReference() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeObjectBlueprint.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.Prototype;
                        
                        /**
                          * ACME object.
                          */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeObjectBlueprint {
                        }
                        """)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             *
                             * @return option1
                             */
                            @Option.Configured
                            AcmeObject option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(key = "option1", description = "Option1", type = AcmeObject.class, required = true)
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
        }

        @Test
        void testBlueprintWithoutJavadoc() throws IOException {
            var result = TestCompiler.builder()
                    .currentRelease()
                    .addClasspath(CLASSPATH)
                    .addProcessor(AptProcessor::new)
                    .options(OPTS)
                    .addSource("AcmeObjectBlueprint.java", """
                            package com.acme;
                            
                            import io.helidon.builder.api.RuntimeType;
                            import java.util.function.Consumer;
                            
                            /**
                             * ACME object.
                             */
                            interface Acme extends RuntimeType.Api<AcmeConfig> {
                                static Acme create(AcmeConfig config) {
                                    throw new UnsupportedOperationException();
                                }
                                static Acme create(java.util.function.Consumer<AcmeConfig.Builder> consumer) {
                                    throw new UnsupportedOperationException();
                                }
                                static AcmeConfig.Builder builder() {
                                    throw new UnsupportedOperationException();
                                }
                            }
                            """)
                    .addSource("AcmeConfigBlueprint.java", """
                            package com.acme;
                            
                            import java.util.Optional;
                            import io.helidon.builder.api.Prototype;
                            import io.helidon.builder.api.Option;
                            
                            @Prototype.Blueprint
                            @Prototype.Configured
                            interface AcmeConfigBlueprint extends Prototype.Factory<Acme> {
                            }
                            """)
                    .build()
                    .compile();
            assertThat(result.success(), is(true));
            var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
            assertThat(Files.exists(schema), is(true));

            var actual = Files.readString(schema);
            assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(description = "ACME object")
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testStandaloneBlueprintWithoutJavadoc() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .printDiagnostics(false)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(description = "<code>N/A</code>")
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));

        assertThat(result.diagnostics(), is(hasItems("""
                /AcmeConfigBlueprint.java:9: warning: Missing javadoc
                interface AcmeConfigBlueprint {
                ^""")));
    }

    @Test
    void testJavadocFistSentence() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        
                        /**
                         * ACME
                         * config.
                         * Use with caution.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(description = "ACME config")
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testInheritedDescription() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .options(OPTS)
                .addSource("AcmeNamed.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        
                        /**
                         * ACME config.
                         */
                        interface AcmeNamed {
                        
                            /**
                             * Name.
                             *
                             * @return name
                             */
                            Optional<String> name();
                        }
                        """)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint extends AcmeNamed {
                        
                            @Override
                            @Option.Configured
                            Optional<String> name();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(key = "name", description = "Name", type = String.class)
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testExternalOptionInterface() throws IOException {
        var compiler = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .options(OPTS)
                .addProcessor(AptProcessor::new)
                .build();

        var result1 = TestCompiler.builder()
                .from(compiler)
                .addSource("AcmeNamed.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        
                        /**
                         * ACME config.
                         */
                        interface AcmeNamed {
                        
                            /**
                             * Name.
                             *
                             * @return name
                             */
                            Optional<String> name();
                        }
                        """)
                .build()
                .compile();
        assertThat(result1.success(), is(true));

        var result2 = TestCompiler.builder()
                .from(compiler)
                .printDiagnostics(false)
                .addClasspathEntry(result1.classOutput())
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint extends AcmeNamed {
                        
                            @Override
                            @Option.Configured
                            Optional<String> name();
                        }
                        """)
                .build()
                .compile();
        assertThat(result2.success(), is(true));
        var schema = result2.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(key = "name", description = "<code>N/A</code>", type = String.class)
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));

        assertThat(result2.diagnostics(), is(hasItems("""
                /AcmeConfigBlueprint.java:16: warning: Missing javadoc
                    Optional<String> name();
                                     ^""")));
    }

    @Test
    void testMapObject() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(CLASSPATH)
                .options(OPTS)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Map;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             */
                            @Option.Configured
                            Map<String, Object> option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(
                            key = "option1",
                            description = "Option1",
                            type = Object.class,
                            kind = ConfiguredOption.Kind.MAP)
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testListObject() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(CLASSPATH)
                .options(OPTS)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.List;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             */
                            @Option.Configured
                            List<Object> option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(
                            key = "option1",
                            description = "Option1",
                            type = Object.class,
                            kind = ConfiguredOption.Kind.LIST)
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testObjectValue() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(CLASSPATH)
                .options(OPTS)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.List;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Option1.
                             */
                            @Option.Configured
                            Object option1();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(key = "option1", description = "Option1", type = Object.class, required = true)
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testCustomMethod() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .options(OPTS)
                .addProcessor(AptProcessor::new)
                .addSource("AcmePrivateKey.java", """
                        package com.acme;
                        
                        /**
                         * ACME private key.
                         */
                        interface AcmePrivateKey {
                        }
                        """)
                .addSource("AcmePrivateKeyConfigBlueprint.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.Prototype;
                        
                        /**
                         * ACME private key.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmePrivateKeyConfigBlueprint {
                        }
                        """)
                .addSource("AcmeConfigMethods.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        class AcmeConfigMethods {
                        
                            @Prototype.RuntimeTypeFactoryMethod("privatekey")
                            static Optional<AcmePrivateKey> createPrivateKey(AcmePrivateKeyConfig config) {
                                throw new UnsupportedOperationException();
                            }
                        }
                        """)
                .addSource("AcmeConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        @Prototype.CustomMethods(AcmeConfigMethods.class)
                        interface AcmeConfigBlueprint {
                        
                            /**
                             * Private key.
                             */
                            @Option.Configured
                            Optional<AcmePrivateKey> privatekey();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME config",
                    options = {
                        @ConfiguredOption(key = "privatekey", description = "Private key", type = AcmePrivateKeyConfig.class)
                    })
                //...
                public interface AcmeConfig extends AcmeConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testMerge() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .options(OPTS)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeListener.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.RuntimeType;
                        
                        interface AcmeListener extends RuntimeType.Api<AcmeListenerConfig> {
                            static AcmeListener create(AcmeListenerConfig config) {
                                throw new UnsupportedOperationException();
                            }
                            static AcmeListener create(java.util.function.Consumer<AcmeListenerConfig.Builder> consumer) {
                                throw new UnsupportedOperationException();
                            }
                            static AcmeListenerConfig.Builder builder() {
                                throw new UnsupportedOperationException();
                            }
                        }
                        """)
                .addSource("AcmeListenerConfigBlueprint.java", """
                        package com.acme;
                        
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME listener config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeListenerConfigBlueprint extends Prototype.Factory<AcmeListener> {
                        
                            /**
                             * Port.
                             */
                            @Option.Configured
                            int port();
                        }
                        """)
                .addSource("AcmeServerConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME server config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeServerConfigBlueprint {
                        
                            /**
                             * Listener.
                             */
                            @Option.Configured(merge = true)
                            Optional<AcmeListener> listener();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeServerConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME server config",
                    options = {
                        @ConfiguredOption(type = AcmeListener.class, mergeWithParent = true)
                    })
                //...
                public interface AcmeServerConfig extends AcmeServerConfigBlueprint, Prototype.Api {
                //...
                }
                """));
    }

    @Test
    void testMergeValueType() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(CLASSPATH)
                .options(OPTS)
                .addProcessor(AptProcessor::new)
                .addSource("AcmeServerConfigBlueprint.java", """
                        package com.acme;
                        
                        import java.util.Optional;
                        import io.helidon.builder.api.Prototype;
                        import io.helidon.builder.api.Option;
                        
                        /**
                         * ACME server config.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface AcmeServerConfigBlueprint {
                        
                            /**
                             * Port.
                             */
                            @Option.Configured(merge = true)
                            int port();
                        }
                        """)
                .build()
                .compile();
        assertThat(result.success(), is(true));
        var schema = result.sourceOutput().resolve("com/acme/AcmeServerConfig.java");
        assertThat(Files.exists(schema), is(true));

        var actual = Files.readString(schema);
        assertThat(actual, matches("""
                //...
                package com.acme;
                //...
                @Configured(
                    description = "ACME server config",
                    options = {
                        @ConfiguredOption(key = "port", description = "Port", type = Integer.class)
                    })
                //...
                public interface AcmeServerConfig extends AcmeServerConfigBlueprint, Prototype.Api {
                //...
                }
                """));

        assertThat(result.diagnostics(), is(hasItems("""
                /AcmeServerConfigBlueprint.java:18: warning: Invalid merge option type: java.lang.Integer
                    int port();
                        ^""")));
    }
}
