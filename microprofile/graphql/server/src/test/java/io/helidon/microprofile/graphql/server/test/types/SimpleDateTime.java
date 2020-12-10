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

package io.helidon.microprofile.graphql.server.test.types;

import java.time.LocalDate;
import java.util.List;

import org.eclipse.microprofile.graphql.DateFormat;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Type;

/**
 * Class representing a simple date/time type.
 */
@Type
public class SimpleDateTime {

    private List<LocalDate> importantDates;

    public SimpleDateTime() {
    }

    public SimpleDateTime(List<LocalDate> importantDates) {
        this.importantDates = importantDates;
    }

    @Name("calendarEntries")
    public void setImportantDates(List<@DateFormat("dd/MM/yy") LocalDate> importantDates) {
        this.importantDates = importantDates;
    }

    public List<@DateFormat("dd/MM") LocalDate> getImportantDates() {
        return importantDates;
    }
}
