/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Unit tests for class {@link InputStreamHelper}.
 *
 * @see InputStreamHelper
 *
 */
public class InputStreamHelperTest{

  @Test
  public void testReadAllBytes() throws IOException {
      byte[] byteArray = new byte[7];
      byteArray[2] = (byte) 22;
      InputStream byteArrayInputStream = new ByteArrayInputStream(byteArray);
      byte[] byteArrayTwo = InputStreamHelper.readAllBytes(byteArrayInputStream);

      assertArrayEquals(new byte[] {(byte)0, (byte)0, (byte)22, (byte)0, (byte)0, (byte)0, (byte)0}, byteArray);
      assertArrayEquals(new byte[] {(byte)0, (byte)0, (byte)22, (byte)0, (byte)0, (byte)0, (byte)0}, byteArrayTwo);

      assertNotSame(byteArray, byteArrayTwo);
      assertNotSame(byteArrayTwo, byteArray);
      assertEquals(7, byteArray.length);
      assertEquals(7, byteArrayTwo.length);
      assertEquals(0, byteArrayInputStream.available());
  }

}