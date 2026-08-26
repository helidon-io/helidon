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

package io.helidon.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

final class BigIntegerExpansionBudget {
    private static final int MAX_EXPANSION = 4_096;
    private static final int MAX_AGGREGATE_EXPANSION = 65_536;
    private static final int UNLIMITED = -1;
    private static final AtomicIntegerFieldUpdater<BigIntegerExpansionBudget> EXPANSION =
            AtomicIntegerFieldUpdater.newUpdater(BigIntegerExpansionBudget.class, "expansion");
    private static final BigIntegerExpansionBudget NO_AGGREGATE = new BigIntegerExpansionBudget(UNLIMITED);

    private volatile int expansion;

    BigIntegerExpansionBudget() {
        this(0);
    }

    private BigIntegerExpansionBudget(int expansion) {
        this.expansion = expansion;
    }

    static BigIntegerExpansionBudget noAggregate() {
        return NO_AGGREGATE;
    }

    static boolean requiresExpansion(BigDecimal value) {
        return value.signum() != 0 && value.scale() < 0;
    }

    BigInteger toBigInteger(BigDecimal value) {
        return convert(value, null, null);
    }

    BigInteger toBigInteger(BigDecimal value, JsonParser parser) {
        return convert(value, Objects.requireNonNull(parser), null);
    }

    BigInteger toBigInteger(BigDecimal value,
                            JsonParser parser,
                            BigIntegerExpansionBudget additionalBudget) {
        return convert(value, Objects.requireNonNull(parser), Objects.requireNonNull(additionalBudget));
    }

    boolean aggregateLimited() {
        return expansion != UNLIMITED;
    }

    private BigInteger convert(BigDecimal value,
                               JsonParser parser,
                               BigIntegerExpansionBudget additionalBudget) {
        int scale = value.scale();
        if (value.signum() == 0 || (scale > 0 && value.precision() <= scale)) {
            return BigInteger.ZERO;
        }
        long requestedExpansion = scale < 0 ? -(long) scale : 0;
        if (requestedExpansion > MAX_EXPANSION) {
            String message = "BigInteger conversion exceeds the maximum supported decimal expansion of "
                    + MAX_EXPANSION + " digits";
            throw parser == null ? new JsonException(message) : parser.createException(message);
        }
        if (requestedExpansion != 0) {
            int expansion = (int) requestedExpansion;
            if (!reserve(expansion)
                    || (additionalBudget != null
                            && !additionalBudget.reserveExcept(expansion, this))) {
                String message = "BigInteger conversions exceed the maximum supported aggregate decimal expansion of "
                        + MAX_AGGREGATE_EXPANSION + " digits";
                throw parser == null ? new JsonException(message) : parser.createException(message);
            }
        }
        return value.toBigInteger();
    }

    private boolean reserve(int requestedExpansion) {
        if (!aggregateLimited()) {
            return true;
        }
        int current;
        int updated;
        do {
            current = expansion;
            if (requestedExpansion > MAX_AGGREGATE_EXPANSION - current) {
                return false;
            }
            updated = current + requestedExpansion;
        } while (!EXPANSION.compareAndSet(this, current, updated));
        return true;
    }

    private boolean reserveExcept(int requestedExpansion, BigIntegerExpansionBudget excluded) {
        return excluded == this || reserve(requestedExpansion);
    }
}
