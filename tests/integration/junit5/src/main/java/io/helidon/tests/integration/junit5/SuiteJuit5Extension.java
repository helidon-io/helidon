/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.junit5;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.junit5.spi.ConfigProvider;
import io.helidon.tests.integration.junit5.spi.ContainerProvider;
import io.helidon.tests.integration.junit5.spi.DbClientProvider;
import io.helidon.tests.integration.junit5.spi.SuiteProvider;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

/**
 * {@link Suite} jUnit 5 extension handler.
 */
public class SuiteJuit5Extension
        implements BeforeAllCallback, ExtensionContext.Store.CloseableResource, ParameterResolver {

    private static final Logger LOGGER = System.getLogger(SuiteJuit5Extension.class.getName());

    // Store all stored SuiteProvider instances keys to close them.
    private static final Set<String> PROVIDER_KEYS = new HashSet<>();

    // Already loaded SuiteProvider instances are stored in jUnit 5 GLOBAL store.
    // Store key is SuiteProvider implementing class fully qualified name.
    private String storeKey;
    private Suite suite;
    private Class<? extends SuiteProvider> providerClass;
    private SuiteDescriptor descriptor;
    ExtensionContext.Store globalStore;

    public SuiteJuit5Extension() {
        storeKey = null;
        suite = null;
        providerClass = null;
        descriptor = null;
        globalStore = null;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (context.getTestClass().isPresent()) {
            LOGGER.log(Level.TRACE,
                       () -> String.format("Running beforeAll of %s test class",
                                           context.getTestClass().get().getSimpleName()));
            suite = suiteFromContext(context);
            providerClass = suite.value();
            storeKey = providerClass.getName();
            globalStore = context.getRoot().getStore(GLOBAL);
            descriptor = providerFromStore(globalStore, storeKey);
            // Run the initialization just once for every suite provider
            if (descriptor == null) {
                descriptor = SuiteDescriptor.create(suite, context);
                LOGGER.log(Level.TRACE,
                           () -> String.format("Initializing the Suite provider %s", providerClass.getSimpleName()));
                descriptor.init();
                storeProvider(globalStore, storeKey, descriptor);
                ensureThisInstanceIsStored(globalStore);
            }
        } else {
            throw new IllegalStateException("Test class was not found in jUnit 5 extension context");
        }
    }

    @Override
    public void close() throws Throwable {
        for (String key : PROVIDER_KEYS) {
            SuiteDescriptor descriptor = globalStore.get(key, SuiteDescriptor.class);
            LOGGER.log(Level.TRACE,
                       () -> String.format("Closing Suite provider %s", descriptor.provider.getClass().getSimpleName()));
            descriptor.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return descriptor.supportsParameterGlobal(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return descriptor.resolveParameterGlobal(parameterContext.getParameter().getType());
    }

    private static SuiteDescriptor providerFromStore(ExtensionContext.Store globalStore, String storeKey) {
        return globalStore.get(storeKey, SuiteDescriptor.class);
    }

    private static void storeProvider(ExtensionContext.Store globalStore, String storeKey, SuiteDescriptor descriptor) {
        globalStore.put(storeKey, descriptor);
        PROVIDER_KEYS.add(storeKey);
    }

    private void ensureThisInstanceIsStored(ExtensionContext.Store globalStore) {
        if (globalStore.get(SuiteJuit5Extension.class.getName()) == null) {
            globalStore.put(SuiteJuit5Extension.class.getName(), this);
        }
    }

    private static Suite suiteFromContext(ExtensionContext context) {
        Class<?> testClass = context.getTestClass().get();
        Suite suite = testClass.getAnnotation(Suite.class);
        if (suite == null) {
            throw new IllegalStateException(
                    String.format("Suite annotation was not found on %s class", testClass.getSimpleName()));
        }
        return suite;
    }

    /**
     * {@link Suite} and {@link SuiteProvider} internal descriptor.
     */
    final static class SuiteDescriptor implements SuiteResolver, SuiteContext {

        // Helidon service loader for SuiteProvider instances
        private static final HelidonServiceLoader<SuiteProvider> LOADER = HelidonServiceLoader
                .builder(ServiceLoader.load(SuiteProvider.class))
                .build();

        private final SuiteProvider provider;
        // Shared storage mapped to jUnit 5 global storage
        private final Storage storage;
        // Additional providers added by annotations on SuiteProvider class
        private final Map<Class<? extends Annotation>, ProviderDescriptor> providers;

        private SuiteDescriptor(Suite suite,
                                SuiteProvider provider,
                                Map<Class<? extends Annotation>, ProviderDescriptor> providers,
                                ExtensionContext context) {
            this.provider = provider;
            // Isolate ExtensionContext.Store namespaces for individual providers (descriptors)
            this.storage = new SuiteJunit5Storage(
                    context.getRoot().getStore(
                            ExtensionContext.Namespace.create(suite.value().getName())));
            this.providers = providers;
        }

        @Override
        public boolean supportsParameter(Type type) {
            // SuiteProvider may also implement SuiteResolver
            if (containsInterface(provider.getClass(), SuiteResolver.class)
                    && ((SuiteResolver)provider).supportsParameter(type)) {
                return true;
            }
            // Always resolve SuiteContext as this
            return SuiteContext.class.isAssignableFrom((Class<?>)type);
        }

        @Override
        public Object resolveParameter(Type type) {
            // SuiteProvider may also implement SuiteResolver
            if (containsInterface(provider.getClass(), SuiteResolver.class)
                    && ((SuiteResolver)provider).supportsParameter(type)) {
                return ((SuiteResolver)provider).resolveParameter(type);
            }
            // Always resolve SuiteContext as this
            if (SuiteContext.class.isAssignableFrom((Class<?>)type)) {
                return this;
            }
            throw new IllegalArgumentException(String.format("Cannot resolve parameter Type %s", type.getTypeName()));
        }

        // Resolve parameter globally (using suite provider and all suite extension providers)
        private boolean supportsParameterGlobal(Class<?> cls) {
            if (supportsParameter(cls)) {
                return true;
            }
            for (ProviderDescriptor provider : providers.values()) {
                if (containsInterface(provider.provider().getClass(), SuiteResolver.class)
                        && ((SuiteResolver)provider.provider()).supportsParameter(cls)) {
                    return true;
                }
            }
            return false;
        }

        // Resolve parameter globally (using suite provider and all suite extension providers)
        private Object resolveParameterGlobal(Class<?> cls) {
            if (supportsParameter(cls)) {
                return resolveParameter(cls);
            }
            for (ProviderDescriptor provider : providers.values()) {
                if (containsInterface(provider.provider().getClass(), SuiteResolver.class)
                        && ((SuiteResolver)provider.provider()).supportsParameter(cls)) {
                    return ((SuiteResolver)provider.provider()).resolveParameter(cls);
                }
            }
            throw new IllegalArgumentException(String.format("Cannot resolve parameter Class %s", cls.getName()));
        }

        @Override
        public Storage storage() {
            return storage;
        }

        // Run suite initialization
        private void init() {
            // Pass suite context to all providers
            provider.suiteContext(this);
            providers.values().forEach(descriptor -> descriptor.provider().suiteContext(this));
            // Scan suite provider for method annotated for execution during initialization and startup phase
            Method[] methods = provider.getClass().getMethods();
            Set<Method> added = new HashSet<>();
            List<Method> setUpConfig = new LinkedList<>();
            List<Method> setUpContainer = new LinkedList<>();
            List<Method> setUpDbClient = new LinkedList<>();
            List<Method> beforeSuite = new LinkedList<>();
            // Scan SuiteProvider methods for init annotations
            for (Method method : methods) {
                // @SetUpConfig annotated methods are called 1st
                if (method.isAnnotationPresent(SetUpConfig.class)) {
                    if (!added.contains(method)) {
                        setUpConfig.add(method);
                        added.add(method);
                    }
                }
                // @SetUpContainer annotated methods are called 2nd
                if (method.isAnnotationPresent(SetUpContainer.class)) {
                    if (!added.contains(method)) {
                        setUpContainer.add(method);
                        added.add(method);
                    }
                }
                // @SetUpDbClient annotated methods are called 3rd
                if (method.isAnnotationPresent(SetUpDbClient.class)) {
                    if (!added.contains(method)) {
                        setUpDbClient.add(method);
                        added.add(method);
                    }
                }
                // @BeforeSuite annotated methods are called last
                if (method.isAnnotationPresent(BeforeSuite.class)) {
                    if (!added.contains(method)) {
                        beforeSuite.add(method);
                        added.add(method);
                    }
                }
            }
            initInternalProviders();
            setupConfig(setUpConfig);
            setupContainer(setUpContainer);
            setupDbClient(setUpDbClient);
            // Run @BeforeSuite annotated method as last step
            beforeSuite.forEach(this::callMethod);
        }

        // Run suite cleanup
        private void close() {
            for (Method method : provider.getClass().getMethods()) {
                if (method.isAnnotationPresent(AfterSuite.class)) {
                    callMethod(method);
                }
            }
            closeContainer();
        }

        private void initInternalProviders() {
            // TestConfig provider initialization
            ProviderDescriptor testConfigProvider = providers.get(TestConfig.class);
            if (testConfigProvider != null) {
                TestConfig testConfig = provider.getClass().getAnnotation(TestConfig.class);
                ConfigProvider configProvider = testConfigProvider.provider().as(ConfigProvider.class);
                configProvider.file(testConfig.file());
            }
            // ContainerTest provider initialization
            ProviderDescriptor containerTestProvider = providers.get(ContainerTest.class);
            if (containerTestProvider != null) {
                ContainerTest containerTest = provider.getClass().getAnnotation(ContainerTest.class);
                String image = containerTest.image();
                ContainerProvider containerProvider = containerTestProvider.provider().as(ContainerProvider.class);
                containerProvider.image(image == null || image.isEmpty() ? Optional.empty() : Optional.of(image));
            }
        }

        // Configuration is initialized first
        private void setupConfig(List<Method> setUpConfig) {
            // TestConfig provider setup
            ProviderDescriptor descriptor = providers.get(TestConfig.class);
            if (descriptor != null) {
                ConfigProvider configProvider = descriptor.provider().as(ConfigProvider.class);
                configProvider.setup();
                setUpConfig.forEach(method -> callSetUpConfig(method, descriptor));
                configProvider.start();
            } else {
                setUpConfig.forEach(this::callMethod);
            }
        }

        // Container is initialized second
        private void setupContainer(List<Method> setUpContainer) {
            ProviderDescriptor descriptor = providers.get(ContainerTest.class);
            if (descriptor != null) {
                ContainerProvider containerProvider = descriptor.provider().as(ContainerProvider.class);
                containerProvider.setup();
                setUpContainer.forEach(method -> callSetUpContainer(method, descriptor));
                containerProvider.start();
            } else {
                setUpContainer.forEach(this::callMethod);
            }
        }

        // Container is the only one being closed
        private void closeContainer() {
            ProviderDescriptor descriptor = providers.get(ContainerTest.class);
            if (descriptor != null) {
                ContainerProvider containerProvider = descriptor.provider().as(ContainerProvider.class);
                containerProvider.stop();
            }
        }

        // DbClient is initialized third
        private void setupDbClient(List<Method> setUpDbClient) {
            ProviderDescriptor descriptor = providers.get(DbClientTest.class);
            if (descriptor != null) {
                DbClientProvider containerProvider = descriptor.provider().as(DbClientProvider.class);
                containerProvider.setup();
                setUpDbClient.forEach(method -> callSetUpDbClient(method, descriptor));
                containerProvider.start();
            } else {
                setUpDbClient.forEach(this::callMethod);
            }
        }

        // Call @SetUpConfig annotated method with additional Config.Builder resolver
        private void callSetUpConfig(Method method, ProviderDescriptor descriptor) {
            Type[] types = method.getGenericParameterTypes();
            int count = method.getParameterCount();
            Object[] parameters = new Object[count];
            for (int i = 0; i < count; i++) {
                parameters[i] = resolveSetUpConfigParameter(types[i], descriptor);
            }
            invoke(method, parameters);
       }

        // Call @SetUpContainer annotated method with additional ContainerConfig.Builder resolver
        private void callSetUpContainer(Method method, ProviderDescriptor descriptor) {
            Type[] types = method.getGenericParameterTypes();
            int count = method.getParameterCount();
            Object[] parameters = new Object[count];
            for (int i = 0; i < count; i++) {
                parameters[i] = resolveSetUpContainerParameter(types[i], descriptor);
            }
            invoke(method, parameters);
        }

        // Call @SetUpDbClient annotated method with additional DbClient.Builder resolver
        private void callSetUpDbClient(Method method, ProviderDescriptor descriptor) {
            Type[] types = method.getGenericParameterTypes();
            int count = method.getParameterCount();
            Object[] parameters = new Object[count];
            for (int i = 0; i < count; i++) {
                parameters[i] = resolveSetUpDbClientParameter(types[i], descriptor);
            }
            invoke(method, parameters);
        }

        // Extended resolver for @SetUpConfig annotated method
        private Object resolveSetUpConfigParameter(Type type, ProviderDescriptor configProvider) {
            // Resolver exception for Config.Builder which must be supplied by ConfigProvider's builder getter
            if (configProvider != null
                    && Config.Builder.class.isAssignableFrom((Class<?>)type)) {
                return configProvider.provider().as(ConfigProvider.class).builder();
            }
            return resolve(type);
        }

        // Extended resolver for @SetUpContainer annotated method
        private Object resolveSetUpContainerParameter(Type type, ProviderDescriptor containerProvider) {
            // Resolver exception for Config.Builder which must be supplied by ConfigProvider's builder getter
            if (containerProvider != null
                    && ContainerConfig.Builder.class.isAssignableFrom((Class<?>)type)) {
                return containerProvider.provider().as(ContainerProvider.class).builder();
            }
            return resolve(type);
        }

        // Extended resolver for @SetUpDbClient annotated method
        private Object resolveSetUpDbClientParameter(Type type, ProviderDescriptor containerProvider) {
            // Resolver exception for Config.Builder which must be supplied by ConfigProvider's builder getter
            if (containerProvider != null
                    && DbClient.Builder.class.isAssignableFrom((Class<?>)type)) {
                return containerProvider.provider().as(DbClientProvider.class).builder();
            }
            return resolve(type);
        }

        /**
         * Call method with resolved parameters.
         *
         * @param method method handler
         */
        private void callMethod(Method method) {
            Type[] types = method.getGenericParameterTypes();
            int count = method.getParameterCount();
            Object[] parameters = new Object[count];
            for (int i = 0; i < count; i++) {
                parameters[i] = resolve(types[i]);
            }
            invoke(method, parameters);
        }

        // Resolve single method parameter
        private Object resolve(Type type) {
            Function<Type, Object> resolver = null;
            // Suite resolver
            if (supportsParameter(type)) {
                resolver = this::resolveParameter;
            } else {
                for (ProviderDescriptor provider : providers.values()) {
                    if (containsInterface(provider.provider().getClass(), SuiteResolver.class)
                            && ((SuiteResolver) provider.provider()).supportsParameter(type)) {
                        resolver = ((SuiteResolver) provider.provider())::resolveParameter;
                        break;
                    }
                }
            }
            if (resolver != null) {
                return resolver.apply(type);
            } else {
                throw new IllegalArgumentException(
                        String.format("Cannot resolve parameter Type %s", type.getTypeName()));
            }
        }

        // Invoke provider's method
        private void invoke(Method method, Object[] parameters) {
            try {
                method.invoke(provider, parameters);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(
                        String.format("Could not invoke %s method %s",
                                      provider.getClass().getSimpleName(),
                                      method.getName())
                        ,e);
            }
        }

        private static SuiteDescriptor create(Suite suite, ExtensionContext context) {
            Class<? extends SuiteProvider> providerClass = suite.value();
            SuiteProvider provider = load(providerClass);
            Map<Class<? extends Annotation>, ProviderDescriptor> providers = providers(providerClass);
            return new SuiteDescriptor(suite, provider, providers, context);
        }

        private static SuiteProvider load(Class<? extends SuiteProvider> providerClass) {
            List<SuiteProvider> loadedProviders = LOADER.stream()
                    .filter(providerClass::isInstance)
                    .toList();
            switch (loadedProviders.size()) {
                case 0:
                    throw new IllegalStateException(
                            String.format("No SuiteProvider %s instance found on the classpath",
                                          providerClass.getSimpleName()));
                case 1:
                    return loadedProviders.getFirst();
                default:
                    throw new IllegalStateException(
                            String.format("Multiple SuiteProvider %s instances found on the classpath",
                                          providerClass.getSimpleName()));
            }

        }

        private static Map<Class<? extends Annotation>, ProviderDescriptor> providers (
                Class<? extends SuiteProvider> providerClass) {
            Map<Class<? extends Annotation>, ProviderDescriptor> providers = new HashMap<>();
            for (Annotation annotation : providerClass.getAnnotations()) {
                // Default supported annotation, use internal initialization
                if (ProviderDescriptor.ANNOTATIONS.contains(annotation.annotationType())) {
                    if (annotation.annotationType().equals(TestConfig.class)) {
                        TestConfig testConfig = (TestConfig) annotation;
                        SuiteExtensionProvider provider =  ProviderDescriptor.load(ConfigProvider.class,
                                                                                   testConfig.provider());
                        providers.put(TestConfig.class, new ProviderDescriptor(provider));
                    }
                    if (annotation.annotationType().equals(ContainerTest.class)) {
                        ContainerTest containerTest = (ContainerTest) annotation;
                        SuiteExtensionProvider provider =  ProviderDescriptor.load(ContainerProvider.class,
                                                                                   containerTest.provider());
                        providers.put(ContainerTest.class, new ProviderDescriptor(provider));
                    }
                    if (annotation.annotationType().equals(DbClientTest.class)) {
                        DbClientTest dbClientTest = (DbClientTest) annotation;
                        SuiteExtensionProvider provider =  ProviderDescriptor.load(DbClientProvider.class,
                                                                                   dbClientTest.provider());
                        providers.put(DbClientTest.class, new ProviderDescriptor(provider));
                    }
                }
                // External annotation, use ExternalInitializer
                if (ProviderDescriptor.EXTERNAL_ANNOTATIONS.keySet().contains(annotation.annotationType())) {
                    ExternalInitializer initializer = ProviderDescriptor.EXTERNAL_ANNOTATIONS.get(annotation.annotationType());
                    SuiteExtensionProvider provider =  ProviderDescriptor.load(initializer.providerInterface(),
                                                                               initializer.providerClass());
                    providers.put(initializer.annotationClass(), new ProviderDescriptor(provider, Optional.of(initializer)));
                }
            }
            return providers;
        }

        // Search whether class or any of it's super class implements interface
        private static boolean containsInterface(Class<?> cls, Class<?> interfaceCls) {
            if (hasInterface(cls, interfaceCls)) {
                return true;
            }
            Class<?> superCls = cls.getSuperclass();
            while (superCls != null) {
                if (hasInterface(superCls, interfaceCls)) {
                    return true;
                }
                superCls = superCls.getSuperclass();
            }
            return false;
        }


        private static boolean hasInterface(Class<?> cls, Class<?> interfaceCls) {
            Class<?>[] interfaces = cls.getInterfaces();
            for (Class<?> iface : interfaces) {
                if (interfaceCls.equals(iface)) {
                    return true;
                }
            }
            return false;
        }

    }

    /**
     * Additional {@link SuiteExtensionProvider} descriptors.
     * Those are providers added by annotations on {@link SuiteProvider} class.
     */
    final static class ProviderDescriptor {

        // Default supported annotations
        private static final Set<Class<? extends Annotation>> ANNOTATIONS = Set.of(
                TestConfig.class,
                ContainerTest.class,
                DbClientTest.class
        );

        // External registered annotations, not used in current version
        private static final Map<Class<? extends Annotation>,
                           ExternalInitializer> EXTERNAL_ANNOTATIONS = new HashMap<>();

        // Providers service loaders as Map of provider interface class to HelidonServiceLoader
        private static final Map<Class<? extends SuiteExtensionProvider>,
                                 HelidonServiceLoader<? extends SuiteExtensionProvider>> LOADERS = new HashMap<>();

        private final SuiteExtensionProvider provider;

        // External provider initializer, not used in current version
        private final Optional<ExternalInitializer> initializer;

        private ProviderDescriptor(SuiteExtensionProvider provider) {
            this(provider, Optional.empty());
        }

        private ProviderDescriptor(SuiteExtensionProvider provider, Optional<ExternalInitializer> initializer) {
            this.provider = provider;
            this.initializer = initializer;
        }

        private SuiteExtensionProvider provider() {
            return provider;
        }

        /**
         * Load additional {@link SuiteExtensionProvider} instance using service loader.
         *
         * @param providerInterface provider interface used in {@link HelidonServiceLoader}
         * @param providerClass provider interface implementing class from {@link SuiteProvider} annotation
         * @return loaded {@link SuiteExtensionProvider} instance
         */
        private static SuiteExtensionProvider load(Class<? extends SuiteExtensionProvider> providerInterface,
                                                   Class<? extends SuiteExtensionProvider> providerClass) {
            List<? extends SuiteExtensionProvider> loadedProviders = loader(providerInterface).stream()
                    .filter(providerClass::isInstance)
                    .toList();
            switch (loadedProviders.size()) {
                case 0:
                    throw new IllegalStateException(
                            String.format("No Junit5ExtensionProvider %s instance found on the classpath",
                                          providerClass.getSimpleName()));
                case 1:
                    return loadedProviders.getFirst();
                default:
                    throw new IllegalStateException(
                            String.format("Multiple Junit5ExtensionProvider %s instances found on the classpath",
                                          providerClass.getSimpleName()));
            }
        }

        private static HelidonServiceLoader<? extends SuiteExtensionProvider> loader(Class<? extends SuiteExtensionProvider> providerInterface) {
            HelidonServiceLoader<? extends SuiteExtensionProvider> loader = LOADERS.get(providerInterface);
            if (loader != null) {
                return loader;
            } else {
                loader = HelidonServiceLoader
                        .builder(ServiceLoader.load(providerInterface))
                        .build();
                LOADERS.put(providerInterface, loader);
                return loader;
            }
        }

    }

    // Not used in current version
    public interface ExternalInitializer {
        Class<? extends Annotation> annotationClass();
        Class<? extends SuiteExtensionProvider> providerInterface();
        Class<? extends SuiteExtensionProvider> providerClass();
    }

}
