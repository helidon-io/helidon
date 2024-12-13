/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.Serial;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.testing.virtualthreads.PinningRecorder;
import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;


/**
 * Junit5 extension to support Helidon CDI container in tests.
 */
class HelidonJunitExtension implements BeforeAllCallback,
                                       AfterAllCallback,
                                       BeforeEachCallback,
                                       AfterEachCallback,
                                       InvocationInterceptor,
                                       ParameterResolver {
    private static final Set<Class<? extends Annotation>> HELIDON_TEST_ANNOTATIONS =
            Set.of(AddBean.class, AddConfig.class, AddExtension.class, Configuration.class,
                    AddJaxRs.class, AddConfigBlock.class);
    private static final Map<Class<? extends Annotation>, Annotation> BEAN_DEFINING = new HashMap<>();

    static {
        BEAN_DEFINING.put(ApplicationScoped.class, ApplicationScoped.Literal.INSTANCE);
        BEAN_DEFINING.put(Singleton.class, ApplicationScoped.Literal.INSTANCE);
        BEAN_DEFINING.put(RequestScoped.class, RequestScoped.Literal.INSTANCE);
        BEAN_DEFINING.put(Dependent.class, Dependent.Literal.INSTANCE);
    }

    private final List<AddExtension> classLevelExtensions = new ArrayList<>();
    private final List<AddBean> classLevelBeans = new ArrayList<>();
    private final ConfigMeta classLevelConfigMeta = new ConfigMeta();
    private boolean classLevelDisableDiscovery = false;
    private boolean resetPerTest;

    private Class<?> testClass;
    private ConfigProviderResolver configProviderResolver;
    private Config config;
    private SeContainer container;
    private PinningRecorder pinningRecorder;

    @SuppressWarnings("unchecked")
    @Override
    public void beforeAll(ExtensionContext context) {
        testClass = context.getRequiredTestClass();

        List<Annotation> metaAnnotations = extractMetaAnnotations(testClass);

        AddConfig[] configs = getAnnotations(testClass, AddConfig.class, metaAnnotations);
        classLevelConfigMeta.addConfig(configs);
        classLevelConfigMeta.configuration(getAnnotation(testClass, Configuration.class, metaAnnotations));
        classLevelConfigMeta.addConfigBlock(getAnnotation(testClass, AddConfigBlock.class, metaAnnotations));
        configProviderResolver = ConfigProviderResolver.instance();

        AddExtension[] extensions = getAnnotations(testClass, AddExtension.class, metaAnnotations);
        classLevelExtensions.addAll(Arrays.asList(extensions));

        AddBean[] beans = getAnnotations(testClass, AddBean.class, metaAnnotations);
        classLevelBeans.addAll(Arrays.asList(beans));

        HelidonTest testAnnot = testClass.getAnnotation(HelidonTest.class);
        if (testAnnot != null) {
            resetPerTest = testAnnot.resetPerTest();
            if (testAnnot.pinningDetection()) {
                pinningRecorder = PinningRecorder.create();
                pinningRecorder.record(Duration.ofMillis(testAnnot.pinningThreshold()));
            }
        }


        DisableDiscovery discovery = getAnnotation(testClass, DisableDiscovery.class, metaAnnotations);
        if (discovery != null) {
            classLevelDisableDiscovery = discovery.value();
        }

        if (resetPerTest) {
            validatePerTest();

            return;
        }
        validatePerClass();

        // add beans when using JaxRS
        AddJaxRs addJaxRsAnnotation = getAnnotation(testClass, AddJaxRs.class, metaAnnotations);
        if (addJaxRsAnnotation != null){
            classLevelExtensions.add(ProcessAllAnnotatedTypesLiteral.INSTANCE);
            classLevelExtensions.add(ServerCdiExtensionLiteral.INSTANCE);
            classLevelExtensions.add(JaxRsCdiExtensionLiteral.INSTANCE);
            classLevelExtensions.add(CdiComponentProviderLiteral.INSTANCE);
            classLevelBeans.add(WeldRequestScopeLiteral.INSTANCE);
        }

        configure(classLevelConfigMeta);

        if (!classLevelConfigMeta.useExisting) {
            // the container startup is delayed in case we `useExisting`, so the is first set up by the user
            // when we do not need to `useExisting`, we want to start early, so parameterized test method sources that use CDI
            // can work
            startContainer(classLevelBeans, classLevelExtensions, classLevelDisableDiscovery);
        }
    }

    private List<Annotation> extractMetaAnnotations(Class<?> testClass) {
        Annotation[] testAnnotations = testClass.getAnnotations();
        for (Annotation testAnnotation : testAnnotations) {
            List<Annotation> annotations = List.of(testAnnotation.annotationType().getAnnotations());
            List<Class<?>> annotationsClass = annotations.stream()
                    .map(Annotation::annotationType).collect(Collectors.toList());
            if (!Collections.disjoint(HELIDON_TEST_ANNOTATIONS, annotationsClass)) {
                // Contains at least one of HELIDON_TEST_ANNOTATIONS
                return annotations;
            }
        }
        return List.of();
    }

    private <T extends Annotation> T getAnnotation(Class<?> testClass, Class<T> annotClass,
            List<Annotation> metaAnnotations) {
        T annotation = testClass.getAnnotation(annotClass);
        if (annotation == null) {
            List<T> byType = annotationsByType(annotClass, metaAnnotations);
            if (!byType.isEmpty()) {
                annotation = byType.get(0);
            }
        }
        return annotation;
    }

    @SuppressWarnings("unchecked")
    private <T extends Annotation> T[] getAnnotations(Class<?> testClass, Class<T> annotClass,
            List<Annotation> metaAnnotations) {
        // inherited does not help, as it only returns annot from superclass if
        // child has none
        T[] directAnnotations = testClass.getAnnotationsByType(annotClass);

        List<T> allAnnotations = new ArrayList<>(List.of(directAnnotations));
        // Include meta annotations
        allAnnotations.addAll(annotationsByType(annotClass, metaAnnotations));

        Class<?> superClass = testClass.getSuperclass();
        while (superClass != null) {
            directAnnotations = superClass.getAnnotationsByType(annotClass);
            allAnnotations.addAll(List.of(directAnnotations));
            superClass = superClass.getSuperclass();
        }

        Object result = Array.newInstance(annotClass, allAnnotations.size());
        for (int i = 0; i < allAnnotations.size(); i++) {
             Array.set(result, i, allAnnotations.get(i));
        }

        return (T[]) result;
    }

    private <T extends Annotation> List<T> annotationsByType(Class<T> annotClass, List<Annotation> metaAnnotations) {
        List<T> byType = new ArrayList<>();
        for (Annotation annotation : metaAnnotations) {
            if (annotation.annotationType() == annotClass) {
                byType.add((T) annotation);
            }
        }
        return byType;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (resetPerTest) {
            Method method = context.getRequiredTestMethod();
            AddConfig[] configs = method.getAnnotationsByType(AddConfig.class);
            ConfigMeta methodLevelConfigMeta = classLevelConfigMeta.nextMethod();
            methodLevelConfigMeta.addConfig(configs);
            methodLevelConfigMeta.configuration(method.getAnnotation(Configuration.class));
            methodLevelConfigMeta.addConfigBlock(method.getAnnotation(AddConfigBlock.class));

            configure(methodLevelConfigMeta);

            List<AddExtension> methodLevelExtensions = new ArrayList<>(classLevelExtensions);
            List<AddBean> methodLevelBeans = new ArrayList<>(classLevelBeans);
            boolean methodLevelDisableDiscovery = classLevelDisableDiscovery;

            AddExtension[] extensions = method.getAnnotationsByType(AddExtension.class);
            methodLevelExtensions.addAll(Arrays.asList(extensions));

            AddBean[] beans = method.getAnnotationsByType(AddBean.class);
            methodLevelBeans.addAll(Arrays.asList(beans));

            DisableDiscovery discovery = method.getAnnotation(DisableDiscovery.class);
            if (discovery != null) {
                methodLevelDisableDiscovery = discovery.value();
            }

            startContainer(methodLevelBeans, methodLevelExtensions, methodLevelDisableDiscovery);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (resetPerTest) {
            releaseConfig();
            stopContainer();
        }
//        pinningRecorder.checkAndThrow();
    }

    private void validatePerClass() {
        Method[] methods = testClass.getMethods();
        for (Method method : methods) {
            if (method.getAnnotation(Test.class) != null) {
                // a test method
                if (hasHelidonTestAnnotation(method)) {
                    throw new RuntimeException("When a class is annotated with @HelidonTest, "
                                                       + "there is a single CDI container used to invoke all "
                                                       + "test methods on the class. Method " + method
                                                       + " has an annotation that modifies container behavior.");
                }
            }
        }

        methods = testClass.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getAnnotation(Test.class) != null) {
                // a test method
                if (hasHelidonTestAnnotation(method)) {
                    throw new RuntimeException("When a class is annotated with @HelidonTest, "
                                                       + "there is a single CDI container used to invoke all "
                                                       + "test methods on the class. Method " + method
                                                       + " has an annotation that modifies container behavior.");
                }
            }
        }

        AddJaxRs addJaxRsAnnotation = testClass.getAnnotation(AddJaxRs.class);
        if (addJaxRsAnnotation != null){
            if (testClass.getAnnotation(DisableDiscovery.class) == null){
                throw new RuntimeException("@AddJaxRs annotation should be used only with @DisableDiscovery annotation.");
            }
        }
    }

    private boolean hasHelidonTestAnnotation(AnnotatedElement element) {
        for (Class<? extends Annotation> aClass : HELIDON_TEST_ANNOTATIONS) {
            if (element.getAnnotation(aClass) != null) {
                return true;
            }
        }
        return false;
    }

    private void validatePerTest() {
        Constructor<?>[] constructors = testClass.getConstructors();
        if (constructors.length > 1) {
            throw new RuntimeException("When a class is annotated with @HelidonTest(resetPerTest=true),"
                                               + " the class must have only a single no-arg constructor");
        }
        if (constructors.length == 1) {
            Constructor<?> c = constructors[0];
            if (c.getParameterCount() > 0) {
                throw new RuntimeException("When a class is annotated with @HelidonTest(resetPerTest=true),"
                                                   + " the class must have a no-arg constructor");
            }
        }

        Field[] fields = testClass.getFields();
        for (Field field : fields) {
            if (field.getAnnotation(Inject.class) != null) {
                throw new RuntimeException("When a class is annotated with @HelidonTest(resetPerTest=true),"
                                                   + " injection into fields or constructor is not supported, as each"
                                                   + " test method uses a different CDI container. Field " + field
                                                   + " is annotated with @Inject");
            }
        }

        fields = testClass.getDeclaredFields();
        for (Field field : fields) {
            if (field.getAnnotation(Inject.class) != null) {
                throw new RuntimeException("When a class is annotated with @HelidonTest(resetPerTest=true),"
                                                   + " injection into fields or constructor is not supported, as each"
                                                   + " test method uses a different CDI container. Field " + field
                                                   + " is annotated with @Inject");
            }
        }
    }

    private void configure(ConfigMeta configMeta) {
        if (config != null) {
            configProviderResolver.releaseConfig(config);
        }
        if (!configMeta.useExisting) {
            // only create a custom configuration if not provided by test method/class
            // prepare configuration
            ConfigBuilder builder = configProviderResolver.getBuilder();

            configMeta.additionalSources.forEach(it -> {
                String fileName = it.trim();
                int idx = fileName.lastIndexOf('.');
                String type = idx > -1 ? fileName.substring(idx + 1) : "properties";
                try {
                    Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources(fileName);
                    urls.asIterator().forEachRemaining(url -> builder.withSources(MpConfigSources.create(type, url)));
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to read \"" + fileName + "\" from classpath", e);
                }
            });
            if (configMeta.type != null && configMeta.block != null) {
                builder.withSources(MpConfigSources.create(configMeta.type, new StringReader(configMeta.block)));
            }
            config = builder
                    .withSources(MpConfigSources.create(configMeta.additionalKeys))
                    .addDefaultSources()
                    .addDiscoveredSources()
                    .addDiscoveredConverters()
                    .build();
            configProviderResolver.registerConfig(config, Thread.currentThread().getContextClassLoader());
        }
    }
    private void releaseConfig() {
        if (configProviderResolver != null && config != null) {
            configProviderResolver.releaseConfig(config);
            config = null;
        }
    }

    @SuppressWarnings("unchecked")
    private void startContainer(List<AddBean> beanAnnotations,
                                List<AddExtension> extensionAnnotations,
                                boolean disableDiscovery) {

        // now let's prepare the CDI bootstrapping
        SeContainerInitializer initializer = SeContainerInitializer.newInstance();

        if (disableDiscovery) {
            initializer.disableDiscovery();
        }

        initializer.addExtensions(new AddBeansExtension(testClass, beanAnnotations));

        for (AddExtension addExtension : extensionAnnotations) {
            Class<? extends Extension> extensionClass = addExtension.value();
            if (Modifier.isPublic(extensionClass.getModifiers())) {
                initializer.addExtensions(addExtension.value());
            } else {
                throw new IllegalArgumentException("Extension classes must be public, but " + extensionClass
                        .getName() + " is not");
            }
        }

        container = initializer.initialize();
    }

    private void stopContainer() {
        if (container != null) {
            container.close();
            container = null;
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        stopContainer();
        releaseConfig();
        callAfterStop();
        if (pinningRecorder != null) {
            pinningRecorder.close();
            pinningRecorder = null;
        }
    }

    @Override
    public <T> T interceptTestClassConstructor(Invocation<T> invocation,
                                               ReflectiveInvocationContext<Constructor<T>> invocationContext,
                                               ExtensionContext extensionContext) throws Throwable {

        if (resetPerTest) {
            // Junit creates test instance
            return invocation.proceed();
        }

        // we need to start container before the test class is instantiated, to honor @BeforeAll that
        // creates a custom MP config
        if (container == null) {
            // at this early stage the class should be checked whether it is annotated with
            // @TestInstance(TestInstance.Lifecycle.PER_CLASS) to start correctly the container
            TestInstance testClassAnnotation = testClass.getAnnotation(TestInstance.class);
            if (testClassAnnotation != null && testClassAnnotation.value().equals(TestInstance.Lifecycle.PER_CLASS)){
                throw new RuntimeException("When a class is annotated with @HelidonTest, "
                        + "it is not compatible with @TestInstance(TestInstance.Lifecycle.PER_CLASS)"
                        + "annotation, as it is a Singleton CDI Bean.");
            }
            startContainer(classLevelBeans, classLevelExtensions, classLevelDisableDiscovery);
        }

        // we need to replace instantiation with CDI lookup, to properly injection into fields (and constructors)
        invocation.skip();

        return container.select(invocationContext.getExecutable().getDeclaringClass())
                .get();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {

        Executable executable = parameterContext.getParameter().getDeclaringExecutable();

        if (resetPerTest) {
            if (executable instanceof Constructor) {
                throw new ParameterResolutionException(
                        "When a test class is annotated with @HelidonTest(resetPerMethod=true), constructor must not have "
                                + "parameters.");
            }
        } else {
            // we need to start container before the test class is instantiated, to honor @BeforeAll that
            // creates a custom MP config
            if (container == null) {
                startContainer(classLevelBeans, classLevelExtensions, classLevelDisableDiscovery);
            }
        }

        Class<?> paramType = parameterContext.getParameter().getType();

        if (executable instanceof Constructor) {
            return !container.select(paramType).isUnsatisfied();
        } else if (executable instanceof Method) {
            if (paramType.equals(SeContainer.class)) {
                return true;
            }
            if (paramType.equals(WebTarget.class)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Executable executable = parameterContext.getParameter().getDeclaringExecutable();
        Class<?> paramType = parameterContext.getParameter().getType();

        if (executable instanceof Method) {
            if (paramType.equals(SeContainer.class)) {
                return container;
            }
            if (paramType.equals(WebTarget.class)) {
                return container.select(WebTarget.class).get();
            }
        }
        // we return null, as construction of the object is done by CDI
        // for primitive types we must return appropriate primitive default
        if (paramType.isPrimitive()) {
            // a hack to get to default value of a primitive type
            return Array.get(Array.newInstance(paramType, 1), 0);
        } else {
            return null;
        }
    }

    private void callAfterStop() {
        List<Method> toInvoke = new ArrayList<>();

        Method[] methods = testClass.getMethods();
        for (Method method : methods) {
            AfterStop annotation = method.getAnnotation(AfterStop.class);
            if (annotation != null) {
                if (method.getParameterCount() != 0) {
                    throw new IllegalStateException("Method " + method + " is annotated with @AfterStop, but it has parameters");
                }
                if (Modifier.isStatic(method.getModifiers())) {
                    method.setAccessible(true);
                    toInvoke.add(method);
                } else {
                    throw new IllegalStateException("Method " + method + " is annotated with @AfterStop, but it is not static");
                }
            }
        }

        for (Method method : toInvoke) {
            try {
                method.invoke(testClass);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to invoke method: " + method, e);
            }
        }
    }

    // this is not registered as a bean - we manually register an instance
    @SuppressWarnings("CdiManagedBeanInconsistencyInspection")
    private static class AddBeansExtension implements Extension {
        private final Class<?> testClass;
        private final List<AddBean> addBeans;

        private final HashMap<String, Annotation> socketAnnotations = new HashMap<>();

        private AddBeansExtension(Class<?> testClass, List<AddBean> addBeans) {
            this.testClass = testClass;
            this.addBeans = addBeans;
        }


        void processSocketInjectionPoints(@Observes ProcessInjectionPoint<?, WebTarget> event) throws Exception{
             InjectionPoint injectionPoint = event.getInjectionPoint();
             Set<Annotation> qualifiers = injectionPoint.getQualifiers();
                for (Annotation qualifier : qualifiers) {
                    if (qualifier.annotationType().equals(Socket.class)) {
                        String value = ((Socket) qualifier).value();
                        socketAnnotations.put(value, qualifier);
                        break;
                    }
                }

        }

        void registerOtherBeans(@Observes AfterBeanDiscovery event) {

            Client client = ClientBuilder.newClient();

            //register for all named Ports
            socketAnnotations.forEach((namedPort, qualifier) -> {

                event.addBean()
                        .addType(WebTarget.class)
                        .scope(ApplicationScoped.class)
                        .qualifiers(qualifier)
                        .createWith(context -> getWebTarget(client, namedPort));
            });

            event.addBean()
                    .addType(jakarta.ws.rs.client.WebTarget.class)
                    .scope(ApplicationScoped.class)
                    .createWith(context -> getWebTarget(client, "@default"));

        }

        @SuppressWarnings("unchecked")
        private static WebTarget getWebTarget(Client client, String namedPort) {
            try {
                Class<? extends Extension> extClass = (Class<? extends Extension>) Class
                        .forName("io.helidon.microprofile.server.ServerCdiExtension");
                Extension extension = CDI.current().getBeanManager().getExtension(extClass);
                Method m = extension.getClass().getMethod("port", String.class);
                int port = (int) m.invoke(extension, new Object[]{namedPort});
                String uri = "http://localhost:" + port;
                return client.target(uri);
            } catch (ReflectiveOperationException e) {
                return client.target("http://localhost:7001");
            }
        }

        void registerAddedBeans(@Observes BeforeBeanDiscovery event) {
            event.addAnnotatedType(testClass, "junit-" + testClass.getName())
                    .add(ApplicationScoped.Literal.INSTANCE);

            for (AddBean addBean : addBeans) {
                Annotation scope;
                Class<? extends Annotation> definedScope = addBean.scope();

                scope = BEAN_DEFINING.get(definedScope);

                if (scope == null) {
                    throw new IllegalStateException(
                            "Only on of " + BEAN_DEFINING.keySet() + " scopes are allowed in tests. Scope "
                                    + definedScope.getName() + " is not allowed for bean " + addBean.value().getName());
                }

                AnnotatedTypeConfigurator<?> configurator = event
                        .addAnnotatedType(addBean.value(), "junit-" + addBean.value().getName());
                if (!hasBda(addBean.value())) {
                    configurator.add(scope);
                }
            }
        }

        private boolean hasBda(Class<?> value) {
            // does it have bean defining annotation?
            for (Class<? extends Annotation> aClass : BEAN_DEFINING.keySet()) {
                if (value.getAnnotation(aClass) != null) {
                    return true;
                }
            }

            return false;
        }

    }

    private static final class ConfigMeta {
        private final Map<String, String> additionalKeys = new HashMap<>();
        private final List<String> additionalSources = new ArrayList<>();
        private String type;
        private String block;
        private boolean useExisting;
        private String profile;

        private ConfigMeta() {
            // to allow SeContainerInitializer (forbidden by default because of native image)
            additionalKeys.put("mp.initializer.allow", "true");
            additionalKeys.put("mp.initializer.no-warn", "true");
            // to run on random port
            additionalKeys.put("server.port", "0");
            // higher ordinal then all the defaults, system props and environment variables
            additionalKeys.putIfAbsent(ConfigSource.CONFIG_ORDINAL, "1000");
            // profile
            additionalKeys.put("mp.config.profile", "test");
        }

        private void addConfig(AddConfig[] configs) {
            for (AddConfig config : configs) {
                additionalKeys.put(config.key(), config.value());
            }
        }

        private void configuration(Configuration config) {
            if (config == null) {
                return;
            }
            useExisting = config.useExisting();
            profile = config.profile();
            additionalSources.addAll(List.of(config.configSources()));
            //set additional key for profile
            additionalKeys.put("mp.config.profile", profile);
        }

        private void addConfigBlock(AddConfigBlock config) {
            if (config == null) {
                return;
            }
            this.type = config.type();
            this.block = config.value();
        }

        ConfigMeta nextMethod() {
            ConfigMeta methodMeta = new ConfigMeta();

            methodMeta.additionalKeys.putAll(this.additionalKeys);
            methodMeta.additionalSources.addAll(this.additionalSources);
            methodMeta.useExisting = this.useExisting;
            methodMeta.profile = this.profile;

            return methodMeta;
        }
    }


    /**
     * Add WeldRequestScope. Used with {@code AddJaxRs}.
     */
    private static final class WeldRequestScopeLiteral extends AnnotationLiteral<AddBean> implements AddBean {

        static final WeldRequestScopeLiteral INSTANCE = new WeldRequestScopeLiteral();

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public Class<?> value() {
            return org.glassfish.jersey.weld.se.WeldRequestScope.class;
        }

        @Override
        public Class<? extends Annotation> scope() {
            return RequestScoped.class;
        }
    }


    /**
     * Add ProcessAllAnnotatedTypes. Used with {@code AddJaxRs}.
     */
    private static final class ProcessAllAnnotatedTypesLiteral extends AnnotationLiteral<AddExtension> implements AddExtension {

        static final ProcessAllAnnotatedTypesLiteral INSTANCE = new ProcessAllAnnotatedTypesLiteral();

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public Class<? extends Extension> value() {
            return org.glassfish.jersey.ext.cdi1x.internal.ProcessAllAnnotatedTypes.class;
        }
    }

    /**
     * Add ServerCdiExtension. Used with {@code AddJaxRs}.
     */
    private static final class ServerCdiExtensionLiteral extends AnnotationLiteral<AddExtension> implements AddExtension {

        static final ServerCdiExtensionLiteral INSTANCE = new ServerCdiExtensionLiteral();

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public Class<? extends Extension> value() {
            return ServerCdiExtension.class;
        }
    }

    /**
     * Add WeldRequestScope. Used with {@code AddJaxRs}.
     */
    private static final class JaxRsCdiExtensionLiteral extends AnnotationLiteral<AddExtension> implements AddExtension {

        static final JaxRsCdiExtensionLiteral INSTANCE = new JaxRsCdiExtensionLiteral();

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public Class<? extends Extension> value() {
            return JaxRsCdiExtension.class;
        }
    }

    /**
     * Add CdiComponentProvider. Used with {@code AddJaxRs}.
     */
    private static final class CdiComponentProviderLiteral extends AnnotationLiteral<AddExtension> implements AddExtension {

        static final CdiComponentProviderLiteral INSTANCE = new CdiComponentProviderLiteral();

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public Class<? extends Extension> value() {
            return CdiComponentProvider.class;
        }
    }

}
