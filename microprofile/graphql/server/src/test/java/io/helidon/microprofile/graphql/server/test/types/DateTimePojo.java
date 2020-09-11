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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.json.bind.annotation.JsonbDateFormat;

import org.eclipse.microprofile.graphql.DateFormat;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Type;

/**
 * Class representing various date/time types.
 */
@Type
public class DateTimePojo {

    @JsonbDateFormat("MM/dd/yyyy")
    private LocalDate localDate;

    @DateFormat("MM/dd/yyyy")
    private LocalDate localDate2;

    @JsonbDateFormat("hh:mm:ss")
    private LocalTime localTime;
    private OffsetTime offsetTime;
    private LocalDateTime localDateTime;
    private OffsetDateTime offsetDateTime;
    private ZonedDateTime zonedDateTime;
    @Description("description")
    private LocalDate localDateNoFormat;
    private LocalTime localTimeNoFormat;

    private List<LocalDate> significantDates;

    public DateTimePojo(LocalDate localDate,
                        LocalDate localDate2,
                        LocalTime localTime,
                        OffsetTime offsetTime,
                        LocalDateTime localDateTime,
                        OffsetDateTime offsetDateTime,
                        ZonedDateTime zonedDateTime,
                        LocalDate localDateNoFormat,
                        List<LocalDate> significantDates) {
        this.localDate = localDate;
        this.localDate2 = localDate2;
        this.localTime = localTime;
        this.offsetTime = offsetTime;
        this.localDateTime = localDateTime;
        this.offsetDateTime = offsetDateTime;
        this.zonedDateTime = zonedDateTime;
        this.localDateNoFormat = localDateNoFormat;
        this.significantDates = significantDates;
    }

    public LocalTime getLocalTimeNoFormat() {
        return localTimeNoFormat;
    }

    public void setLocalTimeNoFormat(LocalTime localTimeNoFormat) {
        this.localTimeNoFormat = localTimeNoFormat;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public void setLocalDate(LocalDate localDate) {
        this.localDate = localDate;
    }

    public LocalTime getLocalTime() {
        return localTime;
    }

    public void setLocalTime(LocalTime localTime) {
        this.localTime = localTime;
    }

    public OffsetTime getOffsetTime() {
        return offsetTime;
    }

    public void setOffsetTime(OffsetTime offsetTime) {
        this.offsetTime = offsetTime;
    }

    public LocalDateTime getLocalDateTime() {
        return localDateTime;
    }

    public void setLocalDateTime(LocalDateTime localDateTime) {
        this.localDateTime = localDateTime;
    }

    public OffsetDateTime getOffsetDateTime() {
        return offsetDateTime;
    }

    public void setOffsetDateTime(OffsetDateTime offsetDateTime) {
        this.offsetDateTime = offsetDateTime;
    }

    public ZonedDateTime getZonedDateTime() {
        return zonedDateTime;
    }

    public void setZonedDateTime(ZonedDateTime zonedDateTime) {
        this.zonedDateTime = zonedDateTime;
    }

    public LocalDate getLocalDate2() {
        return localDate2;
    }

    public void setLocalDate2(LocalDate localDate2) {
        this.localDate2 = localDate2;
    }

    public LocalDate getLocalDateNoFormat() {
        return localDateNoFormat;
    }

    public void setLocalDateNoFormat(LocalDate localDateNoFormat) {
        this.localDateNoFormat = localDateNoFormat;
    }

    public void setSignificantDates(List<LocalDate> significantDates) {
        this.significantDates = significantDates;
    }

    public List<LocalDate> getSignificantDates() {
        return this.significantDates;
    }
}
