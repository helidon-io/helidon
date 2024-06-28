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

package io.helidon.service.maven.plugin;

import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenLogger;
import io.helidon.common.Errors;
import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Ip;
import io.helidon.service.inject.api.Lookup;

class ApplicationValidator {
    private final MavenCodegenContext scanContext;
    private final boolean failOnWarning;

    ApplicationValidator(MavenCodegenContext scanContext, boolean failOnWarning) {
        this.scanContext = scanContext;
        this.failOnWarning = failOnWarning;
    }

    void validate(WrappedServices services) {
        Errors.Collector collector = Errors.collector();

        validate(services, collector);

        Errors errors = collector.collect();
        CodegenLogger logger = scanContext.logger();
        for (Errors.ErrorMessage error : errors) {
            Level level = switch (error.getSeverity()) {
                case FATAL -> Level.ERROR;
                case WARN -> Level.WARNING;
                case HINT -> Level.INFO;
            };
            logger.log(level, error.getSeverity() + " " + error.getSource() + ": " + error.getMessage());
        }

        if (errors.hasFatal() || failOnWarning && errors.hasWarning()) {
            throw new CodegenException("Application validation failed, see log output for details.");
        }
    }

    private void validate(WrappedServices services, Errors.Collector collector) {
        // check all singletons, that they only contain injection points that are singletons, or have a supplier
        List<InjectServiceInfo> requestScopedServices = services.all(Lookup.builder()
                                                                             .addScope(Injection.RequestScope.TYPE)
                                                                             .build());
        Set<TypeName> requestScopedContracts = new HashSet<>();
        Map<TypeName, Set<TypeName>> requestScopedByContracts = new HashMap<>();

        for (InjectServiceInfo requestScoped : requestScopedServices) {
            TypeName serviceType = requestScoped.serviceType();
            Set<TypeName> contracts = requestScoped.contracts();

            requestScopedContracts.add(serviceType);
            requestScopedByContracts.computeIfAbsent(serviceType, k -> new HashSet<>())
                    .add(serviceType);

            requestScopedContracts.addAll(contracts);
            for (TypeName contract : contracts) {
                requestScopedByContracts.computeIfAbsent(contract, k -> new HashSet<>())
                        .add(serviceType);
            }
        }

        List<InjectServiceInfo> singletons = services.all(Lookup.builder()
                                                                  .addScope(Injection.Singleton.TYPE)
                                                                  .build());

        boolean requestScopeHinted = false;

        for (InjectServiceInfo singleton : singletons) {
            for (Ip dependency : singleton.dependencies()) {
                TypeName contract = dependency.contract();
                if (requestScopedContracts.contains(contract)) {
                    // this is an injection of request scope service into a singleton
                    if (dependency.typeName().isSupplier()) {
                        // this is correct
                        if (!requestScopeHinted) {
                            collector.hint(
                                    "Injection of request scoped service into a singleton (as a supplier). This is correct, "
                                            + "please "
                                            + "make sure you have appropriate request scope library on your module path.");
                            requestScopeHinted = true;
                        }
                    } else {
                        // this does not have to be an error, if the user decides the whole application has a request
                        // scope active and they implement their own request scope initialization
                        collector.warn("Injection of request scoped service into a singleton without a supplier. "
                                               + " Singleton: " + singleton.serviceType() + ", request scoped service(s): "
                                               + requestScopedByContracts.get(contract));
                    }
                }
            }
        }
    }
}
