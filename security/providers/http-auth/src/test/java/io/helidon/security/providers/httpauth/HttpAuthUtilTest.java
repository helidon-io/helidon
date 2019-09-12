/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.providers.httpauth;

import java.security.SecureRandom;
import java.util.Random;

import javax.crypto.Cipher;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link HttpAuthUtil}.
 */
public class HttpAuthUtilTest {
    @Test
    public void positiveTestLong() throws Exception {
        testToAndBack(45545554L);
        testToAndBack(Long.MAX_VALUE);
        testToAndBack(Long.MIN_VALUE);
    }

    private void testToAndBack(long longValue) {
        byte[] bytes = HttpAuthUtil.toBytes(longValue);
        assertThat(HttpAuthUtil.toLong(bytes, 0, bytes.length), is(longValue));
    }

    @Test
    public void negativeTetLong() {
        byte[] bytes = HttpAuthUtil.toBytes(455687);
        try {
            HttpAuthUtil.toLong(bytes, 0, 1);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("Wrong length"));
        }

        try {
            HttpAuthUtil.toLong(bytes, 4, bytes.length);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("capacity of the array"));
        }
    }

    @Test
    public void cipher() throws Exception {
        byte[] salt = new byte[16];
        byte[] aesNonce = new byte[12];
        Random r = new SecureRandom();
        r.nextBytes(salt);
        r.nextBytes(aesNonce);
        Cipher cipher = HttpAuthUtil.cipher("pwd".toCharArray(), salt, aesNonce, Cipher.ENCRYPT_MODE);

        assertThat(cipher, notNullValue());
    }

}
