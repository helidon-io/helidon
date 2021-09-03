/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.common.spi;

import io.helidon.config.Config;
import io.helidon.media.common.MediaSupport;

/**
 * Java service loader interface.
 */
public interface MediaSupportProvider {

    /**
     * Config key expected under {@code media-support.services.config}.
     *
     * @return name of the configuration node of this service
     */
    default String configKey() {
        return "unconfigured";
    }

    /**
     * Create a new service instance based on configuration.
     *
     * @param config configuration of this service, never null
     * @return a new media service instance
     */
    MediaSupport create(Config config);

}
