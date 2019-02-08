/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.server;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.ext.Provider;

/**
 * Extension to gather JAX-RS application or JAX-RS resource classes
 * if no application is present.
 */
public class ServerCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(ServerCdiExtension.class.getName());

    private final List<Class<?>> resourceClasses = new LinkedList<>();
    private final List<Class<? extends Application>> applications = new LinkedList<>();
    private final List<Class<?>> providerClasses = new LinkedList<>();

    /**
     * Gather Application or resource classes to start.
     *
     * @param pit injection target
     * @param <T> any type
     */
    @SuppressWarnings("unchecked")
    public <T> void gatherJaxRsResources(@Observes ProcessInjectionTarget<T> pit) {
        AnnotatedType<T> at = pit.getAnnotatedType();

        if (!at.isAnnotationPresent(ApplicationScoped.class)
                && !at.isAnnotationPresent(RequestScoped.class)
                && !at.isAnnotationPresent(Dependent.class)) {
            return;
        }

        // class is annotated, let's see its type
        Class<?> theClass = at.getJavaClass();

        if (at.isAnnotationPresent(Provider.class)) {
            this.providerClasses.add(theClass);
            return;
        }

        if (Application.class.isAssignableFrom(theClass)) {
            if (at.isAnnotationPresent(ApplicationScoped.class)) {
                this.applications.add((Class<? extends Application>) theClass);
            } else {
                LOGGER.warning("Found an application with RequestScoped or Dependent annotation. This application is ignored: "
                                       + theClass.getName());
            }
        } else {
            // still may be a jax-rs resource (with no application attached)
            if (at.isAnnotationPresent(Path.class)) {
                this.resourceClasses.add(theClass);
            }
        }

    }

    List<Class<? extends Application>> getApplications() {
        return applications;
    }

    List<Class<?>> getResourceClasses() {
        return resourceClasses;
    }

    List<Class<?>> getProviderClasses() {
        return providerClasses;
    }
}
