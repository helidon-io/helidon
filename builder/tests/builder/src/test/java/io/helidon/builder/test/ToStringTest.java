/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.builder.test;

import io.helidon.builder.test.testsubjects.tostring.ArrayValues;
import io.helidon.builder.test.testsubjects.tostring.Secret;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class ToStringTest {
    @Test
    void testToStringWithArrays() {
        var builder = ArrayValues.builder()
                .data(new byte[] {1, 2, 3})
                .secretData(new byte[] {4, 5, 6})
                .secretChars("secret".toCharArray())
                .optionalSecretChars("optional-secret".toCharArray());
        var value = builder.build();

        assertThat(builder.toString(),
                   is("ArrayValuesBuilder{data=[1, 2, 3],secretData=****,secretChars=****,optionalSecretChars=****}"));
        assertThat(value.toString(),
                   is("ArrayValues{data=[1, 2, 3],secretData=****,secretChars=****,optionalSecretChars=****}"));
    }

    @Test
    void testToStringWithEmptyArrays() {
        var builder = ArrayValues.builder()
                .data(new byte[0])
                .secretData(new byte[0])
                .secretChars(new char[0]);
        var value = builder.build();

        assertThat(builder.toString(),
                   is("ArrayValuesBuilder{data=[],secretData=****,secretChars=****,optionalSecretChars=null}"));
        assertThat(value.toString(),
                   is("ArrayValues{data=[],secretData=****,secretChars=****,optionalSecretChars=null}"));
    }

    @Test
    void testToStringWithUnsetArrays() {
        assertThat(ArrayValues.builder().toString(),
                   is("ArrayValuesBuilder{data=null,secretData=null,secretChars=null,optionalSecretChars=null}"));
    }

    @Test
    void testToStringWithConfidential() {
        var builder = Secret.builder()
                .name("public-name")
                .secret("secret-value");
        var secret = builder.build();

        assertThat(builder.toString(), containsString("public-name"));
        assertThat(builder.toString(), not(containsString("secret-value")));

        assertThat(secret.toString(), containsString("public-name"));
        assertThat(secret.toString(), not(containsString("secret-value")));
    }
}
