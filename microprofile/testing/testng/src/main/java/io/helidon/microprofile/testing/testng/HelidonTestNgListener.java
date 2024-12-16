/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.testing.testng;

import java.io.IOException;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.ProcessInjectionTarget;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.ClientBuilder;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.testng.IClassListener;
import org.testng.ITestClass;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.annotations.Test;

/**
 * TestNG extension to support Helidon CDI container in tests.
 */
public class HelidonTestNgListener implements IClassListener, ITestListener {

    private static final Set<Class<? extends Annotation>> TEST_ANNOTATIONS =
            Set.of(AddBean.class, AddConfig.class, AddExtension.class,
                    Configuration.class, AddJaxRs.class, AddConfigBlock.class);

    private static final Map<Class<? extends Annotation>, AnnotationLiteral<?>> BEAN_DEFINING = Map.of(
            ApplicationScoped.class, ApplicationScoped.Literal.INSTANCE,
            Singleton.class, ApplicationScoped.Literal.INSTANCE,
            RequestScoped.class, RequestScoped.Literal.INSTANCE,
            Dependent.class, Dependent.Literal.INSTANCE);

    private List<AddExtension> classLevelExtensions = new ArrayList<>();
    private List<AddBean> classLevelBeans = new ArrayList<>();
    private ConfigMeta classLevelConfigMeta = new ConfigMeta();
    private boolean classLevelDisableDiscovery = false;
    private boolean resetPerTest;

    private Class<?> testClass;
    private Object testInstance;
    private ConfigProviderResolver configProviderResolver;
    private Config config;
    private SeContainer container;

    @Override
    public void onBeforeClass(ITestClass iTestClass) {

        testClass = iTestClass.getRealClass();

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

        Object[] testInstances = iTestClass.getInstances(false);
        if (testInstances != null && testInstances.length > 0) {
            testInstance = testInstances[0];
        }

        if (!classLevelConfigMeta.useExisting) {
            startContainer(classLevelBeans, classLevelExtensions, classLevelDisableDiscovery);
        }
    }


    @Override
    public void onAfterClass(ITestClass testClass) {
        if (!resetPerTest) {
            releaseConfig();
            stopContainer();
        }
    }

