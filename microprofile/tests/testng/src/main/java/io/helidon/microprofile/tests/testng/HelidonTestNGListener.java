/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.testng;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.config.yaml.mp.YamlMpConfigSource;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.InjectionTargetFactory;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.testng.IClassListener;
import org.testng.ITestClass;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.annotations.Test;

/**
 * TestNG extension to support Helidon CDI container in tests.
 */
public class HelidonTestNGListener implements IClassListener, ITestListener {

    private static final Set<Class<? extends Annotation>> HELIDON_TEST_ANNOTATIONS =
            Set.of(AddBean.class, AddConfig.class, AddExtension.class, Configuration.class);
    private static final Map<Class<? extends Annotation>, Annotation> BEAN_DEFINING = new HashMap<>();

    private static final List<String> YAML_SUFFIXES = List.of(".yml", ".yaml");

    static {
        BEAN_DEFINING.put(ApplicationScoped.class, ApplicationScoped.Literal.INSTANCE);
        BEAN_DEFINING.put(Singleton.class, ApplicationScoped.Literal.INSTANCE);
        BEAN_DEFINING.put(RequestScoped.class, RequestScoped.Literal.INSTANCE);
        BEAN_DEFINING.put(Dependent.class, Dependent.Literal.INSTANCE);
    }

    private List<AddExtension> classLevelExtensions = new ArrayList<>();
    private List<AddBean> classLevelBeans = new ArrayList<>();
    private ConfigMeta classLevelConfigMeta = new ConfigMeta();
    private boolean classLevelDisableDiscovery = false;
    private boolean resetPerTest;

    private Class<?> testClass;
    private ConfigProviderResolver configProviderResolver;
    private Config config;
    private SeContainer container;

    @Override
    public void onBeforeClass(ITestClass iTestClass) {

        testClass = iTestClass.getRealClass();

        AddConfig[] configs = getAnnotations(testClass, AddConfig.class);
        classLevelConfigMeta.addConfig(configs);
        classLevelConfigMeta.configuration(testClass.getAnnotation(Configuration.class));
        configProviderResolver = ConfigProviderResolver.instance();

        AddExtension[] extensions = getAnnotations(testClass, AddExtension.class);
        classLevelExtensions.addAll(Arrays.asList(extensions));

        AddBean[] beans = getAnnotations(testClass, AddBean.class);
        classLevelBeans.addAll(Arrays.asList(beans));

        HelidonTest testAnnot = testClass.getAnnotation(HelidonTest.class);
        if (testAnnot != null) {
            resetPerTest = testAnnot.resetPerTest();
        }

        DisableDiscovery discovery = testClass.getAnnotation(DisableDiscovery.class);
        if (discovery != null) {
            classLevelDisableDiscovery = discovery.value();
        }

        if (resetPerTest) {
            validatePerTest();
            return;
        }
        validatePerClass();

        configure(classLevelConfigMeta);

        if (!classLevelConfigMeta.useExisting) {
            startContainer(classLevelBeans, classLevelExtensions, classLevelDisableDiscovery);
        }

        //Injecting Instances to CDI
        Object[] instances = iTestClass.getInstances(false);

        for (Object instance : instances) {
            injectTestToCdi(instance, iTestClass.getRealClass());
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

    /*
     * Helper method to inject TestNG initialized classes into CDI Container.
     */
    private <T> void injectTestToCdi(Object bean, final Class<T> clazz) {
        BeanManager beanManager = CDI.current().getBeanManager();

        AnnotatedType<T> annotatedType = beanManager.createAnnotatedType(clazz);
        InjectionTargetFactory<T> injectionTargetFactory = beanManager.getInjectionTargetFactory(annotatedType);
        InjectionTarget<T> injectionTarget = injectionTargetFactory.createInjectionTarget(null);

        CreationalContext<T> creationalContext = beanManager.createCreationalContext(null);
        injectionTarget.inject((T) bean, creationalContext);
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

        Constructor<?>[] declaredConstructors = testClass.getDeclaredConstructors();
        for (Constructor<?> constructor : declaredConstructors) {
            if (constructor.getAnnotation(Inject.class) != null) {
                if (hasHelidonTestAnnotation(constructor)) {
                    throw new RuntimeException("When a class is annotated with @HelidonTest, "
                            + "there is a single CDI container used to invoke all "
                            + "test methods on the class. Do not use @Inject annotation"
                            + "over constructor. Use it on each field.");
                }
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
                // If not using a YAML extension, assume properties file
                String fileName = it.trim();
                if (YAML_SUFFIXES.stream().anyMatch(fileName::endsWith)) {
                    builder.withSources(YamlMpConfigSource.classPath(it).toArray(new ConfigSource[0]));
                } else {
                    builder.withSources(MpConfigSources.classPath(it).toArray(new ConfigSource[0]));
                }
            });
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

        // now let's prepare the CDI bootstrapping
        SeContainerInitializer initializer = SeContainerInitializer.newInstance();

        if (disableDiscovery) {
            initializer.disableDiscovery();
        }

        initializer.addExtensions(new AddBeansExtension(testClass, beanAnnotations));

        for (AddExtension addExtension : extensionAnnotations) {
            var extensionClass = addExtension.value();
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


    @SuppressWarnings("unchecked")
    private <T extends Annotation> T[] getAnnotations(Class<?> testClass, Class<T> annotClass) {
        // inherited does not help, as it only returns annot from superclass if
        // child has none
        T[] directAnnotations = testClass.getAnnotationsByType(annotClass);

        List<T> allAnnotations = new ArrayList<>(List.of(directAnnotations));

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

    // this is not registered as a bean - we manually register an instance
    @SuppressWarnings("CdiManagedBeanInconsistencyInspection")
    private static class AddBeansExtension implements Extension {
        private final Class<?> testClass;
        private final List<AddBean> addBeans;

        private AddBeansExtension(Class<?> testClass, List<AddBean> addBeans) {
            this.testClass = testClass;
            this.addBeans = addBeans;
        }

        @SuppressWarnings("unchecked")
        void registerOtherBeans(@Observes AfterBeanDiscovery event) {
            Client client = ClientBuilder.newClient();

            event.addBean()
                    .addType(jakarta.ws.rs.client.WebTarget.class)
                    .scope(ApplicationScoped.class)
                    .createWith(context -> {
                        try {
                            Class<? extends Extension> extClass = (Class<? extends Extension>) Class
                                    .forName("io.helidon.microprofile.server.ServerCdiExtension");
                            Extension extension = CDI.current().getBeanManager().getExtension(extClass);
                            Method m = extension.getClass().getMethod("port");
                            int port = (int) m.invoke(extension);
                            String uri = "http://localhost:" + port;
                            return client.target(uri);
                        } catch (ReflectiveOperationException e) {
                            return client.target("http://localhost:7001");
                        }
                    });
        }

        void registerAddedBeans(@Observes BeforeBeanDiscovery event) {
            event.addAnnotatedType(testClass, "testng-" + testClass.getName())
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
                        .addAnnotatedType(addBean.value(), "testng-" + addBean.value().getName());
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

        ConfigMeta nextMethod() {
            ConfigMeta methodMeta = new ConfigMeta();

            methodMeta.additionalKeys.putAll(this.additionalKeys);
            methodMeta.additionalSources.addAll(this.additionalSources);
            methodMeta.useExisting = this.useExisting;
            methodMeta.profile = this.profile;

            return methodMeta;
        }
    }
}
