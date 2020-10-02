/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Service that adds support for Access logging to WebServer.
 */
public final class AccessLogSupport implements Service {
    /**
     * Name of the {@link java.util.logging.Logger} used to log access log records.
     * The message logged contains all information, so the format should be modified
     * to only log the message.
     *
     * @see io.helidon.webserver.accesslog.AccessLogHandler
     */
    public static final String DEFAULT_LOGGER_NAME = "io.helidon.webserver.AccessLog";
    private static final Pattern HEADER_ENTRY_PATTERN = Pattern.compile("%\\{(.*?)}i");

    private final List<AccessLogEntry> logFormat;
    private final Logger logger;
    private final boolean enabled;
    private final Clock clock;

    private AccessLogSupport(Builder builder) {
        this.enabled = builder.enabled;
        this.logFormat = builder.entries;
        this.clock = builder.clock;
        this.logger = Logger.getLogger(builder.loggerName);
    }

    /**
     * Create Access log support with default configuration.
     *
     * @return a new access log support to be registered with WebServer routing
     */
    public static AccessLogSupport create() {
        return builder().build();
    }

    /**
     * Create Access log support configured from {@link Config}.
     *
     * @param config to configure a new access log support instance
     * @return a new access log support to be registered with WebServer routing
     */
    public static AccessLogSupport create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * A new fluent API builder to create Access log support instance.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void update(Routing.Rules rules) {
        if (enabled) {
            rules.any(this::handle);
        }
    }

    private void handle(ServerRequest req, ServerResponse res) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        long nanoNow = System.nanoTime();

        logFormat.forEach(entry -> entry.accept(req, res));

        res.whenSent().thenAccept(aResponse -> log(req, aResponse, now, nanoNow))
                .exceptionally((
                                       throwable -> {
                                           log(req, res, now, nanoNow);
                                           return null;
                                       }));

