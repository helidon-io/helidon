/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.MatcherAssert.assertThat;

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

      assertThat(claszResult.isInterface(), is(false));
      assertThat(claszResult.isArray(), is(false));
      assertThat(claszResult.toString(), is("class java.lang.Object"));
      assertThat(claszResult.getModifiers(), is(1));
      assertThat(claszResult.isEnum(), is(false));
      assertThat(claszResult.isSynthetic(), is(false));
      assertThat(claszResult.isAnnotation(), is(false));
      assertThat(claszResult.isPrimitive(), is(false));
  }

  @Test
  public void testRawClassThrowsIllegalArgumentException() {
      assertThrows(IllegalArgumentException.class, () -> GenericTypeUtil.rawClass(null));
  }

}