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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;

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
import io.helidon.common.types.ResolvedType;
import io.helidon.service.registry.EventManager;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;

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

    @Test
    void testGenericObserverMethods(@TempDir Path directory) throws IOException {
        var result = compiler(directory.resolve("compile-1"), listener("Listener", List.of(
                "@Event.Observer <T> void observe(T event) {}",
                "void observe(Integer event) {}",
                "@Event.AsyncObserver <T> void observeAsync(T event) {}")))
                .build()
                .compile();

        Map<String, String> generated = registrations(result);
        assertThat(generated.values(), hasSize(2));
        assertThat(generated.values().stream()
                           .flatMap(source -> source.lines())
                           .filter(line -> line.contains("manager.") && line.contains("register"))
                           .map(String::strip)
                           .toList(),
                   containsInAnyOrder("manager.register(EVENT_OBJECT, eventObserver::observe, QUALIFIERS);",
                                      "manager.registerAsync(EVENT_OBJECT, eventObserver::observeAsync, QUALIFIERS);"));
    }

    @Test
    void testVarargsObserverRejected(@TempDir Path directory) {
        var synchronous = compiler(directory.resolve("synchronous"), listener("Listener", List.of(
                "@Event.Observer void observe(String... events) {}",
                "void observe(Object event) {}")))
                .build()
                .compile();
        var asynchronous = compiler(directory.resolve("asynchronous"), listener("Listener", List.of(
                "@Event.AsyncObserver void observe(String... events) {}")))
                .build()
                .compile();

        assertThat(synchronous.success(), is(false));
        assertThat(String.join("\n", synchronous.diagnostics()),
                   containsString("Event observer methods cannot declare a varargs parameter"));
        assertThat(asynchronous.success(), is(false));
        assertThat(String.join("\n", asynchronous.diagnostics()),
                   containsString("Event observer methods cannot declare a varargs parameter"));
    }

    @Test
    void testArrayObserverAccepted(@TempDir Path directory) {
        var result = compiler(directory.resolve("array"), listener("Listener", List.of(
                "@Event.Observer void observe(String[] events) {}",
                "void observe(Object event) {}")))
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    @Test
    void testAsyncOnlyObserversRetainLegacyFilenames(@TempDir Path directory) throws IOException {
        var result = compiler(directory.resolve("compile-1"), listener("Listener", List.of(
                "@Event.AsyncObserver @Service.Named(\"red\") void first(String event) {}",
                "@Event.AsyncObserver @Service.Named(\"blue\") void second(String event) {}")))
                .build()
                .compile();

        Map<String, String> generated = registrations(result);
        assertThat("Async-only registrations must replace the previous primary registration classes",
                   generated.keySet(), containsInAnyOrder("Listener__Observer.java", "Listener__Observer_1.java"));
        assertThat(generated.get("Listener__Observer.java"),
                   containsString("manager.registerAsync(EVENT_OBJECT, eventObserver::first, QUALIFIERS);"));
        assertThat(generated.get("Listener__Observer_1.java"),
                   containsString("manager.registerAsync(EVENT_OBJECT, eventObserver::second, QUALIFIERS);"));
    }

    @Test
    void testOverloadWithUnannotatedMethod(@TempDir Path directory) throws IOException {
        var result = compiler(directory.resolve("compile-1"), listener("Listener", List.of(
                "@Event.Observer void observe(String event) {}",
                "void observe(Integer event) {}")))
                .build()
                .compile();

        assertThat(registrations(result).values(), hasSize(1));
    }

    @Test
    void testOverloadWithInheritedMethod(@TempDir Path directory) throws IOException {
        var result = compiler(directory.resolve("compile-1"), """
                package com.example;

                import io.helidon.service.registry.Event;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class Listener extends Parent {
                    @Event.Observer void observe(String event) {}
                }
                """)
                .addSource("Parent.java", """
                        package com.example;

                        class Parent {
                            void observe(Integer event) {}
                        }
                        """)
                .build()
                .compile();

        assertThat(registrations(result).values(), hasSize(1));
    }

    @Test
    void testRecompileWithLegacyRegistrations(@TempDir Path directory) throws IOException, ClassNotFoundException {
        Path retainedOutput = directory.resolve("retained-output");
        var legacy = compiler(retainedOutput, countingListener(false))
                .addSource("Listener__Observer.java", legacyRegistration("Listener__Observer", "first", "red"))
                .addSource("Listener__Observer_1.java", legacyRegistration("Listener__Observer_1", "second", "blue"))
                .build()
                .compile();
        assertThat(String.join("\n", legacy.diagnostics()), legacy.success(), is(true));

        // Keep the previous classes and service metadata, as an incremental compilation does.
        var recompiled = compiler(retainedOutput, countingListener(true))
                .build()
                .compile();
        assertThat(String.join("\n", recompiled.diagnostics()), recompiled.success(), is(true));

        var clean = compiler(directory.resolve("clean-output"), countingListener(true))
                .build()
                .compile();
        assertThat(String.join("\n", clean.diagnostics()), clean.success(), is(true));
        assertSingleDelivery(clean.classOutput());
        assertSingleDelivery(recompiled.classOutput());
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

    private static String countingListener(boolean annotated) {
        return """
                package com.example;

                import java.util.concurrent.atomic.AtomicIntegerArray;
                import java.util.function.IntUnaryOperator;

                import io.helidon.service.registry.Event;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                public class Listener implements IntUnaryOperator {
                    private final AtomicIntegerArray counts = new AtomicIntegerArray(2);

                    %s
                    @Service.Named("red")
                    void first(String event) {
                        counts.incrementAndGet(0);
                    }

                    %s
                    @Service.Named("blue")
                    void second(String event) {
                        counts.incrementAndGet(1);
                    }

                    @Override
                    public int applyAsInt(int index) {
                        return counts.get(index);
                    }
                }
                """.formatted(annotated ? "@Event.Observer" : "", annotated ? "@Event.Observer" : "");
    }

    private static String legacyRegistration(String name, String method, String qualifier) {
        return """
                package com.example;

                import java.util.Set;

                import io.helidon.common.types.ResolvedType;
                import io.helidon.service.registry.EventManager;
                import io.helidon.service.registry.GeneratedService;
                import io.helidon.service.registry.Qualifier;
                import io.helidon.service.registry.Service;

                @Service.Singleton
                class %s implements GeneratedService.EventObserverRegistration {
                    private final Listener listener;

                    @Service.Inject
                    %s(Listener listener) {
                        this.listener = listener;
                    }

                    @Override
                    public void register(EventManager manager) {
                        manager.register(ResolvedType.create(String.class), listener::%s,
                                         Set.of(Qualifier.createNamed("%s")));
                    }
                }
                """.formatted(name, name, method, qualifier);
    }

    private static void assertSingleDelivery(Path classes) throws IOException, ClassNotFoundException {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try (var loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, previous)) {
            thread.setContextClassLoader(loader);
            ServiceRegistryManager manager = ServiceRegistryManager.create();
            try {
                var registry = manager.registry();
                IntUnaryOperator listener = (IntUnaryOperator) registry.get(loader.loadClass("com.example.Listener"));
                EventManager events = registry.get(EventManager.class);
                events.emit(ResolvedType.create(String.class), "red-event", Set.of(Qualifier.createNamed("red")));
                events.emit(ResolvedType.create(String.class), "blue-event", Set.of(Qualifier.createNamed("blue")));
                assertThat("Red event deliveries from " + classes, listener.applyAsInt(0), is(1));
                assertThat("Blue event deliveries from " + classes, listener.applyAsInt(1), is(1));
            } finally {
                manager.shutdown();
            }
        } finally {
            thread.setContextClassLoader(previous);
        }
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
