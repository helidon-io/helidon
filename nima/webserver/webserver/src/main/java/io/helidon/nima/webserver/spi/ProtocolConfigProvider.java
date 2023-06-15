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

package io.helidon.nima.webserver.spi;

import io.helidon.common.config.ConfiguredProvider;

/**
 * Provider of protocol configuration. As we use protocols from multiple places (connection selectors, upgrade from HTTP/1),
 * we have a single abstraction of their configuration.
 *
 * @param <T> type of configuration supported by this provider
 */
public interface ProtocolConfigProvider<T extends ProtocolConfig> extends ConfiguredProvider<T> {

}
