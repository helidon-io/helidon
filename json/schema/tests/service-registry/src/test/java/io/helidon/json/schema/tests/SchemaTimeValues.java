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

package io.helidon.json.schema.tests;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZonedDateTime;

import io.helidon.json.schema.JsonSchema;

@JsonSchema.Schema
record SchemaTimeValues(@JsonSchema.Required @JsonSchema.Description("A date") LocalDate date,
                        Instant instant,
                        LocalDateTime localDateTime,
                        LocalTime localTime,
                        OffsetDateTime offsetDateTime,
                        OffsetTime offsetTime,
                        ZonedDateTime zonedDateTime,
                        Duration duration,
                        Period period,
                        @JsonSchema.Required @JsonSchema.Title("Month") Month month,
                        DayOfWeek dayOfWeek) {
}
