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

package io.helidon.service.codegen.spi;

import io.helidon.common.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class InjectAssignmentTest {
    @Test
    void rejectsNullUsedType() {
        assertThrows(NullPointerException.class, () -> InjectAssignment.Assignment.create(null, _ -> { }));
    }

    @Test
    void rejectsNullCodeGenerator() {
        assertThrows(NullPointerException.class,
                     () -> InjectAssignment.Assignment.create(TypeName.create(String.class), null));
    }
}
