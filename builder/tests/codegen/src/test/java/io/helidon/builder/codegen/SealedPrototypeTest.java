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

package io.helidon.builder.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Builder;

import org.junit.jupiter.api.Test;

import static io.helidon.codegen.testing.CodegenMatchers.matches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

class SealedPrototypeTest {
    private static final List<Class<?>> CLASSPATH = List.of(Prototype.class, Builder.class);

    @Test
    void testGeneratedSource() throws IOException {
        var result = compiler()
                .addSource("SealedConfigBlueprint.java", """
                        package com.acme;

                        import io.helidon.builder.api.Prototype;

                        @Prototype.Blueprint
                        @Prototype.Sealed
                        interface SealedConfigBlueprint {
                            String value();
                        }
                        """)
                .build()
                .compile();

        assertThat(result.diagnostics().toString(), result.success(), is(true));
        var generatedSource = result.sourceOutput().resolve("com/acme/SealedConfig.java");
        assertThat(Files.exists(generatedSource), is(true));
        String declaration = "public sealed interface SealedConfig extends SealedConfigBlueprint, Prototype.Api "
                + "permits SealedConfig.BuilderBase.SealedConfigImpl {";
        assertThat(Files.readString(generatedSource), matches("""
                //...
                %s
                //...
                        protected final SealedConfig buildPrototype(SealedConfig.BuilderBase<?, ?> builder) {
                //...
                        }
                //...
                        private static final class SealedConfigImpl implements SealedConfig {
                //...
                    }
                //...
                }
                """.formatted(declaration)));
    }

    @Test
    void testExternalImplementationRejected() {
        var compiler = compiler().build();
        var prototypeResult = TestCompiler.builder()
                .from(compiler)
                .addSource("SealedConfigBlueprint.java", """
                        package com.acme;

                        import io.helidon.builder.api.Prototype;

                        @Prototype.Blueprint
                        @Prototype.Sealed
                        interface SealedConfigBlueprint {
                            String value();
                        }
                        """)
                .build()
                .compile();
        assertThat(prototypeResult.diagnostics().toString(), prototypeResult.success(), is(true));

        var implementationResult = TestCompiler.builder()
                .from(compiler)
                .printDiagnostics(false)
                .addClasspathEntry(prototypeResult.classOutput())
                .addSource("CustomConfig.java", """
                        package com.acme;

                        final class CustomConfig implements SealedConfig {
                            @Override
                            public String value() {
                                return "custom";
                            }
                        }
                        """)
                .build()
                .compile();

        assertThat(implementationResult.success(), is(false));
        assertThat(implementationResult.diagnostics(),
                   hasItem(containsString("is not allowed to extend sealed class: com.acme.SealedConfig")));
    }

    @Test
    void testCompiledSealedPrototypeInheritanceRejected() {
        var compiler = compiler().build();
        var parentResult = TestCompiler.builder()
                .from(compiler)
                .addSource("ParentConfigBlueprint.java", """
                        package com.acme;

                        import io.helidon.builder.api.Prototype;

                        @Prototype.Blueprint
                        @Prototype.Sealed
                        interface ParentConfigBlueprint<T> {
                        }
                        """)
                .build()
                .compile();
        assertThat(parentResult.diagnostics().toString(), parentResult.success(), is(true));

        var childResult = TestCompiler.builder()
                .from(compiler)
                .printDiagnostics(false)
                .addClasspathEntry(parentResult.classOutput())
                .addSource("ChildConfigBlueprint.java", """
                        package com.acme;

                        import io.helidon.builder.api.Prototype;

                        @Prototype.Blueprint
                        interface ChildConfigBlueprint<T> extends ParentConfig<T> {
                        }
                        """)
                .build()
                .compile();

        assertThat(childResult.success(), is(false));
        assertThat(childResult.diagnostics(), hasItem(containsString(
                "Prototype ChildConfig cannot extend sealed prototype ParentConfig. "
                        + "Sealed prototypes must be leaf prototypes.")));
    }

    @Test
    void testImplementedSealedPrototypeInheritanceRejected() {
        var compiler = compiler().build();
        var parentResult = TestCompiler.builder()
                .from(compiler)
                .addSource("ParentConfigBlueprint.java", """
                        package com.acme;

                        import io.helidon.builder.api.Prototype;

                        @Prototype.Blueprint
                        @Prototype.Sealed
                        interface ParentConfigBlueprint {
                        }
                        """)
                .build()
                .compile();
        assertThat(parentResult.diagnostics().toString(), parentResult.success(), is(true));

        var childResult = TestCompiler.builder()
                .from(compiler)
                .printDiagnostics(false)
                .addClasspathEntry(parentResult.classOutput())
                .addSource("ChildConfigBlueprint.java", """
                        package com.acme;

                        import io.helidon.builder.api.Prototype;

                        @Prototype.Blueprint
                        @Prototype.Implement("com.acme.ParentConfig")
                        interface ChildConfigBlueprint {
                        }
                        """)
                .build()
                .compile();

        assertThat(childResult.success(), is(false));
        assertThat(childResult.diagnostics(), hasItem(containsString(
                "Prototype ChildConfig cannot extend sealed prototype ParentConfig. "
                        + "Sealed prototypes must be leaf prototypes.")));
    }

    @Test
    void testImplementedGenericSealedPrototypeInSameRoundRejected() {
        var result = compiler()
                .addSource("ChildConfigBlueprint.java", """
                        package com.acme;

                        import io.helidon.builder.api.Prototype;

                        @Prototype.Blueprint
                        @Prototype.Implement("com.acme.ParentConfig<T>")
                        interface ChildConfigBlueprint<T> {
                        }
                        """)
                .addSource("ParentConfigBlueprint.java", """
                        package com.acme;

                        import io.helidon.builder.api.Prototype;

                        @Prototype.Blueprint
                        @Prototype.Sealed
                        interface ParentConfigBlueprint<T> {
                        }
                        """)
                .build()
                .compile();

        assertThat(result.success(), is(false));
        assertThat(result.diagnostics(), hasItem(containsString(
                "Prototype ChildConfig cannot extend sealed prototype ParentConfig. "
                        + "Sealed prototypes must be leaf prototypes.")));
    }

    private static TestCompiler.Builder compiler() {
        return TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new);
    }
}
