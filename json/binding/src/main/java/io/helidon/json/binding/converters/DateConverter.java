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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.JsonConverter;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class DateConverter implements JsonConverter<Date> {

    private static final GenericType<Date> TYPE = GenericType.create(Date.class);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    @Override
    public void serialize(JsonGenerator generator, Date instance, boolean writeNulls) {
        Instant instant = instance.toInstant();
        ZonedDateTime zonedDateTime = instant.atZone(ZoneOffset.UTC);
        generator.write(FORMATTER.format(zonedDateTime));
    }

    @Override
    public boolean isMapKeySerializer() {
        return true;
    }

    @Override
    public String serializeAsMapKey(Date instance) {
        Instant instant = instance.toInstant();
        ZonedDateTime zonedDateTime = instant.atZone(ZoneOffset.UTC);
        return FORMATTER.format(zonedDateTime);
    }

    @Override
    public Date deserialize(JsonParser parser) {
        if (parser.currentByte() == '"') {
            return Date.from(Instant.from(FORMATTER.parse(parser.readString())));
        }
        throw parser.createException("Only the string format of the Date is supported");
    }

    @Override
    public GenericType<Date> type() {
        return TYPE;
    }

}
