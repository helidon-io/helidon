/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.integrations.graal.mp.nativeimage.extension;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReaderFactory;

import io.helidon.integrations.graal.nativeimage.extension.NativeConfig;

import com.oracle.svm.core.annotate.AutomaticFeature;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.jboss.weld.bean.proxy.ClientProxyFactory;
import org.jboss.weld.bean.proxy.ClientProxyProvider;
import org.jboss.weld.manager.BeanManagerImpl;
import org.jboss.weld.util.Proxies;

/**
 * An automatic feature for native-image to
 *  register Weld specific stuff.
 */
@AutomaticFeature
public class WeldFeature implements Feature {
    private static final boolean ENABLED = NativeConfig.option("weld.enable-feature", true);
    private static final boolean TRACE = NativeConfig.option("weld.trace", false);
    private static final boolean WARN = NativeConfig.option("weld.warn", false);

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ENABLED;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void duringSetup(DuringSetupAccess access) {
        Class<?> beanManagerClass = access.findClassByName("org.jboss.weld.manager.BeanManagerImpl");
        Set<Class<?>> processed = new HashSet<>();
        Set<Set<Type>> processedExplicitProxy = new HashSet<>();
        Set<Object> processedBeanManagers = Collections.newSetFromMap(new IdentityHashMap<>());
        List<WeldProxyConfig> weldProxyConfigs = weldProxyConfigurations(access);

        trace(() -> "Weld feature");

        access.registerObjectReplacer((obj) -> {
            if (beanManagerClass.isInstance(obj) && processedBeanManagers.add(obj)) {
                try {
                    BeanManagerImpl bm = (BeanManagerImpl) obj;
                    ClientProxyProvider cpp = bm.getClientProxyProvider();

                    String contextId = bm.getContextId();
                    List<Bean<?>> beans = bm.getBeans();
                    // TODO it should be sufficient to create proxy classes, no need to actually select stuff
                    // the selected stuff is cached and may cause trouble in runtime (configuration!!!)
                    iterateBeans(bm, cpp, processed, beans);

                    weldProxyConfigs.forEach(proxy -> {
                        initializeProxy(access,
                                        processedExplicitProxy,
                                        contextId,
                                        proxy.beanClass,
                                        proxy.interfaces);
                    });
                } catch (Exception ex) {
                    warn(() -> "Error processing object " + obj);
                    warn(() -> "  " + ex.getClass().getName() + ": " + ex.getMessage());
                }
            }
            return obj;
        });
    }

    private void trace(Supplier<String> message) {
        if (TRACE) {
            System.out.println(message.get());
        }
    }

    private void warn(Supplier<String> message) {
        if (WARN) {
            System.err.println(message.get());
        }
    }

    private void initializeProxy(DuringSetupAccess access,
                                 Set<Set<Type>> processedExplicitProxy,
                                 String contextId,
                                 String beanType,
                                 String... typeClasses) {

        trace(() -> beanType);

        Class<?> beanClass = access.findClassByName(beanType);
        if (null == beanClass) {
            warn(() -> "  Bean class not found: " + beanType);
            return;
        }

        Set<Type> types = new HashSet<>();

        for (String typeClass : typeClasses) {
            Class<?> theClass = access.findClassByName(typeClass);
            if (null == theClass) {
                warn(() -> "  Class not found: " + typeClass);
                return;
            }
            types.add(theClass);
        }

        if (processedExplicitProxy.add(types)) {
            Bean<?> theBean = new ProxyBean(beanClass, types);

            Proxies.TypeInfo typeInfo = Proxies.TypeInfo.of(types);

            ClientProxyFactory<?> cpf = new ClientProxyFactory<>(contextId, typeInfo.getSuperClass(), types, theBean);

            Class<?> proxyClass = cpf.getProxyClass();
            trace(() -> "  Registering proxy class " + proxyClass.getClassLoader() + " with types " + types);
            RuntimeReflection.register(proxyClass);
            RuntimeReflection.register(proxyClass.getConstructors());
            RuntimeReflection.register(proxyClass.getDeclaredConstructors());
            RuntimeReflection.register(proxyClass.getMethods());
            RuntimeReflection.register(proxyClass.getDeclaredMethods());
            RuntimeReflection.register(proxyClass.getFields());
            RuntimeReflection.register(proxyClass.getDeclaredFields());
        }
    }

