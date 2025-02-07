/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
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
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;

import static io.helidon.microprofile.testing.ReflectionHelper.annotationHierarchy;
import static io.helidon.microprofile.testing.ReflectionHelper.invoke;
import static io.helidon.microprofile.testing.ReflectionHelper.isOverride;
import static io.helidon.microprofile.testing.ReflectionHelper.requireStatic;
import static io.helidon.microprofile.testing.ReflectionHelper.typeHierarchy;
import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * Helidon test CDI extension.
 */
public abstract class HelidonTestExtension implements Extension {

    private static final Map<Class<? extends Annotation>, Annotation> ANNOTATION_LITERALS = Map.of(
            ApplicationScoped.class, ApplicationScoped.Literal.INSTANCE,
            Singleton.class, ApplicationScoped.Literal.INSTANCE,
            RequestScoped.class, RequestScoped.Literal.INSTANCE,
            Dependent.class, Dependent.Literal.INSTANCE);

    private static final Set<Class<? extends Annotation>> TYPE_ANNOTATION_TYPES = Set.of(
            AddConfig.class,
            AddConfigs.class,
            AddConfigBlock.class,
            Configuration.class);

    private static final Set<Class<? extends Annotation>> PARAMETER_ANNOTATION_TYPES = Set.of(
            Socket.class);

    private static final Set<Class<? extends Annotation>> FIELD_ANNOTATION_TYPES = Set.of(
            Socket.class);

    private static final Set<Class<? extends Annotation>> METHOD_ANNOTATION_TYPES = Set.of(
            AddConfig.class,
            AddConfigs.class,
            AddConfigBlock.class,
            AddConfigSource.class,
            AfterStop.class,
            Configuration.class);

    private final HelidonTestInfo<?> testInfo;
    private final HelidonTestConfig testConfig;
    private final HelidonTestScope testScope;
    private final Map<Annotation, String> sockets = new HashMap<>();
    private final List<Method> afterStop = new ArrayList<>();

    /**
     * Create a new instance.
     *
     * @param testInfo  test info
     * @param testScope test scope
     */
    protected HelidonTestExtension(HelidonTestInfo<?> testInfo, HelidonTestScope testScope) {
        this.testInfo = testInfo;
        this.testConfig = new HelidonTestConfig(testInfo);
        this.testScope = testScope;
    }

    /**
     * Get the annotation types usable on type.
     *
     * @return annotations
     */
    protected Set<Class<? extends Annotation>> typeAnnotationTypes() {
        return TYPE_ANNOTATION_TYPES;
    }

    /**
     * Get the annotation types usable on parameters.
     *
     * @return annotation types
     */
    protected Set<Class<? extends Annotation>> parameterAnnotationTypes() {
        return PARAMETER_ANNOTATION_TYPES;
    }

    /**
     * Get the annotation types usable on fields.
     *
     * @return annotation types
     */
    protected Set<Class<? extends Annotation>> fieldAnnotationTypes() {
        return FIELD_ANNOTATION_TYPES;
    }

    /**
     * Get the annotation types usable on methods.
     *
     * @return annotation types
     */
    protected Set<Class<? extends Annotation>> methodAnnotationTypes() {
        return METHOD_ANNOTATION_TYPES;
    }

    /**
     * Process a type annotation.
     *
     * @param annotation annotation
     */
    protected void processTypeAnnotation(Annotation annotation) {
        switch (annotation) {
            case Configuration e -> processConfiguration(e);
            case AddConfig e -> processAddConfig(e);
            case AddConfigs e -> processAddConfig(e.value());
            case AddConfigBlock e -> processAddConfigBlock(e);
            case AddConfigBlocks e -> processAddConfigBlock(e.value());
            default -> {
                // no-op
            }
        }
    }

    /**
     * Process a parameter annotation.
     *
     * @param annotation annotation
     */
    protected void processParameterAnnotation(Annotation annotation) {
        if (annotation instanceof Socket s) {
            processSocket(s, s.value());
        }
    }

    /**
     * Process a field annotation.
     *
     * @param annotation annotation
     */
    protected void processFieldAnnotation(Annotation annotation) {
        if (annotation instanceof Socket s) {
            processSocket(s, s.value());
        }
    }

    /**
     * Process a static method annotation.
     *
     * @param annotation annotation
     * @param method     method
     */
    protected void processStaticMethodAnnotation(Annotation annotation, Method method) {
        if (annotation instanceof AddConfigSource) {
            processAddConfigSource(method);
        } else if (annotation instanceof AfterStop) {
            processAfterStop(method);
        } else {
            throw new IllegalStateException(String.format(
                    "@%s requires method %s to be non static",
                    method, annotation.annotationType().getSimpleName()));
        }
    }

