/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing.junit5;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.microprofile.testing.junit5.HelidonTestInfo.MethodInfo;

import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterTypeDiscovery;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedConstructor;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Singleton;
import jakarta.interceptor.Interceptor;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;

import static io.helidon.microprofile.testing.junit5.ReflectionHelper.annotationHierarchy;
import static io.helidon.microprofile.testing.junit5.ReflectionHelper.invoke;
import static io.helidon.microprofile.testing.junit5.ReflectionHelper.isOverride;
import static io.helidon.microprofile.testing.junit5.ReflectionHelper.requireStatic;
import static io.helidon.microprofile.testing.junit5.ReflectionHelper.typeHierarchy;
import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * Helidon test CDI extension.
 */
@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
final class HelidonTestExtension implements Extension {

    private static final Map<Class<? extends Annotation>, Annotation> ANNOTATION_LITERALS = Map.of(
            ApplicationScoped.class, ApplicationScoped.Literal.INSTANCE,
            Singleton.class, ApplicationScoped.Literal.INSTANCE,
            RequestScoped.class, RequestScoped.Literal.INSTANCE,
            Dependent.class, Dependent.Literal.INSTANCE);

    private static final Set<Class<? extends Annotation>> AFTER_TYPE_ANNOTATIONS = Set.of(
            Alternative.class,
            Interceptor.class,
            Decorator.class);

    private static final Set<Class<? extends Annotation>> TYPE_ANNOTATIONS = Set.of(
            AddBean.class,
            AddBeans.class,
            AddConfig.class,
            AddConfigs.class,
            AddConfigBlock.class,
            Configuration.class);

    private static final Set<Class<? extends Annotation>> CTOR_PARAM_ANNOTATIONS = Set.of(
            Socket.class);

    private static final Set<Class<? extends Annotation>> FIELD_ANNOTATIONS = Set.of(
            Socket.class);

    private static final Set<Class<? extends Annotation>> METHOD_ANNOTATIONS = Set.of(
            AddBean.class,
            AddBeans.class,
            AddConfig.class,
            AddConfigs.class,
            AddConfigBlock.class,
            AddConfigSource.class,
            AfterStop.class,
            Configuration.class);

    private final HelidonTestInfo testInfo;
    private final HelidonTestConfig testConfig;
    private final HelidonTestScope testScope;
    private final Set<AddBean> addBeans = new HashSet<>();
    private final Set<Socket> sockets = new HashSet<>();
    private final List<Method> afterStop = new ArrayList<>();

    HelidonTestExtension(HelidonTestInfo testInfo, HelidonTestConfig testConfig, HelidonTestScope testScope) {
        this.testInfo = testInfo;
        this.testConfig = testConfig;
        this.testScope = testScope;
    }

    private void processTestClass(@Observes
                                  @WithAnnotations(HelidonTestScoped.class)
                                  ProcessAnnotatedType<?> pat,
                                  BeanManager bm) {

        Set<AnnotatedType<?>> allTypes = new HashSet<>();
        allTypes.add(pat.getAnnotatedType());

        // create annotated types for the type hierarchy
        for (Class<?> type : typeHierarchy(pat.getAnnotatedType().getJavaClass(), false)) {
            allTypes.add(bm.createAnnotatedType(type));
        }

        Method testMethod = testInfo instanceof MethodInfo m ? m.element() : null;
        for (AnnotatedType<?> type : allTypes) {

            // type
            processAnnotated(type, TYPE_ANNOTATIONS, a -> {
                switch (a) {
                    case Configuration e -> testConfig.synthetic().update(e);
                    case AddConfig e -> testConfig.synthetic().update(e);
                    case AddConfigs e -> testConfig.synthetic().update(e.value());
                    case AddConfigBlock e -> testConfig.synthetic().update(e);
                    case AddConfigBlocks e -> testConfig.synthetic().update(e.value());
                    case AddBeans e -> addBeans.addAll(List.of(e.value()));
                    case AddBean e -> addBeans.add(e);
                    default -> {
                        // no-op
                    }
                }
            });

            // constructor parameters
            for (AnnotatedConstructor<?> constructor : type.getConstructors()) {
                for (AnnotatedParameter<?> parameter : constructor.getParameters()) {
                    processAnnotated(parameter, FIELD_ANNOTATIONS, a -> {
                        if (a instanceof Socket s) {
                            sockets.add(s);
                        }
                    });
                }
            }

            // fields
            for (AnnotatedField<?> field : type.getFields()) {
                processAnnotated(field, CTOR_PARAM_ANNOTATIONS, a -> {
                    if (a instanceof Socket s) {
                        sockets.add(s);
                    }
                });
            }

            // methods
            for (AnnotatedMethod<?> method : type.getMethods()) {
                processAnnotated(method, METHOD_ANNOTATIONS, a -> {
                    if (method.isStatic()) {
                        switch (a) {
                            case AddConfigSource ignored -> testConfig.synthetic().update(method.getJavaMember());
                            case AfterStop ignored -> afterStop.add(requireStatic(method.getJavaMember()));
                            default -> throw new IllegalStateException(String.format(
                                    "@%s requires method %s to be non static",
                                    method, a.annotationType().getSimpleName()));
                        }
                    } else {
                        // test method or super test method
                        if (testMethod != null && isOverride(method.getJavaMember(), testMethod)) {
                            switch (a) {
                                case Configuration e -> testConfig.synthetic().update(e);
                                case AddConfig e -> testConfig.synthetic().update(e);
                                case AddConfigs e -> testConfig.synthetic().update(e.value());
                                case AddConfigBlock e -> testConfig.synthetic().update(e);
                                case AddConfigBlocks e -> testConfig.synthetic().update(e.value());
                                case AddBeans e -> addBeans.addAll(List.of(e.value()));
                                case AddBean e -> addBeans.add(e);
                                default -> throw new IllegalStateException(String.format(
                                        "@%s requires method %s to be static",
                                        method, a.annotationType().getSimpleName()));
                            }
                        }
                    }
                });
            }
        }
    }

