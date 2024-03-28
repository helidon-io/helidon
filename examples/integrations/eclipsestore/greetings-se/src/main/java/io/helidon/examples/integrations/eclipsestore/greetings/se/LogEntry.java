/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.eclipsestore.greetings.se;

import java.time.LocalDateTime;


/**
 * Simple POJO that represents a Log entry that is stored by
 * Eclipse store in this example.
 */
public final class LogEntry {

    /**
     *  Name to be logged.
     */
    private final String name;

    /**
     * Date and time to be logged.
     */
    private final LocalDateTime dateTime;

    /**
     * @param aName     name to be logged.
     * @param aDateTime dateTime date and time to be logged
     */
    public LogEntry(final String aName, final LocalDateTime aDateTime) {
        this.name = aName;
        this.dateTime = aDateTime;
    }

    /**
     * get the name.
     *
     * @return name
     */
    public String name() {
        return name;
    }


    /**
     * Get the date time.
     *
     * @return date time
     */
    public LocalDateTime dateTime() {
        return dateTime;
    }


}