        req.next();
    }

    private void log(ServerRequest req, ServerResponse res, ZonedDateTime timeStart, long nanoStart) {
        logger.log(Level.INFO, createLogRecord(req, res, timeStart, nanoStart, ZonedDateTime.now(clock), System.nanoTime()));
    }

    String createLogRecord(ServerRequest req,
                           ServerResponse res,
                           ZonedDateTime timeStart,
                           long nanoStart,
                           ZonedDateTime timeNow,
                           long nanoNow) {

        AccessLogContext ctx = new AccessLogContext() {
            @Override
            public long requestNanoTime() {
                return nanoStart;
            }

            @Override
            public long responseNanoTime() {
                return nanoNow;
            }

            @Override
            public ZonedDateTime requestDateTime() {
                return timeStart;
            }

            @Override
            public ZonedDateTime responseDateTime() {
                return timeNow;
            }

            @Override
            public ServerRequest serverRequest() {
                return req;
            }

            @Override
            public ServerResponse serverResponse() {
                return res;
            }
        };
        StringBuilder sb = new StringBuilder();

        for (AccessLogEntry entry : logFormat) {
            sb.append(entry.apply(ctx));
            sb.append(" ");
        }

        if (sb.length() > 1) {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }

    /**
     * A fluent API Builder for {@link io.helidon.webserver.accesslog.AccessLogSupport}.
     */
    public static final class Builder implements io.helidon.common.Builder<AccessLogSupport> {
        private static final List<AccessLogEntry> COMMON_FORMAT = List.of(
                HostLogEntry.create(),
                UserIdLogEntry.create(),
                UserLogEntry.create(),
                TimestampLogEntry.create(),
                RequestLineLogEntry.create(),
                StatusLogEntry.create(),
                SizeLogEntry.create()
        );

        private static final List<AccessLogEntry> HELIDON_FORMAT = List.of(
                HostLogEntry.create(),
                UserLogEntry.create(),
                TimestampLogEntry.create(),
                RequestLineLogEntry.create(),
                StatusLogEntry.create(),
                SizeLogEntry.create(),
                TimeTakenLogEntry.create()
        );

        private final List<AccessLogEntry> entries = new LinkedList<>();
        private Clock clock = Clock.systemDefaultZone();
        private String loggerName = DEFAULT_LOGGER_NAME;
        private boolean enabled = true;

        private Builder() {
        }

        @Override
        public AccessLogSupport build() {
            if (entries.isEmpty()) {
                helidonLogFormat();
            }
            return new AccessLogSupport(this);
        }

        /**
         * Use Helidon log format.
         * <p>
         * Clears the current log entries and replaces them with Helidon log format.
         * <p>
         * Helidon log format uses the following log entries (in this order):
         * <ul>
         *     <li>{@link io.helidon.webserver.accesslog.HostLogEntry}</li>
         *     <li>{@link io.helidon.webserver.accesslog.UserLogEntry}</li>
         *     <li>{@link io.helidon.webserver.accesslog.TimestampLogEntry}</li>
         *     <li>{@link io.helidon.webserver.accesslog.RequestLineLogEntry}</li>
         *     <li>{@link io.helidon.webserver.accesslog.StatusLogEntry}</li>
         *     <li>{@link io.helidon.webserver.accesslog.SizeLogEntry}</li>
         *     <li>{@link io.helidon.webserver.accesslog.TimeTakenLogEntry} configured for
         *          {@link java.util.concurrent.TimeUnit#MICROSECONDS}</li>
         * </ul>
         * @return updated builder instance
         */
        public Builder helidonLogFormat() {
            entries.clear();
            entries.addAll(HELIDON_FORMAT);
            return this;
        }

        /**
         * Use {@code common} log format.
         * <p>
         * Clears the current log entries and replaces them with {@code common} log format.
         * <p>
         * {@code common} log format uses the following log entries (in this order):
         * <ul>
         *     <li>{@link io.helidon.webserver.accesslog.HostLogEntry}</li>
         *     <li>{@link io.helidon.webserver.accesslog.UserIdLogEntry}</li>
         *     <li>{@link io.helidon.webserver.accesslog.UserLogEntry}</li>
         *     <li>{@link io.helidon.webserver.accesslog.TimestampLogEntry}</li>
         *     <li>{@link io.helidon.webserver.accesslog.RequestLineLogEntry}</li>
         *     <li>{@link io.helidon.webserver.accesslog.StatusLogEntry}</li>
         *     <li>{@link io.helidon.webserver.accesslog.SizeLogEntry}</li>
         * </ul>
         * @return updated builder instance
         */
        public Builder commonLogFormat() {
            entries.clear();
            entries.addAll(COMMON_FORMAT);
            return this;
        }

        /**
         * The format for log entries (similar to the Apache {@code LogFormat}).
         * <table class="config">
         *     <caption>Log format elements</caption>
         *     <tr>
         *         <td>%h</td>
         *         <td>IP address of the remote host</td>
         *         <td>{@link io.helidon.webserver.accesslog.HostLogEntry}</td>
         *     </tr>
         *     <tr>
         *         <td>%l</td>
         *         <td>The client identity. This is always undefined in Helidon.</td>
         *         <td>{@link io.helidon.webserver.accesslog.UserIdLogEntry}</td>
         *     </tr>
         *     <tr>
         *         <td>%u</td>
         *         <td>User ID as asserted by Helidon Security.</td>
         *         <td>{@link io.helidon.webserver.accesslog.UserLogEntry}</td>
         *     </tr>
         *     <tr>
         *         <td>%t</td>
         *         <td>The timestamp</td>
         *         <td>{@link io.helidon.webserver.accesslog.TimestampLogEntry}</td>
         *     </tr>
         *     <tr>
         *         <td>%r</td>
         *         <td>The request line ({@code "GET /favicon.ico HTTP/1.0"})</td>
         *         <td>{@link io.helidon.webserver.accesslog.RequestLineLogEntry}</td>
         *     </tr>
         *     <tr>
         *         <td>%s</td>
         *         <td>The status code returned to the client</td>
         *         <td>{@link io.helidon.webserver.accesslog.StatusLogEntry}</td>
         *     </tr>
         *     <tr>
         *         <td>%b</td>
         *         <td>The entity size in bytes</td>
         *         <td>{@link io.helidon.webserver.accesslog.SizeLogEntry}</td>
         *     </tr>
         *     <tr>
         *         <td>%D</td>
         *         <td>The time taken in microseconds (start of request until last byte written)</td>
         *         <td>{@link io.helidon.webserver.accesslog.TimeTakenLogEntry}</td>
         *     </tr>
         *     <tr>
         *         <td>%T</td>
         *         <td>The time taken in seconds (start of request until last byte written), integer</td>
         *         <td>{@link io.helidon.webserver.accesslog.TimeTakenLogEntry}</td>
         *     </tr>
         *     <tr>
         *         <td>%{header-name}i</td>
         *         <td>Value of header {@code header-name}</td>
         *         <td>{@link io.helidon.webserver.accesslog.HeaderLogEntry}</td>
         *     </tr>
         * </table>
         *
         * @param format format string, such as {@code %h %l %u %t %r %b %{Referer}i}
         * @return updated builder instance
         */
        public Builder logFormatString(String format) {
            entries.clear();
            String[] formatEntries = format.split(" ");
            for (String formatEntry : formatEntries) {
                switch (formatEntry) {
                case "%h":
                    add(HostLogEntry.create());
                    break;
                case "%l":
                    add(UserIdLogEntry.create());
                    break;
                case "%u":
                    add(UserLogEntry.create());
                    break;
                case "%t":
                    add(TimestampLogEntry.create());
                    break;
                case "%r":
                    add(RequestLineLogEntry.create());
                    break;
                case "%s":
                    add(StatusLogEntry.create());
                    break;
                case "%b":
                    add(SizeLogEntry.create());
                    break;
                case "%D":
                    add(TimeTakenLogEntry.builder()
                                .unit(TimeUnit.MICROSECONDS)
                                .build());
                    break;
                case "%T":
                    add(TimeTakenLogEntry.builder()
                                .unit(TimeUnit.SECONDS)
                                .build());
                    break;
                default:
                    // an entry that has other classifiers - such as header
                    Matcher matcher = HEADER_ENTRY_PATTERN.matcher(formatEntry);
                    if (matcher.matches()) {
                        add(HeaderLogEntry.create(matcher.group(1)));
                    } else {
                        throw new IllegalArgumentException("Unsupported access log format entry: " + format);
                    }
                    break;
                }
            }
            return this;
        }

        /**
         * Add a {@link io.helidon.webserver.accesslog.AccessLogEntry} to the
         * list of log entries creating the format of this access log.
         * Entries are separated by a space.
         *
         * @param entry entry to add to the list of format entries
         * @return updated builder instance
         */
        public Builder add(AccessLogEntry entry) {
            entries.add(entry);
            return this;
        }

        /**
         * Access logging can be disabled (either through configuration
         *  or explicitly in code.
         *
         * @param enabled whether to enable ({@code true}) or disable ({@code false}) access logging
         * @return updated builder instance
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Update this builder from configuration. In case {@code format} is specified
         * in configuration, it would replace the currently configured format in this builder.
         *
         * @param config configuration with Access log configuration options
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("enabled").asBoolean().ifPresent(this::enabled);
            config.get("logger-name").asString().ifPresent(this::loggerName);
            config.get("format").asString().ifPresent(this::configLogFormat);
            return this;
        }

        /**
         * Name of the logger use to obtain access log logger from {@link Logger#getLogger(String)}.
         * Defaults to {@value DEFAULT_LOGGER_NAME}.
         *
         * @param loggerName name of the logger to use
         * @return updated builder instance
         */
        public Builder loggerName(String loggerName) {
            this.loggerName = loggerName;
            return this;
        }

        /**
         * Configure an alternative clock to use, such as {@link Clock#fixed(java.time.Instant, java.time.ZoneId)}.
         * Defaults to {@link java.time.Clock#systemDefaultZone()}.
         *
         * @param clock clock to use to get current times
         * @return updated builder instance
         */
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        private void configLogFormat(String format) {
            switch (format) {
            case "common":
                commonLogFormat();
                break;
            case "helidon":
                helidonLogFormat();
                break;
            default:
                logFormatString(format);
                break;
            }
        }
    }
}