    private void iterateBeans(BeanManagerImpl bm,
                              ClientProxyProvider cpp,
                              Set<Class<?>> processed,
                              Collection<Bean<?>> beans) {
        for (Bean<?> bean : beans) {
            Class<?> clazz = bean.getBeanClass();
            if (!processed.add(clazz)) {
                continue;
            }

            try {
                Object proxy = cpp.getClientProxy(bean);
                trace(() -> "Created proxy for bean: " + bean.getBeanClass() + ": " + proxy.getClass());
            } catch (Exception e) {
                // try interfaces
                warn(() -> "Failed to create a proxy for bean "
                        + bean.getBeanClass() + ", "
                        + e.getClass().getName() + ": "
                        + e.getMessage() + ", trying with interfaces");

                Class<?>[] interfaces = bean.getBeanClass().getInterfaces();
                for (Class<?> anInterface : interfaces) {
                    Instance<?> select = CDI.current().select(anInterface);
                    select.forEach(it -> {
                        trace(() -> "Found proxy via interface: "
                                + anInterface.getName()
                                + ": " + it.getClass().getName());
                    });
                }
            }

            // now we also need to handle all types
            Set<Type> types = bean.getTypes();
            types.forEach(type -> iterateBeans(bm, cpp, processed, bm.getBeans(type)));
        }
    }

    static List<WeldProxyConfig> weldProxyConfigurations(DuringSetupAccess access) {
        try {
            ClassLoader classLoader = access.findClassByName("io.helidon.config.Config").getClassLoader();
            Enumeration<URL> resources = classLoader
                    .getResources("META-INF/native-image/weld-proxies.json");

            JsonReaderFactory readerFactory = Json.createReaderFactory(Map.of());
            List<WeldProxyConfig> weldProxies = new ArrayList<>();
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                JsonArray proxies = readerFactory.createReader(url.openStream()).readArray();
                proxies.forEach(jsonValue -> {
                    weldProxies.add(new WeldProxyConfig((JsonObject) jsonValue));
                });
            }
            return weldProxies;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to get resources", e);
        }
    }

    /**
     * Proxy used to initialize Weld.
     */
    static final class ProxyBean implements Bean<Object> {

        private final Class<?> beanClass;
        private final Set<Type> types;

        ProxyBean(Class<?> beanClass, Set<Type> types) {
            this.beanClass = beanClass;

            this.types = types;
        }

        @Override
        public Class<?> getBeanClass() {
            return beanClass;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public boolean isNullable() {
            return false;
        }

        @Override
        public Object create(CreationalContext<Object> creationalContext) {
            throw new IllegalStateException("This bean should not be created");
        }

        @Override
        public void destroy(Object instance, CreationalContext<Object> creationalContext) {
        }

        @Override
        public Set<Type> getTypes() {
            return types;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return Set.of(Any.Literal.INSTANCE, Default.Literal.INSTANCE);
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return ApplicationScoped.class;
        }

        @Override
        public String getName() {
            return beanClass.getName();
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public boolean isAlternative() {
            return false;
        }
    }

    private static class WeldProxyConfig {
        private final String beanClass;
        private final String[] interfaces;

        private WeldProxyConfig(JsonObject jsonValue) {
            this.beanClass = jsonValue.getString("bean-class");
            JsonArray array = jsonValue.getJsonArray("ifaces");
            int size = array.size();
            interfaces = new String[size];
            for (int i = 0; i < size; i++) {
                interfaces[i] = array.getString(i);
            }
        }
    }
}
