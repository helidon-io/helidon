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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
public class CalendarPastOrPresentValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ConstraintValidatorContextImpl ctx;
    private final Clock fixedClock;

    CalendarPastOrPresentValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   "io.helidon.validation.Check.Calendar.PastOrPresent");
        this.fixedClock = Clock.fixed(Instant.parse("2024-01-15T12:00:00Z"), java.time.ZoneOffset.UTC);
        this.ctx = new ConstraintValidatorContextImpl(CalendarPastOrPresentValidatorProviderTest.class, this, fixedClock);
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeName.create(Date.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        var response = validator.check(ctx, null);

        assertThat(response.failed(), is(false));
    }

    @Test
    public void testDatePastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(Date.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past date
        var pastDate = new Date(fixedClock.instant().minus(1, ChronoUnit.DAYS).toEpochMilli());
        var response = validator.check(ctx, pastDate);
        assertThat(response.failed(), is(false));

        // Present date (same instant)
        var presentDate = new Date(fixedClock.instant().toEpochMilli());
        response = validator.check(ctx, presentDate);
        assertThat(response.failed(), is(false));

        // Future date
        var futureDate = new Date(fixedClock.instant().plus(1, ChronoUnit.DAYS).toEpochMilli());
        response = validator.check(ctx, futureDate);
        assertThat(response.failed(), is(true));
        // cannot use equals, as Date uses the default time zone, which messes the whole thing up
        assertThat(response.message(), containsString("2024 must be past or present date/time"));
    }

    @Test
    public void testCalendarPastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(Calendar.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past calendar
        var pastCalendar = Calendar.getInstance();
        pastCalendar.setTimeInMillis(fixedClock.instant().minus(1, ChronoUnit.DAYS).toEpochMilli());
        var response = validator.check(ctx, pastCalendar);
        assertThat(response.failed(), is(false));

        // Present calendar
        var presentCalendar = Calendar.getInstance();
        presentCalendar.setTimeInMillis(fixedClock.instant().toEpochMilli());
        response = validator.check(ctx, presentCalendar);
        assertThat(response.failed(), is(false));

        // Future calendar
        var futureCalendar = Calendar.getInstance();
        futureCalendar.setTimeInMillis(fixedClock.instant().plus(1, ChronoUnit.DAYS).toEpochMilli());
        response = validator.check(ctx, futureCalendar);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testInstantPastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(Instant.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past instant
        var pastInstant = fixedClock.instant().minus(1, ChronoUnit.DAYS);
        var response = validator.check(ctx, pastInstant);
        assertThat(response.failed(), is(false));

        // Present instant
        var presentInstant = fixedClock.instant();
        response = validator.check(ctx, presentInstant);
        assertThat(response.failed(), is(false));

        // Future instant
        var futureInstant = fixedClock.instant().plus(1, ChronoUnit.DAYS);
        response = validator.check(ctx, futureInstant);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testLocalDatePastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(LocalDate.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past local date
        var pastDate = LocalDate.now(fixedClock).minusDays(1);
        var response = validator.check(ctx, pastDate);
        assertThat(response.failed(), is(false));

        // Present local date
        var presentDate = LocalDate.now(fixedClock);
        response = validator.check(ctx, presentDate);
        assertThat(response.failed(), is(false));

        // Future local date
        var futureDate = LocalDate.now(fixedClock).plusDays(1);
        response = validator.check(ctx, futureDate);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testLocalDateTimePastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(LocalDateTime.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past local date time
        var pastDateTime = LocalDateTime.now(fixedClock).minusDays(1);
        var response = validator.check(ctx, pastDateTime);
        assertThat(response.failed(), is(false));

        // Present local date time
        var presentDateTime = LocalDateTime.now(fixedClock);
        response = validator.check(ctx, presentDateTime);
        assertThat(response.failed(), is(false));

        // Future local date time
        var futureDateTime = LocalDateTime.now(fixedClock).plusDays(1);
        response = validator.check(ctx, futureDateTime);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testLocalTimePastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(LocalTime.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past local time
        var pastTime = LocalTime.now(fixedClock).minusHours(1);
        var response = validator.check(ctx, pastTime);
        assertThat(response.failed(), is(false));

        // Present local time
        var presentTime = LocalTime.now(fixedClock);
        response = validator.check(ctx, presentTime);
        assertThat(response.failed(), is(false));

        // Future local time
        var futureTime = LocalTime.now(fixedClock).plusHours(1);
        response = validator.check(ctx, futureTime);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testMonthDayPastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(MonthDay.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past month day (earlier in year) - January 14th should be past relative to January 15th
        var pastMonthDay = MonthDay.of(1, 14); // January 14th
        var response = validator.check(ctx, pastMonthDay);
        assertThat(response.failed(), is(false));

        // Present month day
        var presentMonthDay = MonthDay.now(fixedClock);
        response = validator.check(ctx, presentMonthDay);
        assertThat(response.failed(), is(false));

        // Future month day (later in year) - February should be future in January
        var futureMonthDay = MonthDay.of(2, 15); // February 15th
        response = validator.check(ctx, futureMonthDay);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testOffsetDateTimePastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(OffsetDateTime.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past offset date time
        var pastOffsetDateTime = OffsetDateTime.now(fixedClock).minusDays(1);
        var response = validator.check(ctx, pastOffsetDateTime);
        assertThat(response.failed(), is(false));

        // Present offset date time
        var presentOffsetDateTime = OffsetDateTime.now(fixedClock);
        response = validator.check(ctx, presentOffsetDateTime);
        assertThat(response.failed(), is(false));

        // Future offset date time
        var futureOffsetDateTime = OffsetDateTime.now(fixedClock).plusDays(1);
        response = validator.check(ctx, futureOffsetDateTime);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testOffsetTimePastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(OffsetTime.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past offset time
        var pastOffsetTime = OffsetTime.now(fixedClock).minusHours(1);
        var response = validator.check(ctx, pastOffsetTime);
        assertThat(response.failed(), is(false));

        // Present offset time
        var presentOffsetTime = OffsetTime.now(fixedClock);
        response = validator.check(ctx, presentOffsetTime);
        assertThat(response.failed(), is(false));

        // Future offset time
        var futureOffsetTime = OffsetTime.now(fixedClock).plusHours(1);
        response = validator.check(ctx, futureOffsetTime);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testYearPastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(Year.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past year
        var pastYear = Year.now(fixedClock).minusYears(1);
        var response = validator.check(ctx, pastYear);
        assertThat(response.failed(), is(false));

        // Present year
        var presentYear = Year.now(fixedClock);
        response = validator.check(ctx, presentYear);
        assertThat(response.failed(), is(false));

        // Future year
        var futureYear = Year.now(fixedClock).plusYears(1);
        response = validator.check(ctx, futureYear);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testYearMonthPastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(YearMonth.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past year month
        var pastYearMonth = YearMonth.now(fixedClock).minusMonths(1);
        var response = validator.check(ctx, pastYearMonth);
        assertThat(response.failed(), is(false));

        // Present year month
        var presentYearMonth = YearMonth.now(fixedClock);
        response = validator.check(ctx, presentYearMonth);
        assertThat(response.failed(), is(false));

        // Future year month
        var futureYearMonth = YearMonth.now(fixedClock).plusMonths(1);
        response = validator.check(ctx, futureYearMonth);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testZonedDateTimePastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(ZonedDateTime.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past zoned date time
        var pastZonedDateTime = ZonedDateTime.now(fixedClock).minusDays(1);
        var response = validator.check(ctx, pastZonedDateTime);
        assertThat(response.failed(), is(false));

        // Present zoned date time
        var presentZonedDateTime = ZonedDateTime.now(fixedClock);
        response = validator.check(ctx, presentZonedDateTime);
        assertThat(response.failed(), is(false));

        // Future zoned date time
        var futureZonedDateTime = ZonedDateTime.now(fixedClock).plusDays(1);
        response = validator.check(ctx, futureZonedDateTime);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testHijrahDatePastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(HijrahDate.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past hijrah date
        var pastHijrahDate = HijrahDate.now(fixedClock).minus(1, java.time.temporal.ChronoUnit.DAYS);
        var response = validator.check(ctx, pastHijrahDate);
        assertThat(response.failed(), is(false));

        // Present hijrah date
        var presentHijrahDate = HijrahDate.now(fixedClock);
        response = validator.check(ctx, presentHijrahDate);
        assertThat(response.failed(), is(false));

        // Future hijrah date
        var futureHijrahDate = HijrahDate.now(fixedClock).plus(1, java.time.temporal.ChronoUnit.DAYS);
        response = validator.check(ctx, futureHijrahDate);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testJapaneseDatePastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(JapaneseDate.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past japanese date
        var pastJapaneseDate = JapaneseDate.now(fixedClock).minus(1, java.time.temporal.ChronoUnit.DAYS);
        var response = validator.check(ctx, pastJapaneseDate);
        assertThat(response.failed(), is(false));

        // Present japanese date
        var presentJapaneseDate = JapaneseDate.now(fixedClock);
        response = validator.check(ctx, presentJapaneseDate);
        assertThat(response.failed(), is(false));

        // Future japanese date
        var futureJapaneseDate = JapaneseDate.now(fixedClock).plus(1, java.time.temporal.ChronoUnit.DAYS);
        response = validator.check(ctx, futureJapaneseDate);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testMinguoDatePastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(MinguoDate.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past minguo date
        var pastMinguoDate = MinguoDate.now(fixedClock).minus(1, java.time.temporal.ChronoUnit.DAYS);
        var response = validator.check(ctx, pastMinguoDate);
        assertThat(response.failed(), is(false));

        // Present minguo date
        var presentMinguoDate = MinguoDate.now(fixedClock);
        response = validator.check(ctx, presentMinguoDate);
        assertThat(response.failed(), is(false));

        // Future minguo date
        var futureMinguoDate = MinguoDate.now(fixedClock).plus(1, java.time.temporal.ChronoUnit.DAYS);
        response = validator.check(ctx, futureMinguoDate);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testThaiBuddhistDatePastOrPresent() {
        var validator = validatorProvider.create(TypeName.create(ThaiBuddhistDate.class),
                                                 Annotation.create(Check.Calendar.PastOrPresent.class));

        // Past thai buddhist date
        var pastThaiBuddhistDate = ThaiBuddhistDate.now(fixedClock).minus(1, java.time.temporal.ChronoUnit.DAYS);
        var response = validator.check(ctx, pastThaiBuddhistDate);
        assertThat(response.failed(), is(false));

        // Present thai buddhist date
        var presentThaiBuddhistDate = ThaiBuddhistDate.now(fixedClock);
        response = validator.check(ctx, presentThaiBuddhistDate);
        assertThat(response.failed(), is(false));

        // Future thai buddhist date
        var futureThaiBuddhistDate = ThaiBuddhistDate.now(fixedClock).plus(1, java.time.temporal.ChronoUnit.DAYS);
        response = validator.check(ctx, futureThaiBuddhistDate);
        assertThat(response.failed(), is(true));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeName.create(Instant.class), Annotation.builder()
                .typeName(TypeName.create(Check.Calendar.PastOrPresent.class))
                .putValue("message", "Value must be past or present")
                .build());

        var futureInstant = fixedClock.instant().plus(1, ChronoUnit.DAYS);
        var response = validator.check(ctx, futureInstant);

        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("Value must be past or present"));
    }
}