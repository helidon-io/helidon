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
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Service.NamedByType;
import io.helidon.validation.spi.ConstraintValidator;
import io.helidon.validation.spi.ConstraintValidatorProvider;

@Service.Singleton
class ValidatorsService {
    private static final TypeName CHAR_SEQUENCE_TYPE = TypeName.create(CharSequence.class);
    private static final TypeName NUMBER_TYPE = TypeName.create(Number.class);

    private final ConstraintValidator notNullValidator;
    private final ConstraintValidator nullValidator;
    private final ConstraintValidator stringEmailValidator;
    private final ConstraintValidator stringNotBlankValidator;
    private final ConstraintValidator stringNotEmptyValidator;
    private final ConstraintValidator numberNegativeValidator;
    private final ConstraintValidator numberNegativeOrZeroValidator;
    private final ConstraintValidator numberPositiveValidator;
    private final ConstraintValidator numberPositiveOrZeroValidator;
    private final ConstraintValidator booleanTrueValidator;
    private final ConstraintValidator booleanFalseValidator;

    // Providers for parameterized validators
    private final ConstraintValidatorProvider stringLengthProvider;
    private final ConstraintValidatorProvider stringPatternProvider;
    private final ConstraintValidatorProvider numberMinProvider;
    private final ConstraintValidatorProvider numberMaxProvider;
    private final ConstraintValidatorProvider numberDigitsProvider;
    private final ConstraintValidatorProvider integerMinProvider;
    private final ConstraintValidatorProvider integerMaxProvider;
    private final ConstraintValidatorProvider longMinProvider;
    private final ConstraintValidatorProvider longMaxProvider;
    private final ConstraintValidatorProvider collectionSizeProvider;
    private final ConstraintValidatorProvider calendarFutureProvider;
    private final ConstraintValidatorProvider calendarFutureOrPresentProvider;
    private final ConstraintValidatorProvider calendarPastProvider;
    private final ConstraintValidatorProvider calendarPastOrPresentProvider;

    ValidatorsService(
            @NamedByType(Validation.NotNull.class) ConstraintValidatorProvider notNullProvider,
            @NamedByType(Validation.Null.class) ConstraintValidatorProvider nullProvider,
            @NamedByType(Validation.String.Email.class) ConstraintValidatorProvider stringEmailProvider,
            @NamedByType(Validation.String.NotBlank.class) ConstraintValidatorProvider stringNotBlankProvider,
            @NamedByType(Validation.String.NotEmpty.class) ConstraintValidatorProvider stringNotEmptyProvider,
            @NamedByType(Validation.String.Length.class) ConstraintValidatorProvider stringLengthProvider,
            @NamedByType(Validation.String.Pattern.class) ConstraintValidatorProvider stringPatternProvider,
            @NamedByType(Validation.Number.Negative.class) ConstraintValidatorProvider numberNegativeProvider,
            @NamedByType(Validation.Number.NegativeOrZero.class) ConstraintValidatorProvider numberNegativeOrZeroProvider,
            @NamedByType(Validation.Number.Positive.class) ConstraintValidatorProvider numberPositiveProvider,
            @NamedByType(Validation.Number.PositiveOrZero.class) ConstraintValidatorProvider numberPositiveOrZeroProvider,
            @NamedByType(Validation.Number.Min.class) ConstraintValidatorProvider numberMinProvider,
            @NamedByType(Validation.Number.Max.class) ConstraintValidatorProvider numberMaxProvider,
            @NamedByType(Validation.Number.Digits.class) ConstraintValidatorProvider numberDigitsProvider,
            @NamedByType(Validation.Integer.Min.class) ConstraintValidatorProvider integerMinProvider,
            @NamedByType(Validation.Integer.Max.class) ConstraintValidatorProvider integerMaxProvider,
            @NamedByType(Validation.Long.Min.class) ConstraintValidatorProvider longMinProvider,
            @NamedByType(Validation.Long.Max.class) ConstraintValidatorProvider longMaxProvider,
            @NamedByType(Validation.Boolean.True.class) ConstraintValidatorProvider booleanTrueProvider,
            @NamedByType(Validation.Boolean.False.class) ConstraintValidatorProvider booleanFalseProvider,
            @NamedByType(Validation.Calendar.Future.class) ConstraintValidatorProvider calendarFutureProvider,
            @NamedByType(Validation.Calendar.FutureOrPresent.class) ConstraintValidatorProvider calendarFutureOrPresentProvider,
            @NamedByType(Validation.Calendar.Past.class) ConstraintValidatorProvider calendarPastProvider,
            @NamedByType(Validation.Calendar.PastOrPresent.class) ConstraintValidatorProvider calendarPastOrPresentProvider,
            @NamedByType(Validation.Collection.Size.class) ConstraintValidatorProvider collectionSizeProvider) {

        this.notNullValidator =
                notNullProvider.create(TypeNames.OBJECT,
                                       Annotation.create(Validation.NotNull.class));
        this.nullValidator =
                nullProvider.create(TypeNames.OBJECT,
                                    Annotation.create(Validation.Null.class));
        this.stringEmailValidator =
                stringEmailProvider.create(CHAR_SEQUENCE_TYPE,
                                           Annotation.create(Validation.String.Email.class));
        this.stringNotBlankValidator =
                stringNotBlankProvider.create(CHAR_SEQUENCE_TYPE,
                                              Annotation.create(Validation.String.NotBlank.class));
        this.stringNotEmptyValidator =
                stringNotEmptyProvider.create(CHAR_SEQUENCE_TYPE,
                                              Annotation.create(Validation.String.NotEmpty.class));
        this.numberNegativeValidator =
                numberNegativeProvider.create(NUMBER_TYPE,
                                              Annotation.create(Validation.Number.Negative.class));
        this.numberNegativeOrZeroValidator =
                numberNegativeOrZeroProvider.create(NUMBER_TYPE,
                                                    Annotation.create(Validation.Number.NegativeOrZero.class));
        this.numberPositiveValidator =
                numberPositiveProvider.create(NUMBER_TYPE,
                                              Annotation.create(Validation.Number.Positive.class));
        this.numberPositiveOrZeroValidator =
                numberPositiveOrZeroProvider.create(NUMBER_TYPE,
                                                    Annotation.create(Validation.Number.PositiveOrZero.class));
        this.booleanTrueValidator =
                booleanTrueProvider.create(TypeNames.BOXED_BOOLEAN,
                                           Annotation.create(Validation.Boolean.True.class));
        this.booleanFalseValidator =
                booleanFalseProvider.create(TypeNames.BOXED_BOOLEAN,
                                            Annotation.create(Validation.Boolean.False.class));

        // Store providers for parameterized validators and validators that require a good type
        this.stringLengthProvider = stringLengthProvider;
        this.stringPatternProvider = stringPatternProvider;
        this.numberMinProvider = numberMinProvider;
        this.numberMaxProvider = numberMaxProvider;
        this.numberDigitsProvider = numberDigitsProvider;
        this.integerMinProvider = integerMinProvider;
        this.integerMaxProvider = integerMaxProvider;
        this.longMinProvider = longMinProvider;
        this.longMaxProvider = longMaxProvider;
        this.calendarFutureProvider = calendarFutureProvider;
        this.calendarFutureOrPresentProvider = calendarFutureOrPresentProvider;
        this.calendarPastProvider = calendarPastProvider;
        this.calendarPastOrPresentProvider = calendarPastOrPresentProvider;
        this.collectionSizeProvider = collectionSizeProvider;
    }

