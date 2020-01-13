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
    }

    @Override
    public SeContainerInitializer addBeanClasses(Class<?>... classes) {
        return container.addBeanClasses(classes);
    }

    @Override
    public SeContainerInitializer addPackages(Class<?>... packageClasses) {
        return container.addPackages(packageClasses);
    }

    @Override
    public SeContainerInitializer addPackages(boolean scanRecursively, Class<?>... packageClasses) {
        return container.addPackages(scanRecursively, packageClasses);
    }

    @Override
    public SeContainerInitializer addPackages(Package... packages) {
        return container.addPackages(packages);
    }

    @Override
    public SeContainerInitializer addPackages(boolean scanRecursively, Package... packages) {
        return container.addPackages(scanRecursively, packages);
    }

    @Override
    public SeContainerInitializer addExtensions(Extension... extensions) {
        return container.addExtensions(extensions);
    }

    @Override
    public SeContainerInitializer addExtensions(Class<? extends Extension>... extensions) {
        return container.addExtensions(extensions);
    }

    @Override
    public SeContainerInitializer enableInterceptors(Class<?>... interceptorClasses) {
        return container.enableDecorators(interceptorClasses);
    }

    @Override
    public SeContainerInitializer enableDecorators(Class<?>... decoratorClasses) {
        return container.enableDecorators(decoratorClasses);
    }

    @Override
    public SeContainerInitializer selectAlternatives(Class<?>... alternativeClasses) {
        return container.selectAlternatives(alternativeClasses);
    }

    @Override
    public SeContainerInitializer selectAlternativeStereotypes(Class<? extends Annotation>... alternativeStereotypeClasses) {
        return container.selectAlternativeStereotypes(alternativeStereotypeClasses);
    }

    @Override
    public SeContainerInitializer addProperty(String key, Object value) {
        return container.addProperty(key, value);
    }

    @Override
    public SeContainerInitializer setProperties(Map<String, Object> properties) {
        return container.setProperties(properties);
    }

    @Override
    public SeContainerInitializer disableDiscovery() {
        return container.disableDiscovery();
    }

    @Override
    public SeContainerInitializer setClassLoader(ClassLoader classLoader) {
        return container.setClassLoader(classLoader);
    }

    @Override
    public SeContainer initialize() {
        container.initInContext();
        return container.start();
    }
}
