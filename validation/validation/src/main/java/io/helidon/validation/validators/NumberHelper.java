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

import java.math.BigDecimal;
import java.math.BigInteger;

final class NumberHelper {
    private NumberHelper() {
    }

    static BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal bd) {
            return bd.stripTrailingZeros();
        } else if (number instanceof BigInteger bi) {
            return new BigDecimal(bi);
        } else if (number instanceof Byte b) {
            return new BigDecimal(b & 0xFF);
        } else {
            return new BigDecimal(String.valueOf(number.doubleValue())).stripTrailingZeros();
        }
    }
}
