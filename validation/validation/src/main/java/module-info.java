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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Helidon validation module.
 * <p>
 * Validation allows validating objects before they are used in business logic.
 * This is achieved by either validating a type annotated with {@link io.helidon.validation.Validation.Validated},
 * or by intercepting a method that has one of the constraint annotations
 * (such as {@link io.helidon.validation.Validation.NotNull}), or the {@link io.helidon.validation.Validation.Valid} annotation to
 * validate a type.
 * <p>
 * Constraints are implemented as services that implement the {@link io.helidon.validation.spi.ConstraintValidator} contract,
 * and that are named with the fully qualified name of the constraint annotation. Build-in validators have lower than default
 * weight, so they can be overridden by custom validators.
 * <p>
 * To enable validation of a type, it MUST be annotated with {@link io.helidon.validation.Validation.Validated},
 * and Helidon declarative codegen must be on the annotation processor path. This will generate a type validator service.
 * <p>
 * To enable interception of validated methods, the method, its parameter, or a generic type argument must be annotated with
 * one of the constraints in {@link io.helidon.validation.Validation} class, or the {@link io.helidon.validation.Validation.Valid}
 * annotation, and the same codegen module must be on the annotation processor path.
 * <p>
 * The constraints are grouped (through container classes) based on types that can be constrained.
 * Some constraints are for convenience - such as {@link io.helidon.validation.Validation.Integer.Max} and
 * {@link io.helidon.validation.Validation.Long.Max} - this allows usage of properly typed constants, as the general number
 * constraint {@link io.helidon.validation.Validation.Number.Max} must use a String value (as it can be used for any number,
 * ranging from {@link java.lang.Byte} to a {@link java.math.BigDecimal}.
 *
 * <h2>Object constraints</h2>
 * Any type can be annotated with (see details in the following list):
 * <ul>
 *     <li>{@link io.helidon.validation.Validation.Valid} - as long as the class/interface is
 *     {@link io.helidon.validation.Validation.Validated}, will validate the instance (deep validation)</li>
 *     <li>{@link io.helidon.validation.Validation.NotNull} - will check that the value is not null</li>
 *     <li>{@link io.helidon.validation.Validation.Null} - will check that the value is null</li>
 * </ul>
 *
 * <h2>String constraints</h2>
 * The following constraints can be used on {@link java.lang.String} and {@link java.lang.CharSequence}:
 * <ul>
 *     <li>{@link io.helidon.validation.Validation.String.Length} - minimal and/or maximal length</li>
 *     <li>{@link io.helidon.validation.Validation.String.NotBlank} - will check that the value is not blank</li>
 *     <li>{@link io.helidon.validation.Validation.String.NotEmpty} - will check that the value is not empty</li>
 *     <li>{@link io.helidon.validation.Validation.String.Pattern} - will check that the value matches the regular expression</li>
 *     <li>{@link io.helidon.validation.Validation.String.Email} - will check that the value matches e-mail structure</li>
 * </ul>
 *
 * <h2>Integer constraints</h2>
 * The following constraints can be used on {@link java.lang.Integer},
 * {@link java.lang.Long}, {@link java.lang.Short}, {@link java.lang.Byte}, and {@link java.lang.Character}:
 * <ul>
 *     <li>{@link io.helidon.validation.Validation.Integer.Max} - maximal value</li>
 *     <li>{@link io.helidon.validation.Validation.Integer.Min} - minimal value</li>
 * </ul>
 * Note that byte is always considered to be unsigned (i.e. values are from 0 to 255 inclusive).
 *
 * <h2>Long constraints</h2>
 * The following constraints can be used ONLY on {@link java.lang.Long}:
 * <ul>
 *     <li>{@link io.helidon.validation.Validation.Long.Max} - maximal value</li>
 *     <li>{@link io.helidon.validation.Validation.Long.Min} - minimal value</li>
 * </ul>
 *
 * <h2>Number constraints</h2>
 * The following constraints can be used on {@link java.lang.Number} and its implementations:
 * <ul>
 *     <li>{@link io.helidon.validation.Validation.Number.Digits} - max number of integer and fractional digits</li>
 *     <li>{@link io.helidon.validation.Validation.Number.Max} - maximal value</li>
 *     <li>{@link io.helidon.validation.Validation.Number.Min} - minimal value</li>
 *     <li>{@link io.helidon.validation.Validation.Number.Negative} - number must be negative (negative zero for longs is a zero)</li>
 *     <li>{@link io.helidon.validation.Validation.Number.NegativeOrZero} - number must not be positive</li>
 *     <li>{@link io.helidon.validation.Validation.Number.Positive} - number must be positive (zero is not positive)</li>
 *     <li>{@link io.helidon.validation.Validation.Number.PositiveOrZero} - number must be positive or zero (negative zero is OK)</li>
 * </ul>
 * Note that byte is always considered to be unsigned (i.e. values are from 0 to 255 inclusive).
 */
@Features.Name("Validation")
@Features.Description("Validation of types and service method calls")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path("Validation")
@Features.Preview
module io.helidon.validation {
    requires static io.helidon.common.features.api;

    requires io.helidon.common.types;
    requires io.helidon.service.registry;
    requires io.helidon.common;

    exports io.helidon.validation;
    // only contains package private classes except for service descriptors
    exports io.helidon.validation.validators;
    exports io.helidon.validation.spi;
}