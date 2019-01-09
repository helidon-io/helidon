/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.netty;

import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.regex.Pattern;

/**
 * The WebServerLogFormatter provides a way to customize logging messages.
 */
public class WebServerLogFormatter extends SimpleFormatter {

    private static final Pattern THREAD_PATTERN = Pattern.compile("!thread!");

    @Override
    public String format(LogRecord record) {
        String message = super.format(record);

        return THREAD_PATTERN.matcher(message).replaceAll(Thread.currentThread().toString());
    }
}
