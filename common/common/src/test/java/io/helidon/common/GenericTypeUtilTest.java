/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for class {@link GenericTypeUtil}.
 *
 * @see GenericTypeUtil
 *
 */
public class GenericTypeUtilTest{

  @Test
  public void testRawClass() {
      Class<?> claszResult = GenericTypeUtil.rawClass(Object.class);

      assertFalse(claszResult.isInterface());
      assertFalse(claszResult.isArray());
      assertEquals("class java.lang.Object", claszResult.toString());
      assertEquals(1, claszResult.getModifiers());
      assertFalse(claszResult.isEnum());
      assertFalse(claszResult.isSynthetic());
      assertFalse(claszResult.isAnnotation());
      assertFalse(claszResult.isPrimitive());
  }

  @Test
  public void testRawClassThrowsIllegalArgumentException() {
      assertThrows(IllegalArgumentException.class, () -> GenericTypeUtil.rawClass(null));
  }

}