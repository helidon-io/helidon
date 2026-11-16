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

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;

import io.helidon.codegen.CodegenScope;
import io.helidon.codegen.testing.TestCompiler;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class AptScopeTest {
    @Test
    void testMavenOutput() {
        assertScope("file:///project/target/classes", null, "");
        assertScope("file:///project/target/test-classes", null, "test");
        assertScope("file:///project/target/integration-classes", null, "integration");
    }

    @Test
    void testGradleOutput() {
        assertScope("file:///project/build/classes/java/main", null, "");
        assertScope("file:///project/build/classes/java/test", null, "test");
        assertScope("file:///project/custom-build/classes/java/integrationTest", null, "integrationTest");
        assertScope("file:///project/build/classes/java/test-classes", null, "test-classes");
        assertScope("file:///C:/workspace/project%20name/build/classes/java/test", null, "test");
    }

    @Test
    void testEclipseOutput() {
        assertScope("file:///project/bin/main", null, "");
        assertScope("file:///project/bin/test", null, "test");
        assertScope("file:///project/bin", "file:///project/.apt_generated_tests", "test");
        assertScope("file:///project/target/classes", "file:///project/.apt_generated_tests", "test");
        assertScope("file:///project/bin", "file:///project/.apt_generated", "");
    }

    @Test
    void testRecognizedClassOutputTakesPrecedence() {
        assertScope("file:///project/build/classes/java/main", "file:///project/.apt_generated_tests", "");
        assertScope("file:///project/bin/main", "file:///project/.apt_generated_tests", "");
        assertScope("file:///project/target/test-classes", "file:///project/.apt_generated", "test");
    }

    @Test
    void testUnknownOutput() {
        assertScope("file:///project/test/custom", null, "");
        assertScope("file:///project/test", null, "");
        assertScope("file:///project/build/classes/java/test/nested", null, "");
        assertScope("file:///project/output", "file:///project/test/generated", "");
        assertScope(null, null, "");
        assertScope("opaque:classes", null, "");
        assertScope(null, "file:///project/.apt_generated_tests", "test");
    }

    @Test
    void testExplicitScopeTakesPrecedence() {
        assertThat(scope(null, null, "custom"), is(new CodegenScope("custom")));
        assertThat(scope("file:///project/build/classes/java/test", null, ""), is(CodegenScope.PRODUCTION));
    }

    private static void assertScope(String classes, String sources, String expected) {
        assertThat("Class output: " + classes + ", source output: " + sources,
                   scope(classes, sources, null), is(new CodegenScope(expected)));
    }

    private static CodegenScope scope(String classes, String sources, String explicit) {
        var observed = new AtomicReference<CodegenScope>();
        var result = TestCompiler.builder()
                .addProcessor(new AbstractProcessor() {
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
                            var filer = new OutputFiler(processingEnv.getFiler(), classes, sources);
                            Map<String, String> options = explicit == null
                                    ? Map.of()
                                    : Map.of("helidon.codegen.scope", explicit);
                            var environment = new ScopeEnvironment(processingEnv, filer, options);
                            observed.set(AptContext.create(environment, Set.of()).scope());
                        }
                        return false;
                    }
                })
                .currentRelease()
                .addSource("Example.java", "class Example {}")
                .build()
                .compile();
        assertThat(result.diagnostics().toString(), result.success(), is(true));
        return observed.get();
    }

    private record ScopeEnvironment(ProcessingEnvironment delegate, Filer filer, Map<String, String> options)
            implements ProcessingEnvironment {
        @Override
        public Map<String, String> getOptions() {
            return options;
        }

        @Override
        public Messager getMessager() {
            return delegate.getMessager();
        }

        @Override
        public Filer getFiler() {
            return filer;
        }

        @Override
        public Elements getElementUtils() {
            return delegate.getElementUtils();
        }

        @Override
        public Types getTypeUtils() {
            return delegate.getTypeUtils();
        }

        @Override
        public SourceVersion getSourceVersion() {
            return delegate.getSourceVersion();
        }

        @Override
        public Locale getLocale() {
            return delegate.getLocale();
        }
    }

    private record OutputFiler(Filer delegate, String classes, String sources) implements Filer {
        @Override
        public FileObject getResource(JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName)
                throws IOException {
            if (!"does.not.exist".contentEquals(pkg)) {
                return delegate.getResource(location, pkg, relativeName);
            }
            String directory = location == StandardLocation.CLASS_OUTPUT ? classes : sources;
            if (directory == null) {
                throw new IOException("Output location unavailable");
            }
            var outputUri = URI.create(directory + "/does/not/exist/" + relativeName);
            return new SimpleJavaFileObject(URI.create("file:///scope-probe"), JavaFileObject.Kind.OTHER) {
                @Override
                public URI toUri() {
                    return outputUri;
                }
            };
        }

        @Override
        public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) throws IOException {
            return delegate.createSourceFile(name, originatingElements);
        }

        @Override
        public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
            return delegate.createClassFile(name, originatingElements);
        }

        @Override
        public FileObject createResource(JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName,
                                         Element... originatingElements) throws IOException {
            return delegate.createResource(location, pkg, relativeName, originatingElements);
        }
    }
}
