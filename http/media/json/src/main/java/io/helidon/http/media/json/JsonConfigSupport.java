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

package io.helidon.http.media.json;

import io.helidon.builder.api.Prototype;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.http.HttpMediaType;

final class JsonConfigSupport {
    private JsonConfigSupport() {
    }

    static class CustomMethods {
        private CustomMethods() {
        }

        @Prototype.ConfigFactoryMethod("contentType")
        static HttpMediaType createContentType(Config config) {
            return config.asString()
                    .as(HttpMediaType::create)
                    .get();
        }

        @Prototype.ConfigFactoryMethod("acceptedMediaTypes")
        static MediaType createAcceptedType(Config config) {
            return config.asString()
                    .as(MediaTypes::create)
                    .get();
        }
    }
}
