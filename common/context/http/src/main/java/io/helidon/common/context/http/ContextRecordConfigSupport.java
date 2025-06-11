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

package io.helidon.common.context.http;

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;

final class ContextRecordConfigSupport {
    private ContextRecordConfigSupport() {
    }

    static final class RecordCustomMethods {
        private RecordCustomMethods() {
        }

        /*
          Factory method to read HeaderName directly from configuration
        */
        @Prototype.FactoryMethod
        static HeaderName createHeader(Config config) {
            return config.asString()
                    .map(HeaderNames::create)
                    .orElseThrow(() -> new IllegalStateException("Config node did not contain a header name"));
        }
    }
}