    void validateNotNull(ValidationContext ctx, Object value) {
        ctx.check(notNullValidator, value);
    }

    void validateNull(ValidationContext ctx, Object value) {
        ctx.check(nullValidator, value);
    }

    void validateStringEmail(ValidationContext ctx, Object value) {
        ctx.check(stringEmailValidator, value);
    }

    void validateStringNotBlank(ValidationContext ctx, Object value) {
        ctx.check(stringNotBlankValidator, value);
    }

    void validateStringNotEmpty(ValidationContext ctx, Object value) {
        ctx.check(stringNotEmptyValidator, value);
    }

    void validateStringLength(ValidationContext ctx, CharSequence value, int minLength, int maxLength) {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create(Validation.String.Length.class))
                .putValue("min", minLength)
                .putValue("value", maxLength)
                .build();
        ConstraintValidator validator = stringLengthProvider.create(CHAR_SEQUENCE_TYPE, annotation);
        ctx.check(validator, value);
    }

    void validateStringPattern(ValidationContext ctx, Object value, String pattern) {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create(Validation.String.Pattern.class))
                .value(pattern)
                .build();
        ConstraintValidator validator = stringPatternProvider.create(CHAR_SEQUENCE_TYPE, annotation);
        ctx.check(validator, value);
    }

    void validateNumberNegative(ValidationContext ctx, Object value) {
        ctx.check(numberNegativeValidator, value);
    }

    void validateNumberNegativeOrZero(ValidationContext ctx, Object value) {
        ctx.check(numberNegativeOrZeroValidator, value);
    }

    void validateNumberPositive(ValidationContext ctx, Object value) {
        ctx.check(numberPositiveValidator, value);
    }

    void validateNumberPositiveOrZero(ValidationContext ctx, Object value) {
        ctx.check(numberPositiveOrZeroValidator, value);
    }

    void validateNumberMin(ValidationContext ctx, Number value, String minValue) {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create(Validation.Number.Min.class))
                .value(minValue)
                .build();
        ConstraintValidator validator = numberMinProvider.create(NUMBER_TYPE, annotation);
        ctx.check(validator, value);
    }

    void validateNumberMax(ValidationContext ctx, Number value, String maxValue) {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create(Validation.Number.Max.class))
                .value(maxValue)
                .build();
        ConstraintValidator validator = numberMaxProvider.create(NUMBER_TYPE, annotation);
        ctx.check(validator, value);
    }

    void validateNumberDigits(ValidationContext ctx, Number value, int integerDigits, int fractionDigits) {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create(Validation.Number.Digits.class))
                .putValue("integer", integerDigits)
                .putValue("fraction", fractionDigits)
                .build();
        ConstraintValidator validator = numberDigitsProvider.create(NUMBER_TYPE, annotation);
        ctx.check(validator, value);
    }

    void validateIntegerMin(ValidationContext ctx, Integer value, int minValue) {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create(Validation.Integer.Min.class))
                .putValue("value", minValue)
                .build();
        ConstraintValidator validator = integerMinProvider.create(TypeNames.BOXED_INT, annotation);
        ctx.check(validator, value);
    }

    void validateIntegerMax(ValidationContext ctx, Object value, int maxValue) {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create(Validation.Integer.Max.class))
                .putValue("value", maxValue)
                .build();
        ConstraintValidator validator = integerMaxProvider.create(TypeNames.BOXED_INT, annotation);
        ctx.check(validator, value);
    }

    void validateLongMin(ValidationContext ctx, Long value, long minValue) {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create(Validation.Long.Min.class))
                .putValue("value", minValue)
                .build();
        ConstraintValidator validator = longMinProvider.create(TypeNames.BOXED_LONG, annotation);
        ctx.check(validator, value);
    }

    void validateLongMax(ValidationContext ctx, Long value, long maxValue) {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create(Validation.Long.Max.class))
                .putValue("value", maxValue)
                .build();
        ConstraintValidator validator = longMaxProvider.create(TypeNames.BOXED_LONG, annotation);
        ctx.check(validator, value);
    }

    void validateBooleanTrue(ValidationContext ctx, Object value) {
        ctx.check(booleanTrueValidator, value);
    }

    void validateBooleanFalse(ValidationContext ctx, Object value) {
        ctx.check(booleanFalseValidator, value);
    }

    void validateCalendarFuture(ValidationContext ctx, Object value) {
        Annotation annotation = Annotation.create(Validation.Calendar.Future.class);

        ConstraintValidator validator = calendarFutureProvider.create(calendarType(value), annotation);
        ctx.check(validator, value);
    }

    void validateCalendarFutureOrPresent(ValidationContext ctx, Object value) {
        Annotation annotation = Annotation.create(Validation.Calendar.FutureOrPresent.class);

        ConstraintValidator validator = calendarFutureOrPresentProvider.create(calendarType(value), annotation);
        ctx.check(validator, value);
    }

    void validateCalendarPast(ValidationContext ctx, Object value) {
        Annotation annotation = Annotation.create(Validation.Calendar.Past.class);

        ConstraintValidator validator = calendarPastProvider.create(calendarType(value), annotation);
        ctx.check(validator, value);
    }

    void validateCalendarPastOrPresent(ValidationContext ctx, Object value) {
        Annotation annotation = Annotation.create(Validation.Calendar.PastOrPresent.class);

        ConstraintValidator validator = calendarPastOrPresentProvider.create(calendarType(value), annotation);
        ctx.check(validator, value);
    }

    void validateCollectionSize(ValidationContext ctx, Object value, int minSize, int maxSize) {
        Annotation annotation = Annotation.builder()
                .typeName(TypeName.create(Validation.Collection.Size.class))
                .putValue("min", minSize)
                .putValue("value", maxSize)
                .build();
        TypeName typeName;
        if (value.getClass().isArray()) {
            typeName = TypeName.create(value.getClass());
        } else {
            typeName = switch (value) {
                case Map<?, ?> ignored -> TypeNames.MAP;
                case Set<?> ignored -> TypeNames.SET;
                case List<?> ignored -> TypeNames.LIST;
                default -> throw new ValidationException(
                        "Collection size constraint is only valid on an array, collection, or a "
                                + "map.");
            };
        }
        ConstraintValidator validator = collectionSizeProvider.create(typeName, annotation);
        ctx.check(validator, value);
    }

    private static TypeName calendarType(Object value) {
        if (value == null) {
            return TypeName.create(Instant.class);
        }

        return switch (value) {
            case Date ignored -> TypeName.create(Date.class);
            case Calendar ignored -> TypeName.create(Calendar.class);
            case Instant ignored -> TypeName.create(Instant.class);
            case LocalDate ignored -> TypeName.create(LocalDate.class);
            case LocalDateTime ignored -> TypeName.create(LocalDateTime.class);
            case LocalTime ignored -> TypeName.create(LocalTime.class);
            case MonthDay ignored -> TypeName.create(MonthDay.class);
            case OffsetDateTime ignored -> TypeName.create(OffsetDateTime.class);
            case OffsetTime ignored -> TypeName.create(OffsetTime.class);
            case Year ignored -> TypeName.create(Year.class);
            case YearMonth ignored -> TypeName.create(YearMonth.class);
            case ZonedDateTime ignored -> TypeName.create(ZonedDateTime.class);
            case HijrahDate ignored -> TypeName.create(HijrahDate.class);
            case JapaneseDate ignored -> TypeName.create(JapaneseDate.class);
            case MinguoDate ignored -> TypeName.create(MinguoDate.class);
            case ThaiBuddhistDate ignored -> TypeName.create(ThaiBuddhistDate.class);
            default -> throw new ValidationException("Invalid type for calendar validation constraint: "
                                                             + value.getClass().getName());
        };
    }
}
