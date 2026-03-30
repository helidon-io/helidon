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
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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
                "error: Usage of Helidon APIs annotated with @Api.Internal. Do not use these APIs.",
                "error: This ERROR can be suppressed with @SuppressWarnings(\"helidon:api:internal\") or compiler argument "
                        + "-Ahelidon.api.internal=ignore",
                "error: /MyClass.java:[3,1] io.helidon.codegen.ClassCode is internal API",
                "error: /MyClass.java:[7,9] io.helidon.codegen.ClassCode is internal API"
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
    void testGlobalWarnOptionDowngradesDefaultFailures() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.class),
                               List.of("-Ahelidon.api=warn"),
                               new JavaSourceFromString("InternalApi.java", """
                                       package com.example;

                                       import io.helidon.common.Api;

                                       @Api.Internal
                                       public class InternalApi {
                                       }
                                       """),
                               new JavaSourceFromString("IncubatingApi.java", """
                                       package com.example;

                                       import io.helidon.common.Api;

                                       @Api.Incubating
                                       public class IncubatingApi {
                                       }
                                       """),
                               new JavaSourceFromString("UsesApis.java", """
                                       package com.example;

                                       class UsesApis {
                                           InternalApi internalApi;
                                           IncubatingApi incubatingApi;
                                       }
                                       """));

        assertThat(messages, hasItem(containsString("warning: Usage of Helidon APIs annotated with @Api.Internal")));
        assertThat(messages, hasItem(containsString("warning: Usage of Helidon APIs annotated with @Api.Incubating")));
        assertThat(messages, not(hasItem(containsString("error: Usage of Helidon APIs annotated with @Api.Internal"))));
        assertThat(messages, not(hasItem(containsString("error: Usage of Helidon APIs annotated with @Api.Incubating"))));
    }

    @Test
    void testSpecificOptionOverridesGlobalIgnore() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.class),
                               List.of("-Ahelidon.api=ignore", "-Ahelidon.api.internal=fail"),
                               new JavaSourceFromString("InternalApi.java", """
                                       package com.example;

                                       import io.helidon.common.Api;

                                       @Api.Internal
                                       public class InternalApi {
                                       }
                                       """),
                               new JavaSourceFromString("IncubatingApi.java", """
                                       package com.example;

                                       import io.helidon.common.Api;

                                       @Api.Incubating
                                       public class IncubatingApi {
                                       }
                                       """),
                               new JavaSourceFromString("UsesApis.java", """
                                       package com.example;

                                       class UsesApis {
                                           InternalApi internalApi;
                                           IncubatingApi incubatingApi;
                                       }
                                       """));

        assertThat(messages, hasItem(containsString("error: Usage of Helidon APIs annotated with @Api.Internal")));
        assertThat(messages, not(hasItem(containsString("Usage of Helidon APIs annotated with @Api.Incubating"))));
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
    void testImportedApiWarningsCanBeSuppressedOnTopLevelDeclarations() throws IOException {
        for (var testCase : List.of(
                new TopLevelSuppressionCase("package-info.java", """
                        @SuppressWarnings("helidon:api:internal")
                        package com.example;

                        import com.example.InternalApi;
                        """),
                new TopLevelSuppressionCase("UsesInternalApi.java", """
                        package com.example;

                        import com.example.InternalApi;

                        @SuppressWarnings("helidon:api:internal")
                        @interface UsesInternalApi {
                        }
                        """)
        )) {
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
                                   new JavaSourceFromString(testCase.fileName(), testCase.source()));

            assertThat("Expected top-level suppression in " + testCase.fileName() + " to suppress imported API usages",
                       messages,
                       empty());
        }
    }

    @Test
    void testInternalUsagesFailByDefault() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.class),
                               List.of(),
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
                                           InternalApi api;
                                       }
                                       """));

        assertThat("Messages: " + messages,
                   messages,
                   hasItem(containsString("error: Usage of Helidon APIs annotated with @Api.Internal")));
        assertThat(countContaining(messages, "/UsesInternalApi.java:["), is(1));
    }

    @Test
    void testIncubatingMethodUsageFailsByDefault() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.class),
                               List.of(),
                               new JavaSourceFromString("StableApi.java", """
                                       package com.example;

                                       import io.helidon.common.Api;

                                       @Api.Stable
                                       public class StableApi {
                                           @Api.Incubating
                                           public static void incubatingMethod() {
                                           }
                                       }
                                       """),
                               new JavaSourceFromString("UsesStableApi.java", """
                                       package com.example;

                                       class UsesStableApi {
                                           void use() {
                                               StableApi.incubatingMethod();
                                           }
                                       }
                                       """));

        assertThat(messages, hasItem(containsString("error: Usage of Helidon APIs annotated with @Api.Incubating")));
        assertThat(messages, hasItem(containsString("incubatingMethod() is incubating API")));
        assertThat(countContaining(messages, "/UsesStableApi.java:["), is(1));
    }

    @Test
    void testConstructorAndMethodReferenceUsagesAreValidated() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.class),
                               List.of("-Ahelidon.api.incubating=fail"),
                               new JavaSourceFromString("StableApi.java", """
                                       package com.example;

                                       import io.helidon.common.Api;

                                       @Api.Stable
                                       public class StableApi {
                                           @Api.Incubating
                                           public StableApi() {
                                           }

                                           @Api.Incubating
                                           public static void incubatingMethod() {
                                           }
                                       }
                                       """),
                               new JavaSourceFromString("UsesStableApi.java", """
                                       package com.example;

                                       import java.util.function.Supplier;

                                       class UsesStableApi {
                                           StableApi direct = new StableApi();
                                           Supplier<StableApi> ctorRef = StableApi::new;
                                           Runnable methodRef = StableApi::incubatingMethod;
                                       }
                                       """));

        assertThat(messages, hasItem(containsString("error: Usage of Helidon APIs annotated with @Api.Incubating")));
        assertThat(countContaining(messages, "/UsesStableApi.java:["), is(3));
    }

    @Test
    void testTypeHeaderUsagesAreValidated() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.class),
                               List.of("-Ahelidon.api.incubating=fail"),
                               new JavaSourceFromString("IncubatingApi.java", """
                                       package com.example;

                                       import io.helidon.common.Api;

                                       @Api.Incubating
                                       public interface IncubatingApi {
                                       }
                                       """),
                               new JavaSourceFromString("UsesIncubatingClass.java", """
                                       package com.example;

                                       class UsesIncubatingClass implements IncubatingApi {
                                       }
                                       """),
                               new JavaSourceFromString("UsesIncubatingInterface.java", """
                                       package com.example;

                                       interface UsesIncubatingInterface extends IncubatingApi {
                                       }
                                       """),
                               new JavaSourceFromString("UsesIncubatingEnum.java", """
                                       package com.example;

                                       enum UsesIncubatingEnum implements IncubatingApi {
                                           INSTANCE
                                       }
                                       """),
                               new JavaSourceFromString("UsesIncubatingRecord.java", """
                                       package com.example;

                                       record UsesIncubatingRecord() implements IncubatingApi {
                                       }
                                       """));

        assertThat(messages, hasItem(containsString("error: Usage of Helidon APIs annotated with @Api.Incubating")));
        assertThat(countContaining(messages, "com.example.IncubatingApi is incubating API"), is(4));
    }

    @Test
    void testTypeHeaderSuppressionsSupported() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.class),
                               List.of("-Ahelidon.api.incubating=fail"),
                               new JavaSourceFromString("IncubatingApi.java", """
                                       package com.example;

                                       import io.helidon.common.Api;

                                       @Api.Incubating
                                       public interface IncubatingApi {
                                       }
                                       """),
                               new JavaSourceFromString("package-info.java", """
                                       @SuppressWarnings("helidon:api:incubating")
                                       package com.example;
                                       """),
                               new JavaSourceFromString("PackageSuppressedUsesIncubatingApi.java", """
                                       package com.example;

                                       interface PackageSuppressedUsesIncubatingApi extends IncubatingApi {
                                       }
                                       """),
                               new JavaSourceFromString("TypeSuppressedUsesIncubatingApi.java", """
                                       package com.example;

                                       @SuppressWarnings("helidon:api:incubating")
                                       interface TypeSuppressedUsesIncubatingApi extends IncubatingApi {
                                       }
                                       """));

        assertThat(messages, empty());
    }

    @Test
    void testModuleUsagesAreValidated() throws IOException {
        var messages = compileModule(new ApiStabilityProcessor(),
                                     paths(Api.class),
                                     List.of("-Ahelidon.api.internal=fail"),
                                     new JavaSourceFromString("InternalApi.java", """
                                             package com.example.api;

                                             import io.helidon.common.Api;

                                             @Api.Internal
                                             public interface InternalApi {
                                             }
                                             """),
                                     new JavaSourceFromString("module-info.java", """
                                             module com.example.app {
                                                 requires io.helidon.common;
                                                 uses com.example.api.InternalApi;
                                             }
                                             """));

        assertThat(messages, hasItem(containsString("error: Usage of Helidon APIs annotated with @Api.Internal")));
        assertThat(countContaining(messages, "com.example.api.InternalApi is internal API"), is(1));
    }

    @Test
    void testModuleProvidesImplementationUsagesAreValidated() throws IOException {
        var messages = compileModule(new ApiStabilityProcessor(),
                                     paths(Api.class),
                                     List.of("-Ahelidon.api.incubating=fail"),
                                     new JavaSourceFromString("ServiceApi.java", """
                                             package com.example.api;

                                             import io.helidon.common.Api;

                                             @Api.Stable
                                             public interface ServiceApi {
                                             }
                                             """),
                                     new JavaSourceFromString("IncubatingServiceApiImpl.java", """
                                             package com.example.impl;

                                             import com.example.api.ServiceApi;
                                             import io.helidon.common.Api;

                                             @Api.Incubating
                                             public class IncubatingServiceApiImpl implements ServiceApi {
                                             }
                                             """),
                                     new JavaSourceFromString("module-info.java", """
                                             module com.example.app {
                                                 requires io.helidon.common;
                                                 provides com.example.api.ServiceApi
                                                         with com.example.impl.IncubatingServiceApiImpl;
                                             }
                                             """));

        assertThat(messages, hasItem(containsString("error: Usage of Helidon APIs annotated with @Api.Incubating")));
        assertThat(countContaining(messages, "com.example.impl.IncubatingServiceApiImpl is incubating API"), is(1));
    }

    @Test
    void testModuleSuppressionsSupported() throws IOException {
        var messages = compileModule(new ApiStabilityProcessor(),
                                     paths(Api.class),
                                     List.of("-Ahelidon.api.internal=fail"),
                                     new JavaSourceFromString("InternalApi.java", """
                                             package com.example.api;

                                             import io.helidon.common.Api;

                                             @Api.Internal
                                             public interface InternalApi {
                                             }
                                             """),
                                     new JavaSourceFromString("module-info.java", """
                                             @SuppressWarnings("helidon:api:internal")
                                             module com.example.app {
                                                 requires io.helidon.common;
                                                 uses com.example.api.InternalApi;
                                             }
                                             """));

        assertThat(messages, empty());
    }

    @Test
    void testPreviewAndDeprecatedUsagesWarnByDefault() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.class),
                               List.of(),
                               new JavaSourceFromString("PreviewApi.java", """
                                       package com.example;

                                       import io.helidon.common.Api;

                                       @Api.Preview
                                       public class PreviewApi {
                                       }
                                       """),
                               new JavaSourceFromString("DeprecatedApi.java", """
                                       package com.example;

                                       import io.helidon.common.Api;

                                       @Api.Deprecated
                                       public class DeprecatedApi {
                                       }
                                       """),
                               new JavaSourceFromString("UsesApis.java", """
                                       package com.example;

                                       class UsesApis {
                                           PreviewApi preview;
                                           DeprecatedApi deprecatedApi;
                                       }
                                       """));

        assertThat(messages, hasItem(containsString("warning: Usage of Helidon APIs annotated with @Api.Preview")));
        assertThat(messages, hasItem(containsString("warning: Usage of Helidon APIs annotated with @Api.Deprecated")));
        assertThat(countContaining(messages, "/UsesApis.java:["), is(2));
    }

    @Test
    void testQualifiedUsagesReportOuterAnnotatedType() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.class),
                               List.of("-Ahelidon.api.incubating=fail"),
                               new JavaSourceFromString("IncubatingApi.java", """
                                       package com.example;

                                       import io.helidon.common.Api;

                                       @Api.Incubating
                                       public class IncubatingApi {
                                           public static class Nested {
                                           }

                                           public static void stableMethod() {
                                           }
                                       }
                                       """),
                               new JavaSourceFromString("UsesIncubatingApi.java", """
                                       package com.example;

                                       class UsesIncubatingApi {
                                           IncubatingApi.Nested nested;

                                           void use() {
                                               IncubatingApi.stableMethod();
                                           }
                                       }
                                       """));

        assertThat(messages,
                   hasItem(containsString("/UsesIncubatingApi.java:[4,5] com.example.IncubatingApi is incubating API")));
        assertThat(messages,
                   hasItem(containsString("/UsesIncubatingApi.java:[6,5] com.example.IncubatingApi is incubating API")));
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
        return compile(processor, classpath, List.of(), compilerArguments, compilationUnits);
    }

    private static List<String> compileModule(Processor processor,
                                              List<Path> modulePath,
                                              List<String> compilerArguments,
                                              JavaFileObject... compilationUnits) throws IOException {
        var workDir = Files.createTempDirectory("apt-module-test");
        var sourceRoot = Files.createDirectory(workDir.resolve("src"));
        var classOutput = Files.createDirectory(workDir.resolve("classes"));
        var sourceFiles = materializeModuleSources(sourceRoot, compilationUnits);

        List<String> args = new ArrayList<>(compilerArguments);
        args.add("--release");
        args.add("21");
        args.add("-d");
        args.add(classOutput.toString());
        args.add("--module-source-path");
        args.add(sourceRoot.toString());
        args.add("--module-path");
        args.add(joinPaths(modulePath));
        args.add("-processor");
        args.add(processor.getClass().getName());
        args.add("-processorpath");
        args.add(joinPaths(paths(ApiStabilityProcessor.class,
                                 Api.class,
                                 ClassCode.class,
                                 ClassModel.class,
                                 TypeName.class)));
        for (Path sourceFile : sourceFiles) {
            args.add(sourceFile.toString());
        }

        var javac = java.util.spi.ToolProvider.findFirst("javac")
                .orElseThrow(() -> new IllegalStateException("javac tool is not available"));
        var stdout = new StringWriter();
        var stderr = new StringWriter();
        javac.run(new PrintWriter(stdout), new PrintWriter(stderr), args.toArray(String[]::new));

        return (stderr.toString() + System.lineSeparator() + stdout)
                .lines()
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static List<String> compile(Processor processor,
                                        List<Path> classpath,
                                        List<Path> modulePath,
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
        if (!classpath.isEmpty()) {
            manager.setLocationFromPaths(StandardLocation.CLASS_PATH, classpath);
        }
        if (!modulePath.isEmpty()) {
            manager.setLocationFromPaths(StandardLocation.MODULE_PATH, modulePath);
        }
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

    private static int countContaining(List<String> messages, String snippet) {
        int count = 0;
        for (String message : messages) {
            if (message.contains(snippet)) {
                count++;
            }
        }
        return count;
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

    private static List<Path> materializeModuleSources(Path sourceRoot,
                                                       JavaFileObject... compilationUnits) throws IOException {
        String moduleName = null;
        for (var unit : compilationUnits) {
            if ("module-info.java".equals(fileName(unit))) {
                moduleName = moduleName(unit.getCharContent(true).toString());
                break;
            }
        }

        if (moduleName == null) {
            throw new IllegalArgumentException("Missing module-info.java source");
        }

        Path moduleRoot = sourceRoot.resolve(moduleName);
        List<Path> writtenSources = new ArrayList<>();

        for (var unit : compilationUnits) {
            String source = unit.getCharContent(true).toString();
            String fileName = fileName(unit);
            Path sourceFile;
            if ("module-info.java".equals(fileName)) {
                sourceFile = moduleRoot.resolve(fileName);
            } else {
                String packageName = packageName(source);
                sourceFile = packageName == null
                        ? moduleRoot.resolve(fileName)
                        : moduleRoot.resolve(packageName.replace('.', '/')).resolve(fileName);
            }

            Files.createDirectories(sourceFile.getParent());
            Files.writeString(sourceFile, source);
            writtenSources.add(sourceFile);
        }

        return writtenSources;
    }

    private static String packageName(String source) {
        var matcher = PACKAGE_PATTERN.matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String moduleName(String source) {
        var matcher = MODULE_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("Cannot determine module name from source:\n" + source);
    }

    private static String fileName(JavaFileObject source) {
        String name = source.getName();
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private static String joinPaths(List<Path> paths) {
        return paths.stream()
                .map(Path::toString)
                .distinct()
                .reduce((left, right) -> left + java.io.File.pathSeparator + right)
                .orElse("");
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

    private record TopLevelSuppressionCase(String fileName,
                                           String source) {
    }

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern MODULE_PATTERN = Pattern.compile("(?m)^\\s*(?:open\\s+)?module\\s+([\\w.]+)\\s*\\{");
}
