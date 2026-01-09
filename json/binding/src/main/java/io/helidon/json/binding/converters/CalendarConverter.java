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

package io.helidon.json.binding.converters;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Calendar;
import java.util.GregorianCalendar;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.json.Generator;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.JsonConverter;
import io.helidon.service.registry.Service;

import static java.time.ZoneOffset.UTC;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class CalendarConverter implements JsonConverter<Calendar> {

    private static final LocalTime ZERO_LOCAL_TIME = LocalTime.parse("00:00:00");

    @Override
    public void serialize(Generator generator, Calendar instance, boolean writeNulls) {
        generator.write(serializeAsMapKey(instance));
    }

    @Override
    public boolean isMapKeySerializer() {
        return true;
    }

    @Override
    public String serializeAsMapKey(Calendar instance) {
        DateTimeFormatter formatter = instance.isSet(Calendar.HOUR) || instance.isSet(Calendar.HOUR_OF_DAY)
                ? DateTimeFormatter.ISO_DATE_TIME
                : DateTimeFormatter.ISO_DATE;
        return formatter.withZone(instance.getTimeZone().toZoneId())
                .format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(instance.getTimeInMillis()),
                                                instance.getTimeZone().toZoneId()));
    }

    @Override
    public Calendar deserialize(JsonParser parser) {
        if (parser.currentByte() == '"') {
            String value = parser.readString();
            DateTimeFormatter formatter = value.contains("T")
                    ? DateTimeFormatter.ISO_DATE_TIME
                    : DateTimeFormatter.ISO_DATE;
            final TemporalAccessor parsed = formatter.parse(value);
            LocalTime time = parsed.query(TemporalQueries.localTime());
            ZoneId zone = parsed.query(TemporalQueries.zone());
            if (zone == null) {
                zone = UTC;
            }
            if (time == null) {
                time = ZERO_LOCAL_TIME;
            }
            ZonedDateTime result = LocalDate.from(parsed).atTime(time).atZone(zone);
            return GregorianCalendar.from(result);
        }
        throw parser.createException("Only the string format of the Calendar is supported");
    }

    @Override
    public GenericType<Calendar> type() {
        return GenericType.create(Calendar.class);
    }

}
