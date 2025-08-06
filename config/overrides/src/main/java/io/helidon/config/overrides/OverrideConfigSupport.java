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

package io.helidon.config.overrides;

import java.util.regex.Pattern;

import io.helidon.builder.api.Prototype;

final class OverrideConfigSupport {
    private OverrideConfigSupport() {
    }

    static Pattern expressionToPattern(String expression) {
        return Pattern.compile(expression.replace("*", "\\w+").replace(".", "\\."));
    }

    static final class Decorator implements Prototype.BuilderDecorator<OverrideConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(OverrideConfig.BuilderBase<?, ?> target) {
            target.overrideExpressions()
                    .forEach((expression, value) -> {
                        var pattern = expressionToPattern(expression);
                        target.putOverridePattern(pattern, value);
                    });
        }
    }
}
