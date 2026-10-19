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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Limits for WebServer's internal error diagnostics, shared by all listeners of a server.
 * Application logs, access logs and ordinary HTTP errors are unaffected.
 */
@Prototype.Blueprint(decorator = ErrorLogConfigSupport.BuilderDecorator.class)
@Prototype.Configured
interface ErrorLogConfigBlueprint {
    /**
     * Whether to limit internal error logging. Disabling restores unrestricted logging.
     *
     * @return whether enabled, defaults to {@code true}
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Collection window for detailed logs and retained error identities. Must be positive and representable in nanoseconds.
     * Detailed-log allowances reset when the window ends, independently of suppression reports.
     *
     * @return collection window, defaults to one hour
     */
    @Option.Configured
    @Option.Default("PT1H")
    Duration window();

    /**
     * Interval for suppression reports. Must be positive and representable in nanoseconds.
     * Each report contains counts since the previous report, even if traffic stops, without resetting detailed-log allowances.
     * Pending counts are also reported once at shutdown.
     *
     * @return reporting interval, defaults to ten minutes
     */
    @Option.Configured
    @Option.Default("PT10M")
    Duration reportInterval();

    /**
     * Maximum detailed logs for one error identity per window. Must be positive.
     * Identity uses the logging site, exception types and bounded stack and cause information;
     * exception messages and request data are excluded.
     *
     * @return per-error limit, defaults to {@code 1}
     */
    @Option.Configured
    @Option.DefaultInt(1)
    int maxPerError();

    /**
     * Maximum detailed logs across all error identities and listeners per window. Must be positive.
     * Suppression reports are additional, bounded to one aggregate record per reporting interval plus shutdown.
     *
     * @return overall limit, defaults to {@code 100}
     */
    @Option.Configured
    @Option.DefaultInt(100)
    int maxTotal();

    /**
     * Maximum error identities retained per window. Must be positive.
     * Additional identities are suppressed until the next window. Only fixed-size fingerprints and counters
     * are retained, never exceptions, messages, requests or stack traces.
     *
     * @return retained identity limit, defaults to {@code 100}
     */
    @Option.Configured
    @Option.DefaultInt(100)
    int maxErrors();
}
