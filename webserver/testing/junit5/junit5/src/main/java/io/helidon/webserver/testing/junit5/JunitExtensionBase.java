/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.testing.junit5.TestJunitExtension;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.WebServerService__ServiceDescriptor;
import io.helidon.webserver.spi.ConfiguredServerFeatureFactory;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.spi.ServerFeatureProvider;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static io.helidon.webserver.testing.junit5.Junit5Util.withStaticMethods;

abstract class JunitExtensionBase extends TestJunitExtension implements AfterAllCallback {
    private static final TypeName SERVER_FEATURE_PROVIDER = TypeName.create(ServerFeatureProvider.class);
    private static final ResolvedType CONFIGURED_FEATURE_FACTORY = ResolvedType.create(ConfiguredServerFeatureFactory.class);

    private Class<?> testClass;

    JunitExtensionBase() {
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        callAfterStop();
        super.afterAll(extensionContext);
    }

    void setupServer(WebServerConfig.Builder builder) {
        withStaticMethods(testClass(), SetUpServer.class, (setUpServer, method) -> {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1) {
                throw new IllegalArgumentException("Method " + method + " annotated with " + SetUpServer.class.getSimpleName()
                                                           + " does not have exactly one parameter (WebServerConfig.Builder)");
            }
            if (!parameterTypes[0].equals(WebServerConfig.Builder.class)) {
                throw new IllegalArgumentException("Method " + method + " annotated with " + SetUpServer.class.getSimpleName()
                                                           + " does not have exactly one parameter (WebServerConfig.Builder)");
            }
            if (!Modifier.isStatic(method.getModifiers())) {
                throw new IllegalArgumentException("Method " + method + " annotated with " + SetUpServer.class.getSimpleName()
                                                           + " is not static");
            }
            try {
                method.setAccessible(true);
                method.invoke(null, builder);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Could not invoke method " + method, e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    void setupFeatures(WebServerConfig.Builder builder) {
        withStaticMethods(testClass(), SetUpFeatures.class, ((setUpFeatures, method) -> {
            if (!setUpFeatures.value()) {
                builder.featuresDiscoverServices(false);
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 0) {
                throw new IllegalArgumentException("Method " + method + " annotated with " + SetUpFeatures.class.getSimpleName()
                                                           + " has parameter(s), which is not allowed. It should return "
                                                           + " List<ServerFeature>.");
            }
            if (!Modifier.isStatic(method.getModifiers())) {
                throw new IllegalArgumentException("Method " + method + " annotated with " + SetUpFeatures.class.getSimpleName()
                                                           + " is not static");
            }
            Object result;
            try {
                method.setAccessible(true);
                result = method.invoke(null);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Could not invoke method " + method, e);
            }

            List<ServerFeature> features;
            try {
                features = (List<ServerFeature>) result;
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("Method " + method + " annotated with " + SetUpFeatures.class.getSimpleName()
                                                           + " returned a result that is not a List. Supported is "
                                                           + "List<? extends ServerFeature>.", e);
            }
            try {
                for (ServerFeature feature : features) {
                    builder.addFeature(feature);
                }
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("Method " + method + " annotated with " + SetUpFeatures.class.getSimpleName()
                                                           + " returned a result that is a List, but an element was not "
                                                           + "a ServerFeature.", e);
            }
        }));
    }

    static void setupWebServerFromRegistry(WebServerConfig.Builder serverBuilder) {
        var serviceRegistry = GlobalServiceRegistry.registry();
        serverBuilder.serviceRegistry(serviceRegistry);
        Object o = serviceRegistry
                .get(WebServerService__ServiceDescriptor.INSTANCE)
                .orElseThrow(() -> {
                    return new IllegalStateException("Could not discover WebServerService in service registry, both "
                                                             + "'helidon-service-registry' and `helidon-webserver` must be on "
                                                             + "classpath.");
                });
        // the service is package local
        Class<?> clazz = o.getClass();
        try {
            Method method = clazz.getDeclaredMethod("updateServerBuilder",
                                                    WebServerConfig.BuilderBase.class,
                                                    List.class);
            method.setAccessible(true);
            method.invoke(o, serverBuilder, registryServerFeatures(serviceRegistry));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to get service registry specific method on WebServerService", e);
        }
    }

    void testClass(Class<?> testClass) {
        this.testClass = testClass;
    }

    Class<?> testClass() {
        return testClass;
    }

    private static List<ServerFeature> registryServerFeatures(ServiceRegistry serviceRegistry) {
        List<ServiceInfo> providerServices = serviceRegistry.lookupServices(Lookup.create(ServerFeatureProvider.class));
        Lookup featureLookup = Lookup.create(ServerFeature.class);
        List<ServiceInfo> featureServices = serviceRegistry.lookupServices(featureLookup);
        Set<TypeName> providerBackedFactoryTypes = new HashSet<>();
        for (ServiceInfo serviceInfo : featureServices) {
            // Config-backed factory products must be recreated from the final test server configuration.
            if (!serviceInfo.serviceType().equals(serviceInfo.providedType())
                    && serviceInfo.factoryContracts().contains(CONFIGURED_FEATURE_FACTORY)
                    && hasProviderFor(serviceInfo, providerServices)) {
                providerBackedFactoryTypes.add(serviceInfo.serviceType());
            }
        }

        List<ServerFeature> result = new ArrayList<>();
        for (ServiceInfo featureService : featureServices) {
            if (!providerBackedFactoryTypes.contains(featureService.serviceType())) {
                Lookup serviceLookup = Lookup.builder(featureLookup)
                        .serviceType(featureService.serviceType())
                        .build();
                List<ServiceInstance<ServerFeature>> featureInstances = serviceRegistry.lookupInstances(serviceLookup);
                for (ServiceInstance<ServerFeature> featureInstance : featureInstances) {
                    result.add(featureInstance.get());
                }
            }
        }
        return result;
    }

    private static boolean hasProviderFor(ServiceInfo factoryService, List<ServiceInfo> providerServices) {
        ResolvedType providerContract = ResolvedType.create(TypeName.builder(SERVER_FEATURE_PROVIDER)
                                                                    .addTypeArgument(factoryService.providedType())
                                                                    .build());
        return providerServices.stream()
                .anyMatch(serviceInfo -> serviceInfo.contracts().contains(providerContract));
    }

    private void callAfterStop() {
        if (testClass == null) {
            return;
        }

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
}
