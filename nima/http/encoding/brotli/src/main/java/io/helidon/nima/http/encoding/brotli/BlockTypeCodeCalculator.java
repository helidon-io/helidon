/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.http.encoding.brotli;

class BlockTypeCodeCalculator {
    int lastType;
    int secondLastType;

    BlockTypeCodeCalculator() {
    }

    public static void initBlockTypeCodeCalculator(BlockTypeCodeCalculator self) {
        self.lastType = 1;
        self.secondLastType = 0;
    }

    public static int nextBlockTypeCode(BlockTypeCodeCalculator calculator, int type) {
        int type_code = (type == calculator.lastType + 1) ? 1 :
                (type == calculator.secondLastType) ? 0 : type + 2;
        calculator.secondLastType = calculator.lastType;
        calculator.lastType = type;
        return type_code;
    }
}
