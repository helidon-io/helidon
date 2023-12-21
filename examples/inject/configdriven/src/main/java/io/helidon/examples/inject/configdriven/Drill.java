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

package io.helidon.examples.inject.configdriven;

import java.util.Objects;

import io.helidon.examples.inject.basics.Tool;
import io.helidon.inject.configdriven.service.ConfigDriven;
import io.helidon.inject.service.Injection;


@ConfigDriven(DrillConfigBlueprint.class)
class Drill implements Tool {

    private final DrillConfig cfg;

    @Injection.Inject
    Drill(DrillConfig cfg) {
        this.cfg = Objects.requireNonNull(cfg);
    }

    @Override
    public String name() {
        return cfg.name();
    }

    @Injection.PostConstruct
    @SuppressWarnings("unused")
    void init() {
        System.out.println(name() + "; initialized");
    }

}
