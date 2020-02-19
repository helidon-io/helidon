/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.testing;

import io.helidon.config.spi.ConfigNode;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

/**
 * Hamcrest {@link org.hamcrest.Matcher} implementation that matches {@link ConfigNode.ValueNode} value.
 */
public final class ValueNodeMatcher extends BaseMatcher<ConfigNode> {

    private String expectedValue;

    private ValueNodeMatcher(String expectedValue) {
        this.expectedValue = expectedValue;
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(this.expectedValue);
    }

    @Override
    public boolean matches(Object actualValue) {
        if (actualValue instanceof ConfigNode.ValueNode) {
            return expectedValue.equals(((ConfigNode.ValueNode) actualValue).get());
        }
        return false;
    }

    /**
     * Creates new instance of {@link ValueNodeMatcher} that matches {@link ConfigNode.ValueNode}
     * with spacified {@code expectedValue}.
     *
     * @param expectedValue expected value holded by {@link ConfigNode.ValueNode}
     * @return new instance of {@link ValueNodeMatcher}
     */
    public static ValueNodeMatcher valueNode(String expectedValue) {
        return new ValueNodeMatcher(expectedValue);
    }

}
