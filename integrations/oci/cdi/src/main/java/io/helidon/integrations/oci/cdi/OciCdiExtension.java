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

package io.helidon.integrations.oci.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.inject.Named;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.integrations.oci.connect.OciRestApi;
import io.helidon.integrations.oci.connect.spi.InjectionProvider;
import io.helidon.microprofile.cdi.RuntimeStart;

public class OciCdiExtension implements Extension {
    private final Set<Type> supportedTypes = new HashSet<>();
    private final Set<String> requiredNames = new HashSet<>();
    private final List<InjectionProvider> providers;

    private Config config;

    public OciCdiExtension() {
        providers = HelidonServiceLoader
                .builder(ServiceLoader.load(InjectionProvider.class))
                .build()
                .asList();

        for (InjectionProvider provider : providers) {
            provider.injectables()
                    .stream()
                    .map(InjectionProvider.InjectionType::injectedType)
                    .forEach(supportedTypes::add);
        }
    }

    private void configure(@Observes @RuntimeStart Config config) {
        this.config = config.get("oci");
    }

    /**
     * Add internal qualifier.
     *
     * @param event CDI event
     */
    void updateInjectionPoints(@Observes ProcessInjectionPoint<?, ?> event) {
        InjectionPoint injectionPoint = event.getInjectionPoint();
        Annotated annotated = injectionPoint.getAnnotated();

        Type type = injectionPoint.getType();
        if (supportedTypes.contains(type)) {
            Named name = annotated.getAnnotation(Named.class);

            OciInternal internal = OciInternal.Literal
                    .create((name == null ? "" : name.value()));

            event.configureInjectionPoint()
                    .addQualifier(internal);
        }
    }

    /**
     * Collect injection points that are valid.
     *
     * @param event CDI event
     */
    void processInjectionPointsFromEnabledBeans(@Observes ProcessBean<?> event) {
        for (InjectionPoint injectionPoint : event.getBean().getInjectionPoints()) {
            Set<Annotation> qualifiers = injectionPoint.getQualifiers();
            for (Annotation qualifier : qualifiers) {
                if (qualifier.annotationType().equals(OciInternal.class)) {
                    OciInternal value = (OciInternal) qualifier;

                    requiredNames.add(value.value());
                    break;
                }
            }
        }
    }

    void registerProducers(@Observes AfterBeanDiscovery abd) {
        if (requiredNames.contains("")) {
            requiredNames.add("default");
        } else if (requiredNames.contains("default")) {
            requiredNames.add("");
        }
        boolean addDefault = true;

        for (String name : requiredNames) {
            Named named = NamedLiteral.of(name);

            if (name.isEmpty() || name.equals("default")) {
                if (addDefault) {
                    // default
                    abd.addBean(new QualifiedBean<>(OciCdiExtension.class,
                                                    OciRestApi.class,
                                                    () -> {
                                                        if (config.get("default").exists()) {
                                                            return OciRestApi.create(config.get("default"));
                                                        } else {
                                                            return OciRestApi.create(config);
                                                        }
                                                    }));

                    abd.addBean(new QualifiedBean<>(OciCdiExtension.class,
                                                    OciRestApi.class,
                                                    Set.of(NamedLiteral.of(""), NamedLiteral.of("default")),
                                                    () -> {
                                                        if (config.get("default").exists()) {
                                                            return OciRestApi.create(config.get("default"));
                                                        } else {
                                                            return OciRestApi.create(config);
                                                        }
                                                    }));
                    addDefault = false;
                }
            } else {
                // named
                abd.addBean(new QualifiedBean<>(OciRestApi.class,
                                                OciRestApi.class,
                                                Set.of(named),
                                                () -> {
                                                    System.out.println("Creating OCI REST API from config: " + config.get(name)
                                                            .key());
                                                    return OciRestApi.create(config.get(name));
                                                }
                ));
            }
        }

        // now we need to add the full combination of names and types
        for (String name : requiredNames) {
            OciInternal.Literal qualifier = OciInternal.Literal.create(name);
            Named named = NamedLiteral.of(name);

            // for each name
            for (InjectionProvider provider : providers) {
                // for each provider
                for (InjectionProvider.InjectionType<?> injectable : provider.injectables()) {
                    // for each type
                    abd.addBean(new QualifiedBean<>(OciCdiExtension.class,
                                                    (Class<Object>) injectable.injectedType(),
                                                    Set.of(named, qualifier),
                                                    () -> {
                                                        OciRestApi restApi = CDI.current()
                                                                .select(OciRestApi.class, named)
                                                                .get();

                                                        return injectable.createInstance(restApi, producerConfig(name));
                                                    }));
                }
            }
        }
    }

    private Config producerConfig(String name) {
        if (name.isEmpty() || name.equals("default")) {
            if (config.get("default").exists()) {
                return config.get("default");
            }
            return config;
        }
        return config.get(name);
    }
}
