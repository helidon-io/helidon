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

package io.helidon.codegen.api.stability;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;

import io.helidon.codegen.ClassCode;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.Api;
import io.helidon.common.types.TypeName;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class ApiStabilityProcessorTest {

    @Test
    void testPrivate() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.Internal.class,
                                     ClassCode.class,
                                     TypeName.class,
                                     ClassModel.class),
                               List.of("-Ahelidon.api.internal=fail"),
                               new JavaSourceFromString("MyClass.java", """
                                       package com.example;
                                       
                                       import io.helidon.codegen.ClassCode;
                                       
                                       class MyClass {
                                           void doStuff() {
                                               ClassCode c = new io.helidon.codegen.ClassCode(null, null, null);
                                           }
                                       }
                                       """));
        assertThat(messages, hasItems(
                "error: Usage of Helidon APIs annotated with @Api.Internal. Do not use these APIs. "
                        + "This will fail the build in the next major release of Helidon",
                "error: This ERROR can be suppressed with @SuppressWarnings(\"helidon:api:internal\") or compiler argument "
                        + "-Ahelidon.api.internal=ignore",
                "error: /MyClass.java:[3,1] io.helidon.codegen.ClassCode is internal API\n"
                        + "  import io.helidon.codegen.ClassCode;\n"
                        + "  ^",
                "error: /MyClass.java:[7,9] io.helidon.codegen.ClassCode is internal API\n"
                        + "          ClassCode c = new io.helidon.codegen.ClassCode(null, null, null);\n"
                        + "          ^"
        ));
    }

    @Test
    void testSupportedCompilerOptionsDeclared() {
        assertThat(new ApiStabilityProcessor().getSupportedOptions(),
                   is(Set.of("helidon.api",
                             "helidon.api.preview",
                             "helidon.api.incubating",
                             "helidon.api.internal",
                             "helidon.api.deprecated")));
    }

    @Test
    void testSpecificCompilerOptionsSupported() throws IOException {
        for (var testCase : List.of(
                new CompilerOptionCase("helidon.api.preview", "@Api.Preview", "PreviewApi"),
                new CompilerOptionCase("helidon.api.incubating", "@Api.Incubating", "IncubatingApi"),
                new CompilerOptionCase("helidon.api.internal", "@Api.Internal", "InternalApi"),
                new CompilerOptionCase("helidon.api.deprecated", "@Api.Deprecated", "DeprecatedApi")
        )) {
            var messages = compile(new ApiStabilityProcessor(),
                                   paths(Api.class),
                                   List.of("-A" + testCase.option() + "=ignore"),
                                   new JavaSourceFromString(testCase.apiClassName() + ".java",
                                                            apiSource(testCase.apiAnnotation(), testCase.apiClassName())),
                                   new JavaSourceFromString("Uses" + testCase.apiClassName() + ".java",
                                                            usageSource("Uses" + testCase.apiClassName(),
                                                                        testCase.apiClassName())));

            assertThat("Expected compiler option " + testCase.option() + " to suppress "
                               + testCase.apiClassName() + " usages",
                       messages,
                       empty());
        }
    }

    @Test
    void testGlobalCompilerOptionSupported() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.class),
                               List.of("-Ahelidon.api=ignore"),
                               new JavaSourceFromString("PreviewApi.java", apiSource("@Api.Preview", "PreviewApi")),
                               new JavaSourceFromString("IncubatingApi.java", apiSource("@Api.Incubating",
                                                                                         "IncubatingApi")),
                               new JavaSourceFromString("InternalApi.java", apiSource("@Api.Internal", "InternalApi")),
                               new JavaSourceFromString("DeprecatedApi.java", apiSource("@Api.Deprecated",
                                                                                         "DeprecatedApi")),
                               new JavaSourceFromString("UsesAllApis.java", """
                                       package com.example;
                                       
                                       class UsesAllApis {
                                           PreviewApi preview;
                                           IncubatingApi incubating;
                                           InternalApi internal;
                                           DeprecatedApi deprecatedApi;
                                       }
                                       """));

        assertThat(messages, empty());
    }

    @Test
    void testOtherHelidonOptionsDoNotFailProcessorInitialization() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.class),
                               List.of("-Ahelidon.codegen.scope=production"),
                               new JavaSourceFromString("MyClass.java", """
                                       package com.example;
                                       
                                       class MyClass {
                                       }
                                       """));

        assertThat(messages,
                   not(hasItem(containsString("Unrecognized/unsupported Helidon option configured"))));
    }

    @Test
    void testSourceVersionFollowsCompiler() {
        assertThat(new ApiStabilityProcessor().getSupportedSourceVersion(), is(SourceVersion.latestSupported()));
    }

    @Test
    void testJavaAllSuppressionSupported() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.class),
                               List.of("-Ahelidon.api.internal=fail"),
                               new JavaSourceFromString("InternalApi.java", """
                                       package com.example;
                                       
                                       import io.helidon.common.Api;
                                       
                                       @Api.Internal
                                       public class InternalApi {
                                       }
                                       """),
                               new JavaSourceFromString("UsesInternalApi.java", """
                                       package com.example;
                                       
                                       @SuppressWarnings("all")
                                       class UsesInternalApi {
                                           InternalApi api;
                                       }
                                       """));

        assertThat(messages, empty());
    }

    @Test
    void testSupportedSuppressionsSupported() throws IOException {
        for (var testCase : List.of(
                new SuppressionCase(Api.SUPPRESS_ALL, "@Api.Internal", "InternalApi"),
                new SuppressionCase(Api.SUPPRESS_PREVIEW, "@Api.Preview", "PreviewApi"),
                new SuppressionCase(Api.SUPPRESS_INCUBATING, "@Api.Incubating", "IncubatingApi"),
                new SuppressionCase(Api.SUPPRESS_INTERNAL, "@Api.Internal", "InternalApi"),
                new SuppressionCase(Api.SUPPRESS_DEPRECATED, "@Api.Deprecated", "DeprecatedApi"),
                new SuppressionCase(ApiStabilityProcessor.SUPPRESS_DEPRECATION, "@Api.Deprecated", "DeprecatedApi")
        )) {
            var messages = compile(new ApiStabilityProcessor(),
                                   paths(Api.class),
                                   List.of("-Ahelidon.api=fail"),
                                   new JavaSourceFromString(testCase.apiClassName() + ".java",
                                                            apiSource(testCase.apiAnnotation(), testCase.apiClassName())),
                                   new JavaSourceFromString("Uses" + testCase.apiClassName() + ".java",
                                                            suppressedUsageSource("Uses" + testCase.apiClassName(),
                                                                                  testCase.apiClassName(),
                                                                                  testCase.suppression())));

            assertThat("Expected suppression " + testCase.suppression() + " to suppress "
                               + testCase.apiClassName() + " usages",
                       messages,
                       empty());
        }
    }

    @Test
    void testLocalVariableSuppressionSupported() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.class),
                               List.of("-Ahelidon.api.internal=fail"),
                               new JavaSourceFromString("InternalApi.java", """
                                       package com.example;
                                       
                                       import io.helidon.common.Api;
                                       
                                       @Api.Internal
                                       public class InternalApi {
                                       }
                                       """),
                               new JavaSourceFromString("UsesInternalApi.java", """
                                       package com.example;
                                       
                                       class UsesInternalApi {
                                           void use() {
                                               @SuppressWarnings("helidon:api:internal")
                                               InternalApi api = null;
                                           }
                                       }
                                       """));

        assertThat(messages, empty());
    }

    private static List<String> compile(Processor processor,
                                        List<Path> classpath,
                                        List<String> compilerArguments,
                                        JavaFileObject... compilationUnits) throws IOException {
        List<String> options = new ArrayList<>(compilerArguments);
        options.add("--release");
        options.add("21");
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<>();
        var manager = compiler.getStandardFileManager(diagnostics, null, null);
        var workDir = Files.createTempDirectory("apt-test");
        var classOutput = Files.createDirectory(workDir.resolve("classes"));
        manager.setLocationFromPaths(StandardLocation.SOURCE_PATH, List.of(classOutput));
        manager.setLocationFromPaths(StandardLocation.CLASS_PATH, classpath);
        manager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classOutput));
        var task = compiler.getTask(null, manager, diagnostics, options, null, List.of(compilationUnits));
        task.setProcessors(List.of(processor));
        task.call();
        var messages = new ArrayList<String>();
        for (var diagnostic : diagnostics.getDiagnostics()) {
            messages.add(diagnostic.toString());
        }
        return messages;
    }

    private static String apiSource(String apiAnnotation, String className) {
        return """
                package com.example;
                
                import io.helidon.common.Api;
                
                %s
                public class %s {
                }
                """.formatted(apiAnnotation, className);
    }

    private static String usageSource(String className, String usedType) {
        return """
                package com.example;
                
                class %s {
                    %s api;
                }
                """.formatted(className, usedType);
    }

    private static String suppressedUsageSource(String className, String usedType, String suppression) {
        return """
                package com.example;
                
                @SuppressWarnings("%s")
                class %s {
                    %s api;
                }
                """.formatted(suppression, className, usedType);
    }

    private static List<Path> paths(Class<?>... classes) {
        try {
            List<Path> paths = new ArrayList<>();
            for (var cls : classes) {
                paths.add(Paths.get(cls.getProtectionDomain().getCodeSource().getLocation().toURI()));
            }
            return paths;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static class JavaSourceFromString extends SimpleJavaFileObject {

        private final String code;

        JavaSourceFromString(String fileName, @Language("java") String code) {
            super(URI.create("string:///" + fileName), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    private record CompilerOptionCase(String option,
                                      String apiAnnotation,
                                      String apiClassName) {
    }

    private record SuppressionCase(String suppression,
                                   String apiAnnotation,
                                   String apiClassName) {
    }
}
