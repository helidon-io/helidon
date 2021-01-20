/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.microprofile.metrics;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.inject.Qualifier;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.Routing;

/**
 * Common behavior of CDI extensions for metrics.
 *
 */
class MetricsCdiExtensionBase implements Extension {

//    List<Class<? extends Annotation>> metricAnnotations;

    final Set<Class<?>> metricsAnnotatedClasses = new HashSet<>();
    final Set<Class<?>> metricsAnnotatedClassesProcessed = new HashSet<>();

    final Logger logger;

    MetricsCdiExtensionBase(Logger logger /* , List<Class<? extends Annotation>> metricAnnotations */) {
        this.logger = logger;
//        this.metricAnnotations = metricAnnotations;
    }

    /**
     * Returns the real class of this object, skipping proxies.
     *
     * @param object The object.
     * @return Its class.
     */
    static Class<?> getRealClass(Object object) {
        Class<?> result = object.getClass();
        while (result.isSynthetic()) {
            result = result.getSuperclass();
        }
        return result;
    }

    static Routing.Builder defaultRouting(ServerCdiExtension server) {
        return server.serverRoutingBuilder();
    }

    static Routing.Builder configuredRoutingBuilder(ServerCdiExtension server, Config config) {

        ConfigValue<String> routingNameConfig = config.get("routing").asString();
        Routing.Builder endpointRouting = defaultRouting(server);

        if (routingNameConfig.isPresent()) {
            String routingName = routingNameConfig.get();
            // support for overriding this back to default routing using config
            if (!"@default".equals(routingName)) {
                endpointRouting = server.serverNamedRoutingBuilder(routingName);
            }
        }

        return endpointRouting;
    }
}
