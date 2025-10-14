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

package io.helidon.validation.validators;

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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.validation.ValidationException;
import io.helidon.validation.ValidatorContext;
import io.helidon.validation.ValidatorResponse;
import io.helidon.validation.spi.ConstraintValidator;

final class CalendarHelper {
    private static final TypeName DATE = TypeName.create(Date.class);
    private static final TypeName CALENDAR = TypeName.create(Calendar.class);
    private static final TypeName INSTANT = TypeName.create(Instant.class);
    private static final TypeName LOCAL_DATE = TypeName.create(LocalDate.class);
    private static final TypeName LOCAL_DATE_TIME = TypeName.create(LocalDateTime.class);
    private static final TypeName LOCAL_TIME = TypeName.create(LocalTime.class);
    private static final TypeName MONTH_DAY = TypeName.create(MonthDay.class);
    private static final TypeName OFFSET_DATE_TIME = TypeName.create(OffsetDateTime.class);
    private static final TypeName OFFSET_TIME = TypeName.create(OffsetTime.class);
    private static final TypeName YEAR = TypeName.create(Year.class);
    private static final TypeName YEAR_MONTH = TypeName.create(YearMonth.class);
    private static final TypeName ZONED_DATE_TIME = TypeName.create(ZonedDateTime.class);
    private static final TypeName HIJRAH_DATE = TypeName.create(HijrahDate.class);
    private static final TypeName JAPANESE_DATE = TypeName.create(JapaneseDate.class);
    private static final TypeName MINGUO_DATE = TypeName.create(MinguoDate.class);
    private static final TypeName THAI_BUDDHIST_DATE = TypeName.create(ThaiBuddhistDate.class);

    private static final Map<TypeName, ValidatorFactory> VALIDATORS;

    static {
        Map<TypeName, ValidatorFactory> validators = new HashMap<>();
        validators.put(DATE, DateValidator::new);
        validators.put(CALENDAR, CalendarValidator::new);
        validators.put(INSTANT, InstantValidator::new);
        validators.put(LOCAL_DATE, LocalDateValidator::new);
        validators.put(LOCAL_DATE_TIME, LocalDateTimeValidator::new);
        validators.put(LOCAL_TIME, LocalTimeValidator::new);
        validators.put(MONTH_DAY, MonthDayValidator::new);
        validators.put(OFFSET_DATE_TIME, OffsetDateTimeValidator::new);
        validators.put(OFFSET_TIME, OffsetTimeValidator::new);
        validators.put(YEAR, YearValidator::new);
        validators.put(YEAR_MONTH, YearMonthValidator::new);
        validators.put(ZONED_DATE_TIME, ZonedDateTimeValidator::new);
        validators.put(HIJRAH_DATE, HijrahValidator::new);
        validators.put(JAPANESE_DATE, JapaneseValidator::new);
        validators.put(MINGUO_DATE, MinguoValidator::new);
        validators.put(THAI_BUDDHIST_DATE, ThaiBuddhistValidator::new);

        VALIDATORS = Map.copyOf(validators);
    }

    private CalendarHelper() {
    }

    static ConstraintValidator validator(TypeName type, Annotation constraintAnnotation, boolean future, boolean present) {
        String message = message(future, present);
        return Optional.ofNullable(VALIDATORS.get(type))
                .orElseThrow(() -> new ValidationException("Invalid type for calendar validation constraint: " + type.fqName()))
                .create(constraintAnnotation, message, future, present);
    }

    private static String message(boolean future, boolean present) {
        if (future && present) {
            return "%s must be present or future date/time";
        }
        if (future) {
            return "%s must be future date/time";
        }

        if (present) {
            return "%s must be past or present date/time";
        }
        return "%s must be past date/time";
    }

    private interface ValidatorFactory {
        ConstraintValidator create(Annotation constraintAnnotation, String message, boolean future, boolean present);
    }

    abstract static class CalendarValidatorBase<D> extends BaseValidator {
        private final boolean future;
        private final boolean present;

        CalendarValidatorBase(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, o -> true);
            this.future = future;
            this.present = present;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ValidatorResponse check(ValidatorContext context, Object value) {
            if (value == null) {
                return ValidatorResponse.create();
            }

            if (checkIt(context.clock(), (D) value)) {
                return ValidatorResponse.create();
            }

            return ValidatorResponse.create(annotation(), formatMessage(convertValue(value)), value);
        }