    private void beforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        // remove bootstrap config
        testConfig.resolve();

        // add the test class
        event.addAnnotatedType(testInfo.classInfo().element(), "HelidonTest")
                .add(HelidonTestScoped.Literal.INSTANCE);
    }

    private void afterTypeDiscovery(@Observes AfterTypeDiscovery event, BeanManager bm) {
        for (AddBean addBean : addBeans) {
            Class<?> beanClass = addBean.value();
            Class<? extends Annotation> scopeClass = addBean.scope();
            AnnotatedTypeConfigurator<?> configurator = event.addAnnotatedType(beanClass, beanClass.getName());
            AnnotatedType<?> annotated = configurator.getAnnotated();

            // AfterTypeDiscovery workaround
            // Manually add alternatives, interceptors, decorators
            processAnnotated(annotated, AFTER_TYPE_ANNOTATIONS, a -> {
                switch (a) {
                    case Alternative ignore -> event.getAlternatives().add(beanClass);
                    case Interceptor ignore -> event.getInterceptors().add(beanClass);
                    case Decorator ignore -> event.getDecorators().add(beanClass);
                    default -> {
                        // no-op
                    }
                }
            });

            // process scope
            if (!scopeClass.equals(Annotation.class)) {
                // scope explicitly configured
                Annotation scope = ANNOTATION_LITERALS.get(scopeClass);
                if (scope == null) {
                    scope = new AnnotationLiteral<>() {
                        @Override
                        public Class<? extends Annotation> annotationType() {
                            return scopeClass;
                        }
                    };
                }
                // remove existing scope annotations
                configurator.remove(a -> bm.isScope(a.annotationType()));
                configurator.add(scope);
            } else {
                // no scope configured
                if (annotated.getAnnotations().stream()
                        .noneMatch(a -> bm.isScope(a.annotationType()))) {

                    // bean class does not have a scope annotation
                    // default to ApplicationScoped
                    configurator.add(ApplicationScoped.Literal.INSTANCE);
                }
            }
        }
    }

    private void afterBeanDiscovery(@Observes
                                    @Priority(PLATFORM_BEFORE)
                                    AfterBeanDiscovery event,
                                    BeanManager bm) {

        // useExisting may have changed, re-resolve
        testConfig.resolve();

        // test scope support
        event.addContext(testScope);

        Class<? extends Extension> serverClass = serverClass();
        if (serverClass != null && (!testInfo.disableDiscovery() || testInfo.containsExtension(serverClass))) {

            Extension server = bm.getExtension(serverClass);
            Client client = ClientBuilder.newClient();

            // default port
            event.addBean()
                    .addTransitiveTypeClosure(WebTarget.class)
                    .scope(ApplicationScoped.class)
                    .createWith(c -> client.target("http://localhost:" + port(server, "@default")));

            // named ports
            for (Socket socket : sockets) {

                Supplier<String> supplier = () -> "http://localhost:" + port(server, socket.value());

                event.addBean()
                        .addTransitiveTypeClosure(WebTarget.class)
                        .scope(ApplicationScoped.class)
                        .qualifiers(socket)
                        .createWith(c -> client.target(supplier.get()));

                // unproxiable types, use dependent backed by lazy values

                LazyValue<URI> uri = LazyValue.create(() -> URI.create(supplier.get()));
                event.addBean()
                        .addType(URI.class)
                        .scope(Dependent.class)
                        .qualifiers(socket)
                        .createWith(c -> uri.get());

                LazyValue<String> rawUri = LazyValue.create(supplier);
                event.addBean()
                        .addType(String.class)
                        .scope(Dependent.class)
                        .qualifiers(socket)
                        .createWith(c -> rawUri.get());
            }
        }
    }

    private void afterStop(@Observes
                           @Priority(PLATFORM_AFTER)
                           @Destroyed(ApplicationScoped.class)
                           Object ignored) {

        afterStop.forEach(m -> invoke(Void.class, m, null));
    }

    private void processAnnotated(Annotated elt, Set<Class<? extends Annotation>> types, Consumer<Annotation> action) {
        for (Annotation a : elt.getAnnotations()) {
            if (types.contains(a.annotationType())) {
                action.accept(a);
            } else {
                // meta-annotations
                for (Annotation b : annotationHierarchy(a.annotationType())) {
                    if (types.contains(b.annotationType())) {
                        action.accept(b);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Extension> serverClass() {
        try {
            return (Class<? extends Extension>) Class.forName("io.helidon.microprofile.server.ServerCdiExtension");
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static int port(Extension server, String name) {
        try {
            Method portMethod = server.getClass().getMethod("port", String.class);
            return invoke(Integer.class, portMethod, server, name);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
