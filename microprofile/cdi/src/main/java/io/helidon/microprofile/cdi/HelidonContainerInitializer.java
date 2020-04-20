/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.cdi;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.logging.Logger;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.Extension;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * This class ensures that container is created by us.
 */
public class HelidonContainerInitializer extends SeContainerInitializer {
    private static final Logger LOGGER = Logger.getLogger(HelidonContainerInitializer.class.getName());
    static final String CONFIG_ALLOW_INITIALIZER = "mp.initializer.allow";
    static final String CONFIG_INITIALIZER_NO_WARN = "mp.initializer.no-warn";

    private final HelidonContainerImpl container = new HelidonContainerImpl();
    /**
     * This constructor ensures that we are not created through standard CDI means.
     * @throws java.lang.IllegalStateException unless explicitly configured not to do so.
     */
    public HelidonContainerInitializer() {
        Config config = ConfigProvider.getConfig();
        if (!config.getOptionalValue(CONFIG_ALLOW_INITIALIZER, Boolean.class).orElse(false)) {
            throw new IllegalStateException("Helidon MUST be started using "
                                                    + Main.class.getName()
                                                    + ", or through io.helidon.microprofile.server.Server. "
                                                    + "This is to ensure compatibility with GraalVM native-image. "
                                                    + "If you want to still use SeContainerInitializer, please configure "
                                                    + CONFIG_ALLOW_INITIALIZER + "=true to disable this exception.");
        }
        if (!config.getOptionalValue(CONFIG_INITIALIZER_NO_WARN, Boolean.class).orElse(false)) {
            LOGGER.warning("You are using SeContainerInitializer. This application will not work with GraalVM native-image."
                                   + " You can disable this warning by configuring " + CONFIG_INITIALIZER_NO_WARN + "=true.");
        }
        ContainerInstanceHolder.set(container);
    }

    @Override
    public SeContainerInitializer addBeanClasses(Class<?>... classes) {
        container.addBeanClasses(classes);
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(Class<?>... packageClasses) {
        container.addPackages(packageClasses);
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(boolean scanRecursively, Class<?>... packageClasses) {
        container.addPackages(scanRecursively, packageClasses);
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(Package... packages) {
        container.addPackages(packages);
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(boolean scanRecursively, Package... packages) {
        container.addPackages(scanRecursively, packages);
        return this;
    }

    @Override
    public SeContainerInitializer addExtensions(Extension... extensions) {
        container.addExtensions(extensions);
        return this;
    }

    @Override
    public SeContainerInitializer addExtensions(Class<? extends Extension>... extensions) {
        container.addExtensions(extensions);
        return this;
    }

    @Override
    public SeContainerInitializer enableInterceptors(Class<?>... interceptorClasses) {
        container.enableDecorators(interceptorClasses);
        return this;
    }

    @Override
    public SeContainerInitializer enableDecorators(Class<?>... decoratorClasses) {
        container.enableDecorators(decoratorClasses);
        return this;
    }

    @Override
    public SeContainerInitializer selectAlternatives(Class<?>... alternativeClasses) {
        container.selectAlternatives(alternativeClasses);
        return this;
    }

    @Override
    public SeContainerInitializer selectAlternativeStereotypes(Class<? extends Annotation>... alternativeStereotypeClasses) {
        container.selectAlternativeStereotypes(alternativeStereotypeClasses);
        return this;
    }

    @Override
    public SeContainerInitializer addProperty(String key, Object value) {
        container.addProperty(key, value);
        return this;
    }

    @Override
    public SeContainerInitializer setProperties(Map<String, Object> properties) {
        container.setProperties(properties);
        return this;
    }

    @Override
    public SeContainerInitializer disableDiscovery() {
        container.disableDiscovery();
        return this;
    }

    @Override
    public SeContainerInitializer setClassLoader(ClassLoader classLoader) {
        container.setClassLoader(classLoader);
        return this;
    }

    @Override
    public SeContainer initialize() {
        container.initInContext();
        return container.start();
    }
}
