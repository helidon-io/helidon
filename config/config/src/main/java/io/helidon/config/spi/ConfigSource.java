/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.spi;

import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

/**
 * {@link Source} of configuration.
 *
 * A mutable source may either support a change subscription mechanism (e.g. the source is capable of notifications that
 * do not require external polling), or an explicit polling strategy that provides handling of change notifications
 * as supported by {@link io.helidon.config.spi.PollableSource}, or a target that can be directly watched, such as
 * supported by {@link io.helidon.config.spi.WatchableSource}.
 * Examples of :
 *  - a cloud service may provide notifications through its API that a node changed
 *  - a file source can be watched using a file watch polling strategy
 *  - a file source can be watched using a time based polling strategy
 * @see Config.Builder#sources(Supplier)
 * @see Config.Builder#sources(Supplier, Supplier)
 * @see Config.Builder#sources(Supplier, Supplier, Supplier)
 * @see Config.Builder#sources(java.util.List)
 * @see AbstractConfigSource
 * @see AbstractParsableConfigSource
 * @see ConfigSources ConfigSources - access built-in implementations.
 */
public interface ConfigSource extends Supplier<ConfigSource>, Source {
    @Override
    default ConfigSource get() {
        return this;
    }
}
