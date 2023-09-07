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

package io.helidon.config.tests.config.metadata.meta.api;

import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.config.metadata.ConfiguredValue;

@Configured(description = "builder")
class MyBuilder extends AbstractBuilder<MyBuilder, MyTarget> {
    @Override
    public MyTarget build() {
        return new MyTarget();
    }

    @ConfiguredOption(description = "message description", value = "message")
    MyBuilder message(String message) {
        return this;
    }

    @ConfiguredOption(description = "type description",
                      value = "42",
                      allowedValues = {
                              @ConfiguredValue(value = "42", description = "answer"),
                              @ConfiguredValue(value = "0", description = "no answer")
                      })
    MyBuilder type(int type) {
        return this;
    }

    // this is not a configured option
    MyBuilder ignored(String ignored) {
        return this;
    }

    // this is also not a configured option
    @ConfiguredOption(configured = false)
    MyBuilder ignoredToo(String ignored) {
        return this;
    }
}
