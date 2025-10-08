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

package io.helidon.validation.tests.validation;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.service.registry.Service;
import io.helidon.validation.Check;

@Service.Singleton
public class ValidatedService {
    @Service.Inject
    @Check.NotNull
    Config config;

    @Check.String.NotBlank
    String process(@Check.Valid @Check.NotNull ValidatedType type) {
        if (type.second() == 42) {
            return "Good";
        }
        if (type.second() == 43) {
            return "";
        }
        return "Bad";
    }

    // TODO ignored valid now that Size was added
    void process(@Check.Collection.Size(2) List<@Check.Valid ValidatedType> list) {
    }
}
