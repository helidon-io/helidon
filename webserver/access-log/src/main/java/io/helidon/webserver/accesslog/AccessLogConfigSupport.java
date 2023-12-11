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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.builder.api.Prototype;

import static io.helidon.webserver.accesslog.AccessLogConfigBlueprint.COMMON_FORMAT;
import static io.helidon.webserver.accesslog.AccessLogConfigBlueprint.HELIDON_FORMAT;

class AccessLogConfigSupport {
    private AccessLogConfigSupport() {
    }

    static class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Use default log format.
         * <p>
         * Clears the current log entries and replaces them with default log format.
         * <p>
         * Helidon log format uses the following log entries (in this order):
         * <ul>
         *     <li>{@link HostLogEntry}</li>
         *     <li>{@link UserLogEntry}</li>
         *     <li>{@link TimestampLogEntry}</li>
         *     <li>{@link RequestLineLogEntry}</li>
         *     <li>{@link StatusLogEntry}</li>
         *     <li>{@link SizeLogEntry}</li>
         *     <li>{@link TimeTakenLogEntry} configured for
         *          {@link java.util.concurrent.TimeUnit#MICROSECONDS}</li>
         * </ul>
         *
         * @param builder builder to update
         */
        @Prototype.BuilderMethod
        static void defaultLogFormat(AccessLogConfig.BuilderBase<?, ?> builder) {
            builder.entries(HELIDON_FORMAT);
        }

        /**
         * Use {@code common} log format.
         * <p>
         * Clears the current log entries and replaces them with {@code common} log format.
         * <p>
         * {@code common} log format uses the following log entries (in this order):
         * <ul>
         *     <li>{@link HostLogEntry}</li>
         *     <li>{@link UserIdLogEntry}</li>
         *     <li>{@link UserLogEntry}</li>
         *     <li>{@link TimestampLogEntry}</li>
         *     <li>{@link RequestLineLogEntry}</li>
         *     <li>{@link StatusLogEntry}</li>
         *     <li>{@link SizeLogEntry}</li>
         * </ul>
         *
         * @param builder builder to update
         */
        @Prototype.BuilderMethod
        static void commonLogFormat(AccessLogConfig.BuilderBase<?, ?> builder) {
            builder.entries(COMMON_FORMAT);
        }

    }

    static class BuilderDecorator implements Prototype.BuilderDecorator<AccessLogConfig.BuilderBase<?, ?>> {
        private static final Pattern HEADER_ENTRY_PATTERN = Pattern.compile("%\\{(.*?)}i");

        BuilderDecorator() {
        }

        @Override
        public void decorate(AccessLogConfig.BuilderBase<?, ?> target) {
            if (target.format().isPresent()) {
                format(target, target.format().get());
                // we always want to get rid of format and use entries
                target.clearFormat();
            }
            if (target.entries().isEmpty()) {
                target.defaultLogFormat();
            }
        }

        private static void format(AccessLogConfig.BuilderBase<?, ?> builder, String format) {
            switch (format) {
            case "common":
                builder.commonLogFormat();
                return;
            case "default":
                builder.defaultLogFormat();
                return;
            default:
                break;
            }

            List<AccessLogEntry> entries = new ArrayList<>();
            String[] formatEntries = format.split(" ");
            for (String formatEntry : formatEntries) {
                switch (formatEntry) {
                case "%h":
                    entries.add(HostLogEntry.create());
                    break;
                case "%l":
                    entries.add(UserIdLogEntry.create());
                    break;
                case "%u":
                    entries.add(UserLogEntry.create());
                    break;
                case "%t":
                    entries.add(TimestampLogEntry.create());
                    break;
                case "%r":
                    entries.add(RequestLineLogEntry.create());
                    break;
                case "%s":
                    entries.add(StatusLogEntry.create());
                    break;
                case "%b":
                    entries.add(SizeLogEntry.create());
                    break;
                case "%D":
                    entries.add(TimeTakenLogEntry.builder()
                                        .unit(TimeUnit.MICROSECONDS)
                                        .build());
                    break;
                case "%T":
                    entries.add(TimeTakenLogEntry.builder()
                                        .unit(TimeUnit.SECONDS)
                                        .build());
                    break;
                default:
                    // an entry that has other classifiers - such as header
                    Matcher matcher = HEADER_ENTRY_PATTERN.matcher(formatEntry);
                    if (matcher.matches()) {
                        entries.add(HeaderLogEntry.create(matcher.group(1)));
                    } else {
                        throw new IllegalArgumentException("Unsupported access log format entry: " + format);
                    }
                    break;
                }
            }
            builder.entries(entries);
        }
    }
}
