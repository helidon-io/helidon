/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.validation;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.chrono.HijrahDate;
import java.time.chrono.JapaneseDate;
import java.time.chrono.MinguoDate;
import java.time.chrono.ThaiBuddhistDate;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.validation.spi.ConstraintValidatorProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
public class CalendarFutureValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ConstraintValidatorContextImpl ctx;
    private final Clock fixedClock;

    CalendarFutureValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   "io.helidon.validation.Check.Calendar.Future");
        this.fixedClock = Clock.fixed(Instant.parse("2024-01-15T12:00:00Z"), java.time.ZoneOffset.UTC);
        this.ctx = new ConstraintValidatorContextImpl(CalendarFutureValidatorProviderTest.class, this, fixedClock);
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeName.create(Date.class), Annotation.create(Check.Calendar.Future.class));

        var response = validator.check(ctx, null);

        assertThat(response.failed(), is(false));
    }

    @Test
    public void testDateFuture() {
        var validator = validatorProvider.create(TypeName.create(Date.class), Annotation.create(Check.Calendar.Future.class));

        // Future date
        var futureDate = new Date(fixedClock.instant().plus(1, ChronoUnit.DAYS).toEpochMilli());
        var response = validator.check(ctx, futureDate);
        assertThat(response.failed(), is(false));

        // Past date
        var pastDate = new Date(fixedClock.instant().minus(1, ChronoUnit.DAYS).toEpochMilli());
        response = validator.check(ctx, pastDate);
        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("Sun Jan 14 13:00:00 CET 2024 must be future date/time"));
    }

    @Test
    public void testCalendarFuture() {
        var validator = validatorProvider.create(TypeName.create(Calendar.class), Annotation.create(Check.Calendar.Future.class));

        // Future calendar
        var futureCalendar = Calendar.getInstance();
        futureCalendar.setTimeInMillis(fixedClock.instant().plus(1, ChronoUnit.DAYS).toEpochMilli());
        var response = validator.check(ctx, futureCalendar);
        assertThat(response.failed(), is(false));

        // Past calendar
        var pastCalendar = Calendar.getInstance();
        pastCalendar.setTimeInMillis(fixedClock.instant().minus(1, ChronoUnit.DAYS).toEpochMilli());
        response = validator.check(ctx, pastCalendar);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testInstantFuture() {
        var validator = validatorProvider.create(TypeName.create(Instant.class), Annotation.create(Check.Calendar.Future.class));

        // Future instant
        var futureInstant = fixedClock.instant().plus(1, ChronoUnit.DAYS);
        var response = validator.check(ctx, futureInstant);
        assertThat(response.failed(), is(false));

        // Past instant
        var pastInstant = fixedClock.instant().minus(1, ChronoUnit.DAYS);
        response = validator.check(ctx, pastInstant);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testLocalDateFuture() {
        var validator = validatorProvider.create(TypeName.create(LocalDate.class), Annotation.create(Check.Calendar.Future.class));

        // Future local date
        var futureDate = LocalDate.now(fixedClock).plusDays(1);
        var response = validator.check(ctx, futureDate);
        assertThat(response.failed(), is(false));

        // Past local date
        var pastDate = LocalDate.now(fixedClock).minusDays(1);
        response = validator.check(ctx, pastDate);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testLocalDateTimeFuture() {
        var validator = validatorProvider.create(TypeName.create(LocalDateTime.class), Annotation.create(Check.Calendar.Future.class));

        // Future local date time
        var futureDateTime = LocalDateTime.now(fixedClock).plusDays(1);
        var response = validator.check(ctx, futureDateTime);
        assertThat(response.failed(), is(false));

        // Past local date time
        var pastDateTime = LocalDateTime.now(fixedClock).minusDays(1);
        response = validator.check(ctx, pastDateTime);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testLocalTimeFuture() {
        var validator = validatorProvider.create(TypeName.create(LocalTime.class), Annotation.create(Check.Calendar.Future.class));

        // Future local time
        var futureTime = LocalTime.now(fixedClock).plusHours(1);
        var response = validator.check(ctx, futureTime);
        assertThat(response.failed(), is(false));

        // Past local time
        var pastTime = LocalTime.now(fixedClock).minusHours(1);
        response = validator.check(ctx, pastTime);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testMonthDayFuture() {
        var validator = validatorProvider.create(TypeName.create(MonthDay.class), Annotation.create(Check.Calendar.Future.class));

        // Current month day is January 15th
        var currentMonthDay = MonthDay.now(fixedClock);

        // Future month day (next month) - February should be future in January
        var futureMonthDay = MonthDay.of(2, 15); // February 15th
        var response = validator.check(ctx, futureMonthDay);
        assertThat(response.failed(), is(false));

        // Past month day (previous month) - December should be past in January
        // Note: For MonthDay, "past" means earlier in the year, so December is actually "future" relative to January
        // Let's use a month that's actually earlier in the year
        var pastMonthDay = MonthDay.of(1, 14); // January 14th (same month, earlier day)
        response = validator.check(ctx, pastMonthDay);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testOffsetDateTimeFuture() {
        var validator = validatorProvider.create(TypeName.create(OffsetDateTime.class), Annotation.create(Check.Calendar.Future.class));

        // Future offset date time
        var futureOffsetDateTime = OffsetDateTime.now(fixedClock).plusDays(1);
        var response = validator.check(ctx, futureOffsetDateTime);
        assertThat(response.failed(), is(false));

        // Past offset date time
        var pastOffsetDateTime = OffsetDateTime.now(fixedClock).minusDays(1);
        response = validator.check(ctx, pastOffsetDateTime);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testOffsetTimeFuture() {
        var validator = validatorProvider.create(TypeName.create(OffsetTime.class), Annotation.create(Check.Calendar.Future.class));

        // Future offset time
        var futureOffsetTime = OffsetTime.now(fixedClock).plusHours(1);
        var response = validator.check(ctx, futureOffsetTime);
        assertThat(response.failed(), is(false));

        // Past offset time
        var pastOffsetTime = OffsetTime.now(fixedClock).minusHours(1);
        response = validator.check(ctx, pastOffsetTime);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testYearFuture() {
        var validator = validatorProvider.create(TypeName.create(Year.class), Annotation.create(Check.Calendar.Future.class));

        // Future year
        var futureYear = Year.now(fixedClock).plusYears(1);
        var response = validator.check(ctx, futureYear);
        assertThat(response.failed(), is(false));

        // Past year
        var pastYear = Year.now(fixedClock).minusYears(1);
        response = validator.check(ctx, pastYear);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testYearMonthFuture() {
        var validator = validatorProvider.create(TypeName.create(YearMonth.class), Annotation.create(Check.Calendar.Future.class));

        // Future year month
        var futureYearMonth = YearMonth.now(fixedClock).plusMonths(1);
        var response = validator.check(ctx, futureYearMonth);
        assertThat(response.failed(), is(false));

        // Past year month
        var pastYearMonth = YearMonth.now(fixedClock).minusMonths(1);
        response = validator.check(ctx, pastYearMonth);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testZonedDateTimeFuture() {
        var validator = validatorProvider.create(TypeName.create(ZonedDateTime.class), Annotation.create(Check.Calendar.Future.class));

        // Future zoned date time
        var futureZonedDateTime = ZonedDateTime.now(fixedClock).plusDays(1);
        var response = validator.check(ctx, futureZonedDateTime);
        assertThat(response.failed(), is(false));

        // Past zoned date time
        var pastZonedDateTime = ZonedDateTime.now(fixedClock).minusDays(1);
        response = validator.check(ctx, pastZonedDateTime);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testHijrahDateFuture() {
        var validator = validatorProvider.create(TypeName.create(HijrahDate.class), Annotation.create(Check.Calendar.Future.class));

        // Future hijrah date
        var futureHijrahDate = HijrahDate.now(fixedClock).plus(1, java.time.temporal.ChronoUnit.DAYS);
        var response = validator.check(ctx, futureHijrahDate);
        assertThat(response.failed(), is(false));

        // Past hijrah date
        var pastHijrahDate = HijrahDate.now(fixedClock).minus(1, java.time.temporal.ChronoUnit.DAYS);
        response = validator.check(ctx, pastHijrahDate);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testJapaneseDateFuture() {
        var validator = validatorProvider.create(TypeName.create(JapaneseDate.class), Annotation.create(Check.Calendar.Future.class));

        // Future japanese date
        var futureJapaneseDate = JapaneseDate.now(fixedClock).plus(1, java.time.temporal.ChronoUnit.DAYS);
        var response = validator.check(ctx, futureJapaneseDate);
        assertThat(response.failed(), is(false));

        // Past japanese date
        var pastJapaneseDate = JapaneseDate.now(fixedClock).minus(1, java.time.temporal.ChronoUnit.DAYS);
        response = validator.check(ctx, pastJapaneseDate);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testMinguoDateFuture() {
        var validator = validatorProvider.create(TypeName.create(MinguoDate.class), Annotation.create(Check.Calendar.Future.class));

        // Future minguo date
        var futureMinguoDate = MinguoDate.now(fixedClock).plus(1, java.time.temporal.ChronoUnit.DAYS);
        var response = validator.check(ctx, futureMinguoDate);
        assertThat(response.failed(), is(false));

        // Past minguo date
        var pastMinguoDate = MinguoDate.now(fixedClock).minus(1, java.time.temporal.ChronoUnit.DAYS);
        response = validator.check(ctx, pastMinguoDate);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testThaiBuddhistDateFuture() {
        var validator = validatorProvider.create(TypeName.create(ThaiBuddhistDate.class), Annotation.create(Check.Calendar.Future.class));

        // Future thai buddhist date
        var futureThaiBuddhistDate = ThaiBuddhistDate.now(fixedClock).plus(1, java.time.temporal.ChronoUnit.DAYS);
        var response = validator.check(ctx, futureThaiBuddhistDate);
        assertThat(response.failed(), is(false));

        // Past thai buddhist date
        var pastThaiBuddhistDate = ThaiBuddhistDate.now(fixedClock).minus(1, java.time.temporal.ChronoUnit.DAYS);
        response = validator.check(ctx, pastThaiBuddhistDate);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeName.create(Instant.class), Annotation.builder()
                .typeName(TypeName.create(Check.Calendar.Future.class))
                .putValue("message", "Value must be in the future")
                .build());

        var pastInstant = fixedClock.instant().minus(1, ChronoUnit.DAYS);
        var response = validator.check(ctx, pastInstant);

        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("Value must be in the future"));
    }
}