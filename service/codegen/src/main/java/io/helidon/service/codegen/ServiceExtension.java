/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.codegen;

import java.util.Collection;

import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

class ServiceExtension implements RegistryCodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(ServiceExtension.class);

    private final RegistryCodegenContext ctx;

    ServiceExtension(RegistryCodegenContext codegenContext) {
        this.ctx = codegenContext;
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        Collection<TypeInfo> descriptorsRequired = roundContext.types();

        for (TypeInfo typeInfo : descriptorsRequired) {
            generateDescriptor(descriptorsRequired, typeInfo);
        }
    }

    private void generateDescriptor(Collection<TypeInfo> services,
                                    TypeInfo typeInfo) {
        GenerateServiceDescriptor.generate(GENERATOR, ctx, services, typeInfo);
    }
}
