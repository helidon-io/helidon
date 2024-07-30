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
package io.helidon.docs.mp.config;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@SuppressWarnings("ALL")
class IntroductionSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        Config config = ConfigProvider.getConfig();
        config.getOptionalValue("app.greeting", String.class).orElse("Hello");
        // end::snippet_1[]
    }

    class Snippet2 {

        class GreetingProvider {

            String message;

            // tag::snippet_2[]
            @Inject
            public GreetingProvider(
                    @ConfigProperty(name = "app.greeting",
                                    defaultValue = "Hello") String message) {
                this.message = message;
            }
            // end::snippet_2[]
        }
    }

}
