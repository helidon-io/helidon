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
package io.helidon.docs.includes.metrics;

import io.helidon.microprofile.metrics.RegistryFactory;

import jakarta.inject.Inject;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryScope;

@SuppressWarnings("ALL")
class MetricsSharedSnippets {

    class Snippet1 {
        // tag::snippet_1[]
        class Example {

            @Inject
            private MetricRegistry applicationRegistry;
        }
        // end::snippet_1[]
    }

    class Snippet2 {
        // tag::snippet_2[]
        class Example {

            @RegistryScope(scope = "myCustomScope")
            @Inject
            private MetricRegistry myCustomRegistry;
        }
        // end::snippet_2[]
    }

    class Snippet3 {
        // tag::snippet_3[]
        class InjectExample {

            @Inject
            private RegistryFactory registryFactory;

            private MetricRegistry findRegistry(String scope) {
                return registryFactory.getRegistry(scope);
            }
        }
        // end::snippet_3[]
    }

    class Snippet4 {
        // tag::snippet_4[]
        class Example {

            private MetricRegistry findRegistry(String scope) {
                return RegistryFactory.getInstance().getRegistry(scope);
            }
        }
        // end::snippet_4[]
    }

}
