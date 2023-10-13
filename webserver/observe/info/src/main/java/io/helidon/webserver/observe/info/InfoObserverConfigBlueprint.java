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

package io.helidon.webserver.observe.info;

import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.observe.ObserverConfigBase;
import io.helidon.webserver.observe.spi.ObserveProvider;

/**
 * Info Observer configuration.
 */
@Prototype.Blueprint
@Prototype.Configured
@Prototype.Provides(ObserveProvider.class)
interface InfoObserverConfigBlueprint extends ObserverConfigBase, Prototype.Factory<InfoObserver> {
    @Option.Configured
    @Option.Default("info")
    String endpoint();

    @Override
    @Option.Default("info")
    String name();

    /**
     * Values to be exposed using this observability endpoint.
     *
     * @return value map
     */
    @Option.Configured
    @Option.Singular
    Map<String, String> values();
}
