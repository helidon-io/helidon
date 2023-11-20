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
package io.helidon.webserver.cors;

import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;

class CorsConfigSupport {

    static class BuilderDecorator implements Prototype.BuilderDecorator<CorsConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(CorsConfig.BuilderBase<?, ?> builder) {
            // If enabled has been explicitly set (perhaps directly, perhaps by config) then use that value.
            // Otherwise:
            //    If there is explicit CORS config then set enabled to true.
            //    Otherwise, set enabled to false.
            if (builder.enabled().isPresent()) {
                return;
            }
            Optional<Config> config = builder.config();
            builder.enabled(config.isPresent() && config.get().exists());
        }
    }
}
