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

package io.helidon.common;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import java.util.regex.Pattern;

/**
 * A {@link StreamHandler} that writes to {@link System#out standard out} and uses a {@link ThreadFormatter} for formatting.
 * Sets the level to {@link Level#ALL} so that level filtering is performed solely by the loggers.
 *
 * @deprecated use io.helidon.logging.jul.HelidonConsoleHandler from helidon-logging-jul module instead
 */
@Deprecated(since = "2.1.1")
public class HelidonConsoleHandler extends StreamHandler {

    /**
     * Creates a new {@link HelidonConsoleHandler} configured with:
     * <ul>
     *     <li>the output stream set to {@link System#out}</li>
     *     <li>the formatter set to a {@link ThreadFormatter}</li>
     *     <li>the level set to {@link Level#ALL}</li>
     * </ul>.
     */
    public HelidonConsoleHandler() {
        setOutputStream(System.out);
        setLevel(Level.ALL); // Handlers should not filter, loggers should
        setFormatter(new ThreadFormatter());
        System.out.println("You are using deprecated logging handler -> io.helidon.common.HelidonConsoleHandler");
        System.out.println("Please use helidon-logging-jul module and change your handler to "
                + "io.helidon.logging.jul.HelidonConsoleHandler");
    }

    @Override
    public void publish(LogRecord record) {
        super.publish(record);
        flush();
    }

    @Override
    public void close() {
        flush();
    }

    /**
     * A {@link SimpleFormatter} that replaces all occurrences of {@code "!thread!"} with the current thread.
     */
    public static class ThreadFormatter extends SimpleFormatter {
        private static final Pattern THREAD_PATTERN = Pattern.compile("!thread!");

        @Override
        public String format(LogRecord record) {
            final String message = super.format(record);
            return THREAD_PATTERN.matcher(message).replaceAll(Thread.currentThread().toString());
        }
    }
}
