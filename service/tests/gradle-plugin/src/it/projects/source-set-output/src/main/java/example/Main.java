/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package example;

import io.helidon.service.registry.EmptyBinding;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;

@Service.GenerateBinding
public class Main {
    public static void main(String[] args) {
        var binding = ApplicationBinding.create();
        if (binding instanceof EmptyBinding) {
            throw new AssertionError("Annotation processor placeholder was not replaced");
        }
        var manager = ServiceRegistryManager.start(binding);
        try {
            System.out.println("SOURCE_SET:" + manager.registry().get(MainService.class).value());
        } finally {
            manager.shutdown();
        }
    }

    @Service.Singleton
    public static class MainService {
        public String value() {
            return "main";
        }
    }
}
