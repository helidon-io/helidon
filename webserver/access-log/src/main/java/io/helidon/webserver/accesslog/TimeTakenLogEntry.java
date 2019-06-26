/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.webserver.accesslog;

import java.util.concurrent.TimeUnit;

/**
 * Access log entry for time taken.
 */
public final class TimeTakenLogEntry extends AbstractLogEntry {
    private final TimeUnit unit;

    private TimeTakenLogEntry(Builder builder) {
        super(builder);

        this.unit = builder.unit;
    }

    /**
     * Create a new time taken access log entry measuring in microseconds.
     *
     * @return a new access log entry for time taken
     */
    public static TimeTakenLogEntry create() {
        return builder().build();
    }

    /**
     * Create a new fluent API builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder().defaults();
    }

    @Override
    protected String doApply(AccessLogContext context) {
        long req = context.requestNanoTime();
        long res = context.responseNanoTime();
        long diff = res - req;

        return String.valueOf(unit.convert(diff, TimeUnit.NANOSECONDS));
    }

    /**
     * A fluent API builder for {@link io.helidon.webserver.accesslog.TimeTakenLogEntry}.
     */
    public static final class Builder extends AbstractLogEntry.Builder<TimeTakenLogEntry, Builder> {
        private TimeUnit unit = TimeUnit.MICROSECONDS;

        private Builder() {
        }

        @Override
        public TimeTakenLogEntry build() {
            return new TimeTakenLogEntry(this);
        }

        /**
         * Configure the time unit to use. Defaults to {@link TimeUnit#MICROSECONDS}.
         * @param unit unit to use when writing time taken to Access log
         * @return updated builder instance
         */
        public Builder unit(TimeUnit unit) {
            this.unit = unit;
            return this;
        }

        private Builder defaults() {
            return super.sanitize(false);
        }
    }
}
