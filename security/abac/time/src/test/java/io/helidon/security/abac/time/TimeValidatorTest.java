/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.abac.time;

import java.lang.annotation.Annotation;
import java.time.DayOfWeek;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import io.helidon.common.Errors;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityLevel;
import io.helidon.security.SecurityTime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link TimeValidator}.
 */
public class TimeValidatorTest {
    private static TimeValidator validator;
    private static List<Annotation> annotations = new LinkedList<>();
    private static TimeValidator.TimeConfig timeConfig;

    @BeforeAll
    public static void initClass() {
        validator = TimeValidator.create();
        EndpointConfig ep = mock(EndpointConfig.class);

        TimeValidator.TimeOfDay tod = mock(TimeValidator.TimeOfDay.class);
        when(tod.from()).thenReturn("08:15:00");
        when(tod.to()).thenReturn("12:00");
        annotations.add(tod);

        TimeValidator.TimeOfDay tod2 = mock(TimeValidator.TimeOfDay.class);
        when(tod2.from()).thenReturn("12:30:00");
        when(tod2.to()).thenReturn("17:30");
        annotations.add(tod2);

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(TimeValidator.TimeOfDay.class, EndpointConfig.AnnotationScope.CLASS))
                .thenReturn(List.of(tod, tod2));

        TimeValidator.DaysOfWeek dow = mock(TimeValidator.DaysOfWeek.class);
        when(dow.value()).thenReturn(new DayOfWeek[] {
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
        });
        annotations.add(dow);
        when(classSecurityLevel.filterAnnotations(TimeValidator.DaysOfWeek.class, EndpointConfig.AnnotationScope.CLASS))
                .thenReturn(List.of(dow));


        timeConfig = validator.fromAnnotations(ep);
    }

    @Test
    public void testBetweenTimesAndDayOfWekPermit() {
        // explicitly set time to 10:00
        SecurityTime time = SecurityTime.builder()
                .value(ChronoField.HOUR_OF_DAY, 10)
                .value(ChronoField.MINUTE_OF_HOUR, 0)
                .value(ChronoField.DAY_OF_WEEK, DayOfWeek.TUESDAY.getValue())
                .build();

        Errors.Collector collector = Errors.collector();
        SecurityEnvironment env = SecurityEnvironment.builder()
                .time(time)
                .build();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(env);

        validator.validate(timeConfig, collector, request);

        collector.collect().checkValid();
    }

    @Test
    public void testBetweenTimesDeny() {
        // explicitly set time to 10:00
        SecurityTime time = SecurityTime.builder()
                .value(ChronoField.HOUR_OF_DAY, 12)
                .value(ChronoField.MINUTE_OF_HOUR, 15)
                .value(ChronoField.DAY_OF_WEEK, DayOfWeek.TUESDAY.getValue())
                .build();

        Errors.Collector collector = Errors.collector();
        SecurityEnvironment env = SecurityEnvironment.builder()
                .time(time)
                .build();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(env);

        validator.validate(timeConfig, collector, request);

        if (collector.collect().isValid()) {
            fail("Should have failed, as 12:15 is not in supported times");
        }
    }

    @Test
    public void testDayOfWeekDeny() {
        // explicitly set time to 10:00
        SecurityTime time = SecurityTime.builder()
                .value(ChronoField.HOUR_OF_DAY, 12)
                .value(ChronoField.MINUTE_OF_HOUR, 15)
                .value(ChronoField.DAY_OF_WEEK, DayOfWeek.SUNDAY.getValue())
                .build();

        Errors.Collector collector = Errors.collector();
        SecurityEnvironment env = SecurityEnvironment.builder()
                .time(time)
                .build();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.env()).thenReturn(env);

        validator.validate(timeConfig, collector, request);

        if (collector.collect().isValid()) {
            fail("Should have failed, as 12:15 is not in supported times");
        }
    }
}
