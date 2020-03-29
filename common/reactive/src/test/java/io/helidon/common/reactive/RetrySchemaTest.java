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
package io.helidon.common.reactive;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Simple unit test for the factory methods of {@link RetrySchema}.
 */
public class RetrySchemaTest {

    @Test
    public void testLinear() {
        RetrySchema schema = RetrySchema.linear(10, 10, 40);
        assertThat(schema.nextDelay(0, -1), is(equalTo(10L)));
        assertThat(schema.nextDelay(1, -1), is(equalTo(20L)));
        assertThat(schema.nextDelay(2, -1), is(equalTo(30L)));
        assertThat(schema.nextDelay(3, -1), is(equalTo(40L)));
        assertThat(schema.nextDelay(4, -1), is(equalTo(40L)));
    }

    @Test
    public void testGeometric() {
        RetrySchema schema = RetrySchema.geometric(10, 2, 40);
        assertThat(schema.nextDelay(0, -1), is(equalTo(10L)));
        assertThat(schema.nextDelay(1, 10), is(equalTo(20L)));
        assertThat(schema.nextDelay(1, 20), is(equalTo(40L)));
        assertThat(schema.nextDelay(1, 30), is(equalTo(40L)));
        assertThat(RetrySchema.geometric(50, 2, 40).nextDelay(0, -1), is(equalTo(40L)));
    }

    @Test
    public void testConstant() {
        RetrySchema schema = RetrySchema.constant(100);
        assertThat(schema.nextDelay(-1, -1), is(equalTo(100L)));
    }
}
