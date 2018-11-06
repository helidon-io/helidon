/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * AuditEvent Junit test.
 */
public class AuditEventTest {
    @Test
    void testParam() {
        String name = "paramName";
        String value = "plainValue";

        AuditEvent.AuditParam param = AuditEvent.AuditParam.plain(name, value);

        assertThat(param.getName(), is(name));
        assertThat(param.getValue().get(), is(value));
        assertThat(param.isSensitive(), is(false));
        assertThat(param.toString(), containsString(name));
        assertThat(param.toString(), containsString(value));

        Assertions.assertEquals(name, param.getName());
    }

    @Test
    public void testSensitiveParam() {
        String name = "paramName";
        String value = "sensitiveValue";

        AuditEvent.AuditParam param = AuditEvent.AuditParam.sensitive(name, value);

        assertThat(param.getName(), is(name));
        assertThat(param.getValue().get(), is(value));
        assertThat(param.isSensitive(), is(true));
        assertThat(param.toString(), containsString(name));
        assertThat(param.toString(), not(containsString(value)));
    }
}
