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
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInfo;

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
                case HINT -> Level.TRACE;
            };
            logger.log(level, error.getSeverity() + " " + error.getSource() + ": " + error.getMessage());
        }

        if (errors.hasFatal() || failOnWarning && errors.hasWarning()) {
            throw new CodegenException("Application validation failed, see log output for details.");
        }
    }

    private void validate(WrappedServices services, Errors.Collector collector) {
        // check all singletons, that they only contain injection points that are singletons, or have a supplier
        List<ServiceInfo> requestScopedServices = services.all(Lookup.builder()
                                                                       .addScope(Service.PerRequest.TYPE)
                                                                       .build());
        Set<ResolvedType> requestScopedContracts = new HashSet<>();
        Map<ResolvedType, Set<TypeName>> requestScopedByContracts = new HashMap<>();

        for (ServiceInfo requestScoped : requestScopedServices) {
            TypeName serviceType = requestScoped.serviceType();
            Set<ResolvedType> contracts = requestScoped.contracts();

            ResolvedType resolvedServiceType = ResolvedType.create(serviceType);
            requestScopedContracts.add(resolvedServiceType);
            requestScopedByContracts.computeIfAbsent(resolvedServiceType, k -> new HashSet<>())
                    .add(serviceType);

            requestScopedContracts.addAll(contracts);
            for (ResolvedType contract : contracts) {
                requestScopedByContracts.computeIfAbsent(contract, k -> new HashSet<>())
                        .add(serviceType);
            }
        }

        List<ServiceInfo> singletons = services.all(Lookup.builder()
                                                                  .addScope(Service.Singleton.TYPE)
                                                                  .build());

        boolean requestScopeHinted = false;

        for (ServiceInfo singleton : singletons) {
            for (Dependency dependency : singleton.dependencies()) {
                ResolvedType contract = ResolvedType.create(dependency.contract());
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
