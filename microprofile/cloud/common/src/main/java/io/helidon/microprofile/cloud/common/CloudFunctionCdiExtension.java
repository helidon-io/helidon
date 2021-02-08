/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.cloud.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.util.AnnotationLiteral;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * MicroProfile Cloud Function CDI Extension.
 */
public class CloudFunctionCdiExtension implements Extension {

    private static final Logger LOGGER = Logger.getLogger(CloudFunctionCdiExtension.class.getName());

    private static final String CLOUD_FUNCTION = "helidon.cloud.function.value";

    private Map<String, Class<?>> functionClasses = new HashMap<>();

    private void registerCloudFunctionHolder(@Observes AfterBeanDiscovery after, BeanManager manager) {
        after.addBean().types(CloudFunctionHolder.class)
        .qualifiers(new AnnotationLiteral<Default>() {}, new AnnotationLiteral<Any>() {})
        .scope(ApplicationScoped.class).name(CloudFunctionHolder.class.getName()).beanClass(CloudFunctionHolder.class)
        .createWith(creationalContext -> {
            Optional<String> configFunction = ConfigProvider.getConfig().getOptionalValue(CLOUD_FUNCTION, String.class);
            Class<?> functionClass = null;
            if (configFunction.isPresent()) {
                LOGGER.fine(() -> CLOUD_FUNCTION + " = " + configFunction.get());
                functionClass = functionClasses.get(configFunction.get());
            } else {
                functionClass = functionClasses.get("");
            }
            Object instance = functionClass == null ? null : manager.createInstance().select(functionClass).get();
            return new CloudFunctionHolder(Optional.ofNullable(instance));
        });
    }

    private void registerCloudFunction(@Observes @WithAnnotations(CloudFunction.class) ProcessAnnotatedType<?> patEvent) {
        Class<?> javaClass = patEvent.getAnnotatedType().getJavaClass();
        LOGGER.fine(() -> "@CloudFunction found in " + javaClass);
        functionClasses.put(javaClass.getAnnotation(CloudFunction.class).value(), javaClass);
    }
}
