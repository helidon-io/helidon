/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.security.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

abstract class InstantValidator extends OptionalValidator {
    private final Instant now;
    private final Duration allowedTimeSkew;

    InstantValidator(BaseBuilder<?, ?> builder) {
        super(builder);
        this.now = builder.now;
        this.allowedTimeSkew = builder.allowedTimeSkew;
    }

    Instant latest() {
        return instant().plus(allowedTimeSkew);
    }

    Instant latest(Instant now) {
        return now.plus(allowedTimeSkew);
    }

    Instant earliest() {
        return instant().minus(allowedTimeSkew);
    }

    Instant earliest(Instant now) {
        return now.minus(allowedTimeSkew);
    }

    Instant instant() {
        return now == null ? Instant.now() : now;
    }

    abstract static class BaseBuilder<B extends BaseBuilder<B, T>, T> extends OptionalValidator.BaseBuilder<B, T> {

        private Instant now = null;
        private Duration allowedTimeSkew = Duration.ofSeconds(5);

        BaseBuilder() {
        }

        /**
         * Specific "current" time to validate time claim against.
         * If not set, {@link Instant#now()} is used for every validation again.
         *
         * @param now specific current time
         * @return updated builder instance
         */
        public B now(Instant now) {
            this.now = Objects.requireNonNull(now);
            return me();
        }

        /**
         * Allowed time skew for time validation.
         * The default value is 5 seconds.
         *
         * @param allowedTimeSkew allowed time skew
         * @return updated builder instance
         */
        public B allowedTimeSkew(Duration allowedTimeSkew) {
            this.allowedTimeSkew = Objects.requireNonNull(allowedTimeSkew);
            return me();
        }
    }
}