    @Override
    public void onTestStart(ITestResult result) {

        if (resetPerTest) {
            Method method = result.getMethod().getConstructorOrMethod().getMethod();
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
    public void onTestFailure(ITestResult iTestResult) {
        if (resetPerTest) {
            releaseConfig();
            stopContainer();
        }
    }

    @Override
    public void onTestSuccess(ITestResult iTestResult) {
        if (resetPerTest) {
            releaseConfig();
            stopContainer();
        }
    }

    private void validatePerClass() {
        validateMethods(testClass.getMethods());
        validateMethods(testClass.getDeclaredMethods());
        validateConstructors(testClass.getDeclaredConstructors());
        AddJaxRs addJaxRsAnnotation = testClass.getAnnotation(AddJaxRs.class);
        if (addJaxRsAnnotation != null){
            if (testClass.getAnnotation(DisableDiscovery.class) == null){
                throw new RuntimeException("@AddJaxRs annotation should be used only with @DisableDiscovery annotation.");
            }
        }
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
        validateFields(testClass.getFields());
        validateFields(testClass.getDeclaredFields());
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

            classLevelExtensions = new ArrayList<>();
            classLevelBeans = new ArrayList<>();
            classLevelConfigMeta = new ConfigMeta();
            classLevelDisableDiscovery = false;
            configProviderResolver = ConfigProviderResolver.instance();
        }
    }

    @SuppressWarnings("unchecked")
    private void startContainer(List<AddBean> beanAnnotations,
                                List<AddExtension> extensionAnnotations,
                                boolean disableDiscovery) {

        SeContainerInitializer initializer = SeContainerInitializer.newInstance();

        if (disableDiscovery) {
            initializer.disableDiscovery();
        }

        initializer.addExtensions(
                new TestInstanceExtension(testInstance, testClass),
                new AddBeansExtension(beanAnnotations));

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
        container.select(testClass).get();
    }

    private void stopContainer() {
        if (container != null) {
            container.close();
            container = null;
        }
    }

    private List<Annotation> extractMetaAnnotations(Class<?> testClass) {
        Annotation[] testAnnotations = testClass.getAnnotations();
        for (Annotation testAnnotation : testAnnotations) {
            List<Annotation> annotations = List.of(testAnnotation.annotationType().getAnnotations());
            List<Class<?>> annotationsClass = annotations.stream()
                    .map(a -> a.annotationType()).collect(Collectors.toList());
            if (!Collections.disjoint(TEST_ANNOTATIONS, annotationsClass)) {
                // Contains at least one of HELIDON_TEST_ANNOTATIONS
                return annotations;
            }
        }
        return List.of();
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

    private static void validateMethods(Method[] methods) {
        for (Method method : methods) {
            if (method.getAnnotation(Test.class) != null) {
                // a test method
                if (hasAnnotation(method, TEST_ANNOTATIONS)) {
                    throw new RuntimeException("When a class is annotated with @HelidonTest, "
                                               + "there is a single CDI container used to invoke all "
                                               + "test methods on the class. Method " + method
                                               + " has an annotation that modifies container behavior.");
                }
            }
        }
    }

    private static void validateConstructors(Constructor<?>[] constructors) {
        for (Constructor<?> constructor : constructors) {
            if (constructor.getAnnotation(Inject.class) != null) {
                if (hasAnnotation(constructor, TEST_ANNOTATIONS)) {
                    throw new RuntimeException("When a class is annotated with @HelidonTest, "
                                               + "there is a single CDI container used to invoke all "
                                               + "test methods on the class. Do not use @Inject annotation"
                                               + "over constructor. Use it on each field.");
                }
            }
        }
    }

    private static void validateFields(Field[] fields) {
        for (Field field : fields) {
            if (field.getAnnotation(Inject.class) != null) {
                throw new RuntimeException("When a class is annotated with @HelidonTest(resetPerTest=true),"
                                           + " injection into fields or constructor is not supported, as each"
                                           + " test method uses a different CDI container. Field " + field
                                           + " is annotated with @Inject");
            }
        }
    }

    private static boolean hasAnnotation(AnnotatedElement element, Set<Class<? extends Annotation>> annotations) {
        for (Class<? extends Annotation> aClass : annotations) {
            if (element.getAnnotation(aClass) != null) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("CdiManagedBeanInconsistencyInspection")
    private record TestInstanceExtension(Object testInstance, Class<?> testClass) implements Extension {

        void registerTestClass(@Observes BeforeBeanDiscovery event) {
            event.addAnnotatedType(testClass, "testng-" + testClass.getName())
                    .add(SingletonLiteral.INSTANCE);
        }

        @SuppressWarnings("unchecked")
        <T> void registerTestInstances(@Observes ProcessInjectionTarget<T> pit) {
            if (pit.getAnnotatedType().getJavaClass().equals(testClass)) {
                pit.setInjectionTarget(new TestInjectionTarget<>(pit.getInjectionTarget(), (T) testInstance));
            }
        }
    }

    @SuppressWarnings("CdiManagedBeanInconsistencyInspection")
    private record AddBeansExtension(List<AddBean> addBeans) implements Extension {

        void registerOtherBeans(@Observes AfterBeanDiscovery event) {
            event.addBean()
                    .addType(jakarta.ws.rs.client.WebTarget.class)
                    .scope(ApplicationScoped.class)
                    .produceWith(context -> ClientBuilder.newClient().target(clientUri()));
                        }

        void registerAddedBeans(@Observes BeforeBeanDiscovery event) {
            for (AddBean beanDef : addBeans) {
                Class<?> beanType = beanDef.value();
                Class<? extends Annotation> scopeType = beanDef.scope();

                AnnotationLiteral<?> scope = BEAN_DEFINING.get(scopeType);
                if (scope == null) {
                    throw new IllegalStateException(
                            "Only on of " + BEAN_DEFINING.keySet() + " scopes are allowed in tests. Scope "
                                    + scopeType.getName() + " is not allowed for bean " + beanDef.value().getName());
                }

                AnnotatedTypeConfigurator<?> configurator = event.addAnnotatedType(beanType, "testng-" + beanType.getName());
                if (!hasAnnotation(beanType, BEAN_DEFINING.keySet())) {
                    configurator.add(scope);
                }
            }
        }

        @SuppressWarnings("unchecked")
        static String clientUri() {
            try {
                Class<?> extClass = Class.forName("io.helidon.microprofile.server.ServerCdiExtension");
                Extension extension = CDI.current().getBeanManager().getExtension((Class<? extends Extension>) extClass);
                Method m = extension.getClass().getMethod("port");
                int port = (int) m.invoke(extension);
                return "http://localhost:" + port;
            } catch (ReflectiveOperationException e) {
                return "http://localhost:7001";
            }
        }
    }

    record TestInjectionTarget<T>(InjectionTarget<T> delegate, T testInstance)
            implements InjectionTarget<T> {

        @Override
        public void dispose(T i) {
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return delegate.getInjectionPoints();
        }

        @Override
        public void inject(T testInstance, CreationalContext<T> cc) {
            delegate.inject(testInstance, cc);
        }

        @Override
        public void postConstruct(T testInstance) {
            delegate.postConstruct(testInstance);
                }

        @Override
        public void preDestroy(T testInstance) {
            delegate.preDestroy(testInstance);
            }

        @Override
        public T produce(CreationalContext<T> cc) {
            return testInstance;
        }
    }

    private static final class ConfigMeta {
        private final Map<String, String> additionalKeys = new HashMap<>();
        private final List<String> additionalSources = new ArrayList<>();
        private String type;
        private String block;
        private boolean useExisting;
        private String profile;

        ConfigMeta() {
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

        void addConfig(AddConfig[] configs) {
            for (AddConfig config : configs) {
                additionalKeys.put(config.key(), config.value());
            }
        }

        void configuration(Configuration config) {
            if (config == null) {
                return;
            }
            useExisting = config.useExisting();
            profile = config.profile();
            additionalSources.addAll(List.of(config.configSources()));
            //set additional key for profile
            additionalKeys.put("mp.config.profile", profile);
        }

        void addConfigBlock(AddConfigBlock config) {
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

    @SuppressWarnings("ClassExplicitlyAnnotation")
    private static final class WeldRequestScopeLiteral extends AnnotationLiteral<AddBean> implements AddBean {

        static final WeldRequestScopeLiteral INSTANCE = new WeldRequestScopeLiteral();

        @Override
        public Class<?> value() {
            return org.glassfish.jersey.weld.se.WeldRequestScope.class;
        }

        @Override
        public Class<? extends Annotation> scope() {
            return RequestScoped.class;
        }
    }


    @SuppressWarnings("ClassExplicitlyAnnotation")
    private static final class ProcessAllAnnotatedTypesLiteral extends AnnotationLiteral<AddExtension> implements AddExtension {

        static final ProcessAllAnnotatedTypesLiteral INSTANCE = new ProcessAllAnnotatedTypesLiteral();

        @Override
        public Class<? extends Extension> value() {
            return org.glassfish.jersey.ext.cdi1x.internal.ProcessAllAnnotatedTypes.class;
        }
    }

    @SuppressWarnings("ClassExplicitlyAnnotation")
    private static final class ServerCdiExtensionLiteral extends AnnotationLiteral<AddExtension> implements AddExtension {

        static final ServerCdiExtensionLiteral INSTANCE = new ServerCdiExtensionLiteral();

        @Override
        public Class<? extends Extension> value() {
            return ServerCdiExtension.class;
        }
    }

    @SuppressWarnings("ClassExplicitlyAnnotation")
    private static final class JaxRsCdiExtensionLiteral extends AnnotationLiteral<AddExtension> implements AddExtension {

        static final JaxRsCdiExtensionLiteral INSTANCE = new JaxRsCdiExtensionLiteral();

        @Override
        public Class<? extends Extension> value() {
            return JaxRsCdiExtension.class;
        }
    }

    @SuppressWarnings("ClassExplicitlyAnnotation")
    private static final class CdiComponentProviderLiteral extends AnnotationLiteral<AddExtension> implements AddExtension {

        static final CdiComponentProviderLiteral INSTANCE = new CdiComponentProviderLiteral();

        @Override
        public Class<? extends Extension> value() {
            return CdiComponentProvider.class;
        }
    }

    @SuppressWarnings("ClassExplicitlyAnnotation")
    private static final class SingletonLiteral extends AnnotationLiteral<Singleton> implements Singleton {
        static final SingletonLiteral INSTANCE = new SingletonLiteral();
    }
}
