/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import io.helidon.common.http.Http;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * The StatusTypeTest.
 */
public class StatusTypeTest {

    @Test
    public void adHocStatusTypeObjectPropertiesTest() throws Exception {
        assertThat(Http.ResponseStatus.create(200) == Http.Status.OK_200, is(true));
        assertThat(Http.ResponseStatus.create(200).hashCode() == Http.Status.OK_200.hashCode(), is(true));
        assertThat(Http.ResponseStatus.create(200).equals(Http.Status.OK_200), is(true));

        assertThat(Http.ResponseStatus.create(999).equals(Http.ResponseStatus.create(999)), is(true));
        // TODO if we were able to maintain even the '==' property, it would be awesome (cache the created ones?)
        assertThat(Http.ResponseStatus.create(999) != Http.ResponseStatus.create(999), is(true));
        assertThat(Http.ResponseStatus.create(999).hashCode() == Http.ResponseStatus.create(999).hashCode(), is(true));
    }
}
