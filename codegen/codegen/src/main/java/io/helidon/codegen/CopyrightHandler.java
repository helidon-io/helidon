/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.codegen;

import java.util.ServiceLoader;

import io.helidon.codegen.spi.CopyrightProvider;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.TypeName;

/**
 * Handle copyright for generated types.
 */
final class CopyrightHandler {
    private static final CopyrightProvider PROVIDER =
            HelidonServiceLoader.builder(ServiceLoader.load(CopyrightProvider.class,
                                                            CopyrightHandler.class.getClassLoader()))
                    .addService(new DefaultProvider(), 0)
                    .build()
                    .iterator()
                    .next();

    private CopyrightHandler() {
    }

    /**
     * Provides copyright header to be added before package declaration.
     *
     * @param generator type of the generator (annotation processor)
     * @param trigger type of the class that caused this type to be generated
     * @param generatedType type that is going to be generated
     * @return copyright string (can be multiline)
     */
    static String copyright(TypeName generator, TypeName trigger, TypeName generatedType) {
        return PROVIDER.copyright(generator, trigger, generatedType);
    }

    private static final class DefaultProvider implements CopyrightProvider {
        @Override
        public String copyright(TypeName generator, TypeName trigger, TypeName generatedType) {
            return "// This is a generated file (powered by Helidon). " + "Do not edit or extend from this artifact as it is "
                    + "subject to change at any time!";
        }
    }
}
