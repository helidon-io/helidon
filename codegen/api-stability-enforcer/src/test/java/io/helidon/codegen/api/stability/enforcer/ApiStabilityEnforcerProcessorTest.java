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

package io.helidon.codegen.api.stability.enforcer;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

class ApiStabilityEnforcerProcessorTest {
    @Test
    void testSourceVersionFollowsCompiler() {
        assertThat(new ApiStabilityEnforcerProcessor().getSupportedSourceVersion(),
                   is(SourceVersion.latestSupported()));
    }

    @Test
    void testMissingApiAnnotationsSkipsEnforcement() throws IOException {
        var messages = compileWithoutApi(new ApiStabilityEnforcerProcessor(),
                                         new JavaSourceFromString("TestApi.java", """
                                                 package io.helidon.common;
                                                 
                                                 public class TestApi {
                                                 }
                                                 """));

        assertThat(messages, empty());
    }

    @Test
    void testMissingTopLevelAnnotationFails() throws IOException {
        var messages = compileWithApi(new ApiStabilityEnforcerProcessor(),
                                      new JavaSourceFromString("TestApi.java", """
                                              package io.helidon.common;
                                              
                                              public class TestApi {
                                              }
                                              """));

        assertThat(messages,
                   hasItem("""
                                   /TestApi.java:3: error: Public API io.helidon.common.TestApi is missing stability annotation (@Api.*)
                                   public class TestApi {
                                          ^\
                                   """));
    }

    @Test
    void testDuplicateTopLevelAnnotationsFail() throws IOException {
        var messages = compileWithApi(new ApiStabilityEnforcerProcessor(),
                                      new JavaSourceFromString("TestApi.java", """
                                              package io.helidon.common;
                                              
                                              @Api.Stable
                                              @Api.Incubating
                                              public class TestApi {
                                              }
                                              """));

        assertThat(messages,
                   hasItem("""
                                   /TestApi.java:5: error: Public API io.helidon.common.TestApi has more than one stability annotation (@Api.*)
                                   public class TestApi {
                                          ^\
                                   """));
    }

    @Test
    void testTopLevelAnnotationAllowsMemberAndNestedTypes() throws IOException {
        var messages = compileWithApi(new ApiStabilityEnforcerProcessor(),
                                      new JavaSourceFromString("TestApi.java", """
                                              package io.helidon.common;
                                              
                                              @Api.Stable
                                              public class TestApi {
                                                  @Api.Incubating
                                                  public TestApi(String value) {
                                                  }
                                              
                                                  @Api.Preview
                                                  public void preview() {
                                                  }
                                              
                                                  public static class Nested {
                                                      @Api.Internal
                                                      public void internal() {
                                                      }
                                                  }
                                              }
                                              """));

        assertThat(messages, empty());
    }

    @Test
    void testDeprecatedDoesNotSatisfyTopLevelStabilityRequirement() throws IOException {
        var messages = compileWithApi(new ApiStabilityEnforcerProcessor(),
                                      new JavaSourceFromString("TestApi.java", """
                                              package io.helidon.common;
                                              
                                              @Deprecated
                                              public class TestApi {
                                              }
                                              """));

        assertThat(messages,
                   hasItem("""
                                   /TestApi.java:4: error: Public API io.helidon.common.TestApi is missing stability annotation (@Api.*)
                                   public class TestApi {
                                          ^\
                                   """));
    }

    @Test
    void testStableAndDeprecatedTopLevelAnnotationIsAllowed() throws IOException {
        var messages = compileWithApi(new ApiStabilityEnforcerProcessor(),
                                      new JavaSourceFromString("TestApi.java", """
                                              package io.helidon.common;
                                              
                                              @Api.Stable
                                              @Deprecated
                                              public class TestApi {
                                              }
                                              """));

        assertThat(messages, empty());
    }

    @Test
    void testNestedMethodCannotIncreaseStabilityAboveEnclosingType() throws IOException {
        var messages = compileWithApi(new ApiStabilityEnforcerProcessor(),
                                      new JavaSourceFromString("TestApi.java", """
                                              package io.helidon.common;
                                              
                                              @Api.Incubating
                                              public class TestApi {
                                                  @Api.Preview
                                                  public void previewMethod() {
                                                  }
                                              }
                                              """));

        assertThat(messages,
                   hasItem("""
                                   /TestApi.java:6: error: Element previewMethod() must not declare @Api.Preview because enclosing API io.helidon.common.TestApi is @Api.Incubating
                                       public void previewMethod() {
                                                   ^\
                                   """));
    }

    @Test
    void testNestedTypeCannotIncreaseStabilityAboveEnclosingType() throws IOException {
        var messages = compileWithApi(new ApiStabilityEnforcerProcessor(),
                                      new JavaSourceFromString("TestApi.java", """
                                              package io.helidon.common;
                                              
                                              @Api.Internal
                                              public class TestApi {
                                                  @Api.Preview
                                                  public static class Nested {
                                                  }
                                              }
                                              """));

        assertThat(messages,
                   hasItem("""
                                   /TestApi.java:6: error: Element io.helidon.common.TestApi.Nested must not declare @Api.Preview because enclosing API io.helidon.common.TestApi is @Api.Internal
                                       public static class Nested {
                                                     ^\
                                   """));
    }

    private static List<String> compileWithoutApi(Processor processor,
                                                  JavaFileObject... compilationUnits) throws IOException {
        return compile(processor, compilationUnits);
    }

    private static List<String> compileWithApi(Processor processor,
                                               JavaFileObject... compilationUnits) throws IOException {
        List<JavaFileObject> sources = new ArrayList<>();
        sources.add(new JavaSourceFromString("Api.java", """
                package io.helidon.common;
                
                final class Api {
                    public @interface Stable {
                    }
                
                    public @interface Preview {
                    }
                
                    public @interface Incubating {
                    }
                
                    public @interface Internal {
                    }
                }
                """));
        sources.addAll(List.of(compilationUnits));
        return compile(processor, sources.toArray(JavaFileObject[]::new));
    }

    private static List<String> compile(Processor processor,
                                        JavaFileObject... compilationUnits) throws IOException {
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            var workDir = Files.createTempDirectory("apt-test");
            var classOutput = Files.createDirectory(workDir.resolve("classes"));
            manager.setLocationFromPaths(StandardLocation.SOURCE_PATH, List.of(classOutput));
            manager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classOutput));

            List<JavaFileObject> sources = new ArrayList<>(List.of(compilationUnits));

            var task = compiler.getTask(null, manager, diagnostics, List.of("--release", "21"), null, sources);
            task.setProcessors(List.of(processor));
            task.call();
        }

        var messages = new ArrayList<String>();
        for (var diagnostic : diagnostics.getDiagnostics()) {
            messages.add(diagnostic.toString());
        }
        return messages;
    }

    private static class JavaSourceFromString extends SimpleJavaFileObject {
        private final String code;

        JavaSourceFromString(String fileName, String code) {
            super(URI.create("string:///" + fileName), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
