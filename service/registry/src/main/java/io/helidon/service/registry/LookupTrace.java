/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.registry;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

final class LookupTrace {
    private static final System.Logger LOGGER = System.getLogger(LookupTrace.class.getName());

    private LookupTrace() {
    }

    static void traceLookup(Lookup lookup, String message, Object... args) {
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, prefix(lookup) + message, args);
        }
    }

    static <T> void traceLookupInstance(Lookup lookup,
                                        ServiceManager<T> manager,
                                        List<ServiceInstance<T>> instances) {
        if (LOGGER.isLoggable(TRACE)) {
            String serviceType = manager.descriptor().serviceType().fqName();
            if (instances.isEmpty()) {
                LOGGER.log(TRACE, prefix(lookup) + "service {0} added 0 instances",
                           serviceType);
                return;
            }

            for (ServiceInstance<T> instance : instances) {
                LOGGER.log(TRACE, prefix(lookup) + "service {0} adding instance: {1}",
                           serviceType,
                           instanceInfo(instance));
            }
        }
    }

    static <T> void traceLookupInstances(Lookup lookup,
                                         List<ServiceInstance<T>> instances) {
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, prefix(lookup) + "sorted instances by weight and service:{0}",
                       instances.stream()
                               .map(it -> it.serviceType().fqName() + " [" + it.weight() + "]")
                               .collect(Collectors.joining(", ")));
        }
    }

    static void traceLookup(Lookup lookup, String message, List<ServiceInfo> services) {
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, "{0}matching service providers {1}: {2}",
                       prefix(lookup),
                       message,
                       services.stream()
                               .map(ServiceInfo::serviceType)
                               .map(TypeName::fqName)
                               .collect(Collectors.toUnmodifiableList()));
        }
    }

    private static String instanceInfo(ServiceInstance<?> instance) {
        double weight = instance.weight();
        TypeName scope = instance.scope();
        Set<Qualifier> qualifiers = instance.qualifiers();
        Object o = instance.get();

        return "weight(" + weight + "), "
                + "scope(" + scope(scope) + "), "
                + "qualifiers(" + qualifiers.stream()
                .map(Qualifier::typeName)
                .map(TypeName::fqName)
                .collect(Collectors.joining(", ")) + "), "
                + "instance(" + o + ")";
    }

    private static String scope(TypeName typeName) {
        if (typeName.packageName().startsWith("io.helidon.service.registry")) {
            return typeName.classNameWithEnclosingNames();
        }
        return typeName.fqName();
    }

    private static String prefix(Lookup lookup) {
        return "[" + System.identityHashCode(lookup) + "] ";
    }
}
