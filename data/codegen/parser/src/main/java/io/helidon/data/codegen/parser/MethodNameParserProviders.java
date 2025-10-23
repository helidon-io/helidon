/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.codegen.parser;

import java.util.List;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.data.codegen.parser.spi.MethodNameParserProvider;

// Service loader for MethodNameParserProvider
// Factory method is part of MethodNameParser interface and this shall not be visible.
class MethodNameParserProviders {

    private static final List<MethodNameParserProvider> PROVIDERS = HelidonServiceLoader.builder(ServiceLoader.load(
                    MethodNameParserProvider.class))
            // Default Helidon specific method name parser
            .addService(new MethodNameParserProvider() {
                @Override
                public MethodNameParser create() {
                    return MethodNameParserImpl.create();
                }

                // Lesser priority than default weight to allow override from classpath
                @Override
                public double weight() {
                    return 10.0;
                }
            })
            .build()
            .asList();

    private MethodNameParserProviders() {
        throw new UnsupportedOperationException("No instances of MethodNameParserProviders are allowed");
    }

    static List<MethodNameParserProvider> list() {
        return PROVIDERS;
    }

}
