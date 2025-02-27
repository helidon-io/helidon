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
package io.helidon.docs.se.inject;

import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;

/**
 * An example that illustrates {@link Service.RunLevel}
 */
class RunLevelExample {

    // tag::snippet_1[]
    @Service.RunLevel(1)
    @Service.Singleton
    class Level1 {

        @Service.PostConstruct
        void onCreate() {
            System.out.println("level1 created");
        }
    }

    @Service.RunLevel(2)
    @Service.Singleton
    class Level2 {

        @Service.PostConstruct
        void onCreate() {
            System.out.println("level2 created");
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    public static void main(String[] args) {
        ServiceRegistryManager registryManager = ServiceRegistryManager.start();
        registryManager.shutdown();
    }
    // end::snippet_2[]
}
