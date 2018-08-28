/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.abac.scope;

import io.helidon.config.Config;
import io.helidon.security.abac.AbacProvider;
import io.helidon.security.abac.AbacValidatorConfig;
import io.helidon.security.abac.spi.AbacValidator;
import io.helidon.security.abac.spi.AbacValidatorService;

/**
 * A service to provide {@link ScopeValidator} to {@link AbacProvider}.
 */
public class ScopeValidatorService implements AbacValidatorService {
    @Override
    public String configKey() {
        return "scope-validator";
    }

    @Override
    public AbacValidator<? extends AbacValidatorConfig> instantiate(Config config) {
        return ScopeValidator.from(config);
    }
}
