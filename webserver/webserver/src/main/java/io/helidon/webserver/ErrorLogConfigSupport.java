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
package io.helidon.webserver;

import java.time.Duration;

import io.helidon.builder.api.Prototype;

final class ErrorLogConfigSupport {
    private ErrorLogConfigSupport() {
    }

    private static void validateDuration(Duration duration, String name) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Error log " + name + " must be positive");
        }
        try {
            duration.toNanos();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Error log " + name + " is too large", e);
        }
    }

    static final class BuilderDecorator implements Prototype.BuilderDecorator<ErrorLogConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(ErrorLogConfig.BuilderBase<?, ?> target) {
            validateDuration(target.window(), "window");
            validateDuration(target.reportInterval(), "report interval");
            if (target.maxPerError() < 1 || target.maxTotal() < 1 || target.maxErrors() < 1) {
                throw new IllegalArgumentException("Error log max-per-error, max-total and max-errors must be positive");
            }
        }
    }
}