        /**
         * Validation the value.
         *
         * @param clock clock to obtain the current time
         * @param value value to check
         * @return negative if the value is before, 0 the same, and positive after the current time (i.e. past, present, future)
         */
        abstract int compare(Clock clock, D value);

        private boolean checkIt(Clock clock, D value) {
            int result = compare(clock, value);
            if (present && result == 0) {
                return true;
            }
            if (future && result > 0) {
                return true;
            }

            if (!future && result < 0) {
                return true;
            }
            return false;
        }
    }

    private static class DateValidator extends CalendarValidatorBase<Date> {
        DateValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, Date value) {
            var toValidation = value.toInstant();
            return toValidation.compareTo(clock.instant());
        }
    }

    private static class CalendarValidator extends CalendarValidatorBase<Calendar> {
        CalendarValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, Calendar value) {
            var toValidation = value.toInstant();
            return toValidation.compareTo(clock.instant());
        }
    }

    private static class InstantValidator extends CalendarValidatorBase<Instant> {
        InstantValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, Instant value) {
            return value.compareTo(clock.instant());
        }
    }

    private static class LocalDateValidator extends CalendarValidatorBase<LocalDate> {
        LocalDateValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, LocalDate value) {
            return value.compareTo(LocalDate.now(clock));
        }
    }

    private static class LocalDateTimeValidator extends CalendarValidatorBase<LocalDateTime> {
        LocalDateTimeValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, LocalDateTime value) {
            return value.compareTo(LocalDateTime.now(clock));
        }
    }

    private static class LocalTimeValidator extends CalendarValidatorBase<LocalTime> {
        LocalTimeValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, LocalTime value) {
            return value.compareTo(LocalTime.now(clock));
        }
    }

    private static class MonthDayValidator extends CalendarValidatorBase<MonthDay> {
        MonthDayValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, MonthDay value) {
            return value.compareTo(MonthDay.now(clock));
        }
    }

    private static class OffsetDateTimeValidator extends CalendarValidatorBase<OffsetDateTime> {
        OffsetDateTimeValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, OffsetDateTime value) {
            return value.compareTo(OffsetDateTime.now(clock));
        }
    }

    private static class OffsetTimeValidator extends CalendarValidatorBase<OffsetTime> {
        OffsetTimeValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, OffsetTime value) {
            return value.compareTo(OffsetTime.now(clock));
        }
    }

    private static class YearValidator extends CalendarValidatorBase<Year> {
        YearValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, Year value) {
            return value.compareTo(Year.now(clock));
        }
    }

    private static class YearMonthValidator extends CalendarValidatorBase<YearMonth> {
        YearMonthValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, YearMonth value) {
            return value.compareTo(YearMonth.now(clock));
        }
    }

    private static class ZonedDateTimeValidator extends CalendarValidatorBase<ZonedDateTime> {
        ZonedDateTimeValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, ZonedDateTime value) {
            return value.compareTo(ZonedDateTime.now(clock));
        }
    }

    private static class HijrahValidator extends CalendarValidatorBase<HijrahDate> {
        HijrahValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, HijrahDate value) {
            return value.compareTo(HijrahDate.now(clock));
        }
    }

    private static class JapaneseValidator extends CalendarValidatorBase<JapaneseDate> {
        JapaneseValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, JapaneseDate value) {
            return value.compareTo(JapaneseDate.now(clock));
        }
    }

    private static class MinguoValidator extends CalendarValidatorBase<MinguoDate> {
        MinguoValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, MinguoDate value) {
            return value.compareTo(MinguoDate.now(clock));
        }
    }

    private static class ThaiBuddhistValidator extends CalendarValidatorBase<ThaiBuddhistDate> {
        ThaiBuddhistValidator(Annotation annotation, String defaultMessage, boolean future, boolean present) {
            super(annotation, defaultMessage, future, present);
        }

        @Override
        int compare(Clock clock, ThaiBuddhistDate value) {
            return value.compareTo(ThaiBuddhistDate.now(clock));
        }
    }
}

