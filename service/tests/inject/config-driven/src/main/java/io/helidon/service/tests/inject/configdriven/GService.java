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

package io.helidon.service.tests.inject.configdriven;

import io.helidon.service.inject.api.Injection;

@Injection.CreateFor(GConfigBlueprint.class)
class GService implements TheContract {
    private final GConfig config;
    private final String name;

    @Injection.Inject
    GService(GConfig config, @Injection.CreateForName String name) {
        this.config = config;
        this.name = name;
    }

    @Override
    public String value() {
        return config.value();
    }

    @Override
    public String name() {
        return name;
    }
}
