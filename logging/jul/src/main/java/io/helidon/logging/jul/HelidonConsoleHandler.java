/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.logging.jul;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

/**
 * A {@link StreamHandler} that writes to {@link System#out standard out} and uses a {@link HelidonFormatter} for formatting.
 * Sets the level to {@link Level#ALL} so that level filtering is performed solely by the loggers.
 */
public class HelidonConsoleHandler extends StreamHandler {

    /**
     * Creates a new {@link HelidonConsoleHandler} configured with:
     * <ul>
     *     <li>the output stream set to {@link System#out}</li>
     *     <li>the formatter set to a {@link HelidonFormatter}</li>
     *     <li>the level set to {@link Level#ALL}</li>
     * </ul>.
     */
    public HelidonConsoleHandler() {
        setOutputStream(System.out);
        setLevel(Level.ALL); // Handlers should not filter, loggers should
        setFormatter(new HelidonFormatter());
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

}
