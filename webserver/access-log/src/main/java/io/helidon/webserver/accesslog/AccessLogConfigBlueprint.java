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

package io.helidon.webserver.accesslog;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * Configuration of access log feature.
 */
@Prototype.Blueprint(decorator = AccessLogConfigSupport.BuilderDecorator.class)
@Prototype.Configured(value = AccessLogFeature.ACCESS_LOG_ID, root = false)
@Prototype.CustomMethods(AccessLogConfigSupport.CustomMethods.class)
@Prototype.Provides(ServerFeatureProvider.class)
interface AccessLogConfigBlueprint extends Prototype.Factory<AccessLogFeature> {
    /**
     * Common log format, see {@link io.helidon.webserver.accesslog.AccessLogConfig.Builder#commonLogFormat()}.
     */
    List<AccessLogEntry> COMMON_FORMAT = List.of(
            HostLogEntry.create(),
            UserIdLogEntry.create(),
            UserLogEntry.create(),
            TimestampLogEntry.create(),
            RequestLineLogEntry.create(),
            StatusLogEntry.create(),
            SizeLogEntry.create()
    );

    /**
     * Helidon log format, see {@link io.helidon.webserver.accesslog.AccessLogConfig.Builder#defaultLogFormat()}.
     */
    List<AccessLogEntry> HELIDON_FORMAT = List.of(
            HostLogEntry.create(),
            UserLogEntry.create(),
            TimestampLogEntry.create(),
            RequestLineLogEntry.create(),
            StatusLogEntry.create(),
            SizeLogEntry.create(),
            TimeTakenLogEntry.create()
    );

    /**
     * Configured log entries.
     * If both entries and {@link #format()} are defined, {@link #format()} is used.
     *
     * @return list of access log entries
     */
    @Option.Singular("entry")
    List<AccessLogEntry> entries();

    /**
     * Configure an alternative clock to use, such as {@link java.time.Clock#fixed(java.time.Instant, java.time.ZoneId)}.
     * Defaults to {@link java.time.Clock#systemDefaultZone()}.
     *
     * @return clock to use to get current times
     */
    @Option.DefaultCode("@java.time.Clock@.systemDefaultZone()")
    Clock clock();

    /**
     * Name of the logger used to obtain access log logger from {@link System#getLogger(String)}.
     * Defaults to {@value AccessLogFeature#DEFAULT_LOGGER_NAME}.
     *
     * @return name of the logger to use
     */
    @Option.Configured
    @Option.Default(AccessLogFeature.DEFAULT_LOGGER_NAME)
    String loggerName();

    /**
     * Weight of the access log feature. We need to log access for anything happening on the server, so weight is high:
     * {@value io.helidon.webserver.accesslog.AccessLogFeature#WEIGHT}.
     *
     * @return weight of the feature
     */
    @Option.DefaultDouble(AccessLogFeature.WEIGHT)
    @Option.Configured
    double weight();

    /**
     * List of sockets to register this feature on. If empty, it would get registered on all sockets.
     * The logger used will have the expected logger with a suffix of the socket name.
     *
     * @return socket names to register on, defaults to empty (all available sockets)
     */
    @Option.Configured
    Set<String> sockets();

    /**
     * Name of this instance.
     *
     * @return instance name
     */
    @Option.Default(AccessLogFeature.ACCESS_LOG_ID)
    String name();

    /**
     * The format for log entries (similar to the Apache {@code LogFormat}).
     * <table class="config">
     *     <caption>Log format elements</caption>
     *     <tr>
     *         <td>%h</td>
     *         <td>IP address of the remote host</td>
     *         <td>{@link HostLogEntry}</td>
     *     </tr>
     *     <tr>
     *         <td>%l</td>
     *         <td>The client identity. This is always undefined in Helidon.</td>
     *         <td>{@link UserIdLogEntry}</td>
     *     </tr>
     *     <tr>
     *         <td>%u</td>
     *         <td>User ID as asserted by Helidon Security.</td>
     *         <td>{@link UserLogEntry}</td>
     *     </tr>
     *     <tr>
     *         <td>%t</td>
     *         <td>The timestamp</td>
     *         <td>{@link TimestampLogEntry}</td>
     *     </tr>
     *     <tr>
     *         <td>%r</td>
     *         <td>The request line ({@code "GET /favicon.ico HTTP/1.0"})</td>
     *         <td>{@link RequestLineLogEntry}</td>
     *     </tr>
     *     <tr>
     *         <td>%s</td>
     *         <td>The status code returned to the client</td>
     *         <td>{@link StatusLogEntry}</td>
     *     </tr>
     *     <tr>
     *         <td>%b</td>
     *         <td>The entity size in bytes</td>
     *         <td>{@link SizeLogEntry}</td>
     *     </tr>
     *     <tr>
     *         <td>%D</td>
     *         <td>The time taken in microseconds (start of request until last byte written)</td>
     *         <td>{@link TimeTakenLogEntry}</td>
     *     </tr>
     *     <tr>
     *         <td>%T</td>
     *         <td>The time taken in seconds (start of request until last byte written), integer</td>
     *         <td>{@link TimeTakenLogEntry}</td>
     *     </tr>
     *     <tr>
     *         <td>%{header-name}i</td>
     *         <td>Value of header {@code header-name}</td>
     *         <td>{@link HeaderLogEntry}</td>
     *     </tr>
     * </table>
     *
     * @return format string, such as {@code %h %l %u %t %r %b %{Referer}i}
     */
    @Option.Configured
    Optional<String> format();

    /**
     * Whether this feature will be enabled.
     *
     * @return whether enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();
}