    /**
     * Process a test method annotation.
     *
     * @param annotation annotation
     * @param method     method
     */
    protected void processTestMethodAnnotation(Annotation annotation, Method method) {
        switch (annotation) {
            case Configuration e -> processConfiguration(e);
            case AddConfig e -> processAddConfig(e);
            case AddConfigs e -> processAddConfig(e.value());
            case AddConfigBlock e -> processAddConfigBlock(e);
            case AddConfigBlocks e -> processAddConfigBlock(e.value());
            default -> throw new IllegalStateException(String.format(
                    "@%s requires method %s to be static",
                    method, annotation.annotationType().getSimpleName()));
        }
    }

    /**
     * Process a {@link Configuration} annotation.
     *
     * @param annotation annotation
     */
    protected final void processConfiguration(Configuration annotation) {
        testConfig.synthetic().update(annotation);
    }

    /**
     * Process {@link AddConfig Configuration} annotations.
     *
     * @param annotations annotations
     */
    protected final void processAddConfig(AddConfig... annotations) {
        testConfig.synthetic().update(annotations);
    }

    /**
     * Process {@link AddConfig Configuration} annotations.
     *
     * @param annotations annotations
     */
    protected final void processAddConfigBlock(AddConfigBlock... annotations) {
        testConfig.synthetic().update(annotations);
    }

    /**
     * Process a {@link AddConfigSource} method.
     *
     * @param method method
     */
    protected final void processAddConfigSource(Method method) {
        testConfig.synthetic().update(method);
    }

    /**
     * Process a {@link AfterStop} method.
     *
     * @param method method
     */
    protected final void processAfterStop(Method method) {
        afterStop.add(requireStatic(method));
    }

    /**
     * Process a {@link Socket} annotation.
     *
     * @param annotation annotation
     * @param value      value
     */
    protected final void processSocket(Annotation annotation, String value) {
        sockets.put(annotation, value);
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

        Method testMethod = testInfo.testMethod().orElse(null);
        for (AnnotatedType<?> type : allTypes) {

            // type
            processAnnotated(type, typeAnnotationTypes(), this::processTypeAnnotation);

            // constructor parameters
            for (AnnotatedConstructor<?> constructor : type.getConstructors()) {
                for (AnnotatedParameter<?> parameter : constructor.getParameters()) {
                    processAnnotated(parameter, fieldAnnotationTypes(), this::processParameterAnnotation);
                }
            }

            // fields
            for (AnnotatedField<?> field : type.getFields()) {
                processAnnotated(field, parameterAnnotationTypes(), this::processFieldAnnotation);
            }

            // methods
            for (AnnotatedMethod<?> method : type.getMethods()) {
                processAnnotated(method, methodAnnotationTypes(), a -> {
                    if (method.isStatic()) {
                        processStaticMethodAnnotation(a, method.getJavaMember());
                    } else {
                        // test method or super test method
                        if (testMethod != null && isOverride(method.getJavaMember(), testMethod)) {
                            processTestMethodAnnotation(a, method.getJavaMember());
                        }
                    }
                });
            }
        }
    }

    private void beforeBeanDiscovery(@Observes BeforeBeanDiscovery event, BeanManager bm) {
        // remove bootstrap config
        testConfig.resolve();

        // add the test class
        event.addAnnotatedType(testInfo.testClass(), "HelidonTest")
                .add(HelidonTestScoped.Literal.INSTANCE);

        for (AddBean addBean : testInfo.addBeans()) {
            Class<?> beanClass = addBean.value();
            Class<? extends Annotation> scopeClass = addBean.scope();
            AnnotatedTypeConfigurator<?> configurator = event.addAnnotatedType(beanClass, beanClass.getName());

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
                AnnotatedType<?> annotated = configurator.getAnnotated();
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
            sockets.forEach((annotation, value) -> {

                Supplier<String> supplier = () -> "http://localhost:" + port(server, value);

                event.addBean()
                        .addTransitiveTypeClosure(WebTarget.class)
                        .scope(ApplicationScoped.class)
                        .qualifiers(annotation)
                        .createWith(c -> client.target(supplier.get()));

                // unproxiable types, use dependent backed by lazy values

                LazyValue<URI> uri = LazyValue.create(() -> URI.create(supplier.get()));
                event.addBean()
                        .addType(URI.class)
                        .scope(Dependent.class)
                        .qualifiers(annotation)
                        .createWith(c -> uri.get());

                LazyValue<String> rawUri = LazyValue.create(supplier);
                event.addBean()
                        .addType(String.class)
                        .scope(Dependent.class)
                        .qualifiers(annotation)
                        .createWith(c -> rawUri.get());
            });
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
