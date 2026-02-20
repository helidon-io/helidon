package io.helidon.codegen.api.stability;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;

import io.helidon.codegen.ClassCode;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.Api;
import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;

public class ApiStabilityProcessorTest {

    @Test
    void testPrivate() throws IOException {
        var messages = compile(new ApiStabilityProcessor(),
                               paths(Api.Private.class,
                                     ClassCode.class,
                                     TypeName.class,
                                     ClassModel.class),
                               List.of("-Ahelidon.api.private=fail"),
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
                "error: Usage of Helidon APIs annotated with @Api.Private. Do not use these APIs.",
                "error: This ERROR can be suppressed with @SuppressWarnings(\"helidon:api:private\") or compiler argument "
                        + "-Ahelidon.api.private=ignore",
                "error: /MyClass.java:[3,1] io.helidon.codegen.ClassCode is private API\n"
                        + "  import io.helidon.codegen.ClassCode;\n"
                        + "  ^",
                "error: /MyClass.java:[7,9] io.helidon.codegen.ClassCode is private API\n"
                        + "          ClassCode c = new io.helidon.codegen.ClassCode(null, null, null);\n"
                        + "          ^"
        ));
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
