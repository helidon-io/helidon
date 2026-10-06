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

package io.helidon.service.tests.codegen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.GenericType;
import io.helidon.common.types.Annotation;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;

class EventObserverCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(Generated.class,
                                                          GenericType.class,
                                                          Annotation.class,
                                                          Prototype.class,
                                                          Service.class);
    private static final List<String> METHODS = List.of(
            "@Event.Observer @Service.Named(\"red\") void red(String event) {}",
            "@Event.Observer @Service.Named(\"red\") void anotherRed(String event) {}",
            "@Event.Observer @Service.Named(\"blue\") void blue(String event) {}",
            "@Event.Observer void red(Integer event) {}",
            "@Event.Observer @Event.AsyncObserver @Service.Named(\"red\") void dual(String event) {}"
    );

    @Test
    void testRepeatedCompilationAndDeclarationOrder(@TempDir Path directory) throws IOException {
        var reduced = compiler(directory.resolve("compile-1"), listener("Listener", List.of(METHODS.getFirst())))
                .build()
                .compile();
        assertThat(String.join("\n", reduced.diagnostics()), reduced.success(), is(true));
        assertThat(registrations(reduced).size(), is(1));

        var first = compiler(directory.resolve("compile-2"), listener("Listener", METHODS))
                .build()
                .compile();
        var second = compiler(directory.resolve("compile-3"), listener("Listener", METHODS.reversed()))
                .build()
                .compile();

        Map<String, String> firstRegistrations = registrations(first);
        Map<String, String> secondRegistrations = registrations(second);
        assertRegistrations(firstRegistrations);
        assertRegistrations(secondRegistrations);
        assertThat("Generated filenames must retain the same observer, event type and qualifiers across compilations",
                   secondRegistrations, is(firstRegistrations));
    }

    @Test
    void testObserverGeneratedInLaterRound(@TempDir Path directory) throws IOException {
        var result = compiler(directory.resolve("compile-1"), listener("Listener", List.of(METHODS.getFirst())))
                .processors(List.of(new LaterRoundObserverProcessor(), new AptProcessor()))
                .build()
                .compile();

        Map<String, String> generated = registrations(result);
        assertThat(generated.values(), hasSize(2));
        assertThat(generated.values().stream()
                           .map(source -> source.lines()
                                   .filter(line -> line.contains("manager.") && line.contains("register("))
                                   .map(String::strip)
                                   .map(line -> line.replaceAll("manager\\.<[^>]+>", "manager."))
                                   .findFirst().orElseThrow())
                           .toList(),
                   containsInAnyOrder("manager.register(EVENT_OBJECT, eventObserver::red, QUALIFIERS);",
                                      "manager.register(EVENT_OBJECT, eventObserver::later, QUALIFIERS);"));
        assertThat(generated.values().stream().filter(source -> source.contains("eventObserver::later"))
                           .findFirst().orElseThrow(), containsString("LaterListener eventObserver"));
    }

    private static TestCompiler.Builder compiler(Path directory, String source) {
        return TestCompiler.builder()
                .currentRelease()
                .workDir(directory)
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Listener.java", source);
    }

    private static String listener(String name, List<String> methods) {
        return """
                package com.example;

                import io.helidon.service.registry.Event;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class %s {
                    %s
                }
                """.formatted(name, String.join("\n", methods));
    }

    private static Map<String, String> registrations(TestCompiler.Result result) throws IOException {
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        Map<String, String> registrations = new TreeMap<>();
        try (var sources = Files.walk(result.sourceOutput())) {
            for (Path path : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                if (source.contains("implements GeneratedService.EventObserverRegistration")) {
                    // Compare the generated contract, excluding the timestamp in the generated annotation.
                    registrations.put(path.getFileName().toString(), source.substring(source.indexOf("class ")));
                }
            }
        }
        return registrations;
    }

    private static void assertRegistrations(Map<String, String> generated) {
        assertThat(generated.values(), hasSize(6));
        assertThat(generated.values().stream()
                           .filter(source -> source.contains("ResolvedType.create(\"java.lang.Integer\")"))
                           .count(), is(1L));
        assertThat(generated.values().stream()
                           .flatMap(source -> source.lines())
                           .filter(line -> line.contains("manager.") && line.contains("register"))
                           .map(String::strip)
                           .map(line -> line.replaceAll("manager\\.<[^>]+>", "manager."))
                           .toList(),
                   containsInAnyOrder("manager.register(EVENT_OBJECT, eventObserver::red, QUALIFIERS);",
                                      "manager.register(EVENT_OBJECT, eventObserver::anotherRed, QUALIFIERS);",
                                      "manager.register(EVENT_OBJECT, eventObserver::blue, QUALIFIERS);",
                                      "manager.register(EVENT_OBJECT, eventObserver::red, QUALIFIERS);",
                                      "manager.register(EVENT_OBJECT, eventObserver::dual, QUALIFIERS);",
                                      "manager.registerAsync(EVENT_OBJECT, eventObserver::dual, QUALIFIERS);"));
        for (String source : generated.values()) {
            if (source.contains("ResolvedType.create(\"java.lang.Integer\")")) {
                assertThat(source, containsString("QUALIFIERS = Set.of()"));
            } else {
                assertThat(source, containsString("ResolvedType.create(\"java.lang.String\")"));
                assertThat(source, containsString(source.contains("eventObserver::blue") ? "\"blue\"" : "\"red\""));
            }
        }
    }

    private static final class LaterRoundObserverProcessor extends AbstractProcessor {
        private boolean generated;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
            if (!generated && !roundEnvironment.processingOver()) {
                generated = true;
                try (Writer writer = processingEnv.getFiler().createSourceFile("com.example.LaterListener").openWriter()) {
                    writer.write(listener("LaterListener", List.of("@Event.Observer void later(Integer event) {}")));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return false;
        }
    }
}
