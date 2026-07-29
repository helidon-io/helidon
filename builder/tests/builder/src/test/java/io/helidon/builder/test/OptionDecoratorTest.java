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

package io.helidon.builder.test;

import io.helidon.builder.test.testsubjects.OptionDecorators;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class OptionDecoratorTest {
    @Test
    @SuppressWarnings("deprecation")
    void deprecatedPrimitiveGetterRetainsDefault() {
        var builder = OptionDecorators.builder();

        assertThat(builder.legacyValue(), is(42));
        assertThat(builder.replacementValue(), is(42));
    }

    @Test
    void copyDoesNotApplyUnsetDeprecatedAlias() {
        var source = OptionDecorators.builder().replacementValue(64);

        var copy = OptionDecorators.builder().from(source);

        assertThat(copy.replacementValue(), is(64));
    }

    @Test
    @SuppressWarnings("deprecation")
    void copyAppliesExplicitDeprecatedAlias() {
        var source = OptionDecorators.builder().legacyValue(0);

        var copy = OptionDecorators.builder().from(source);

        assertThat(copy.replacementValue(), is(0));
        assertThat(copy.legacyValue(), is(0));
    }

    @Test
    void deprecatedPrimitiveGetterDescriptorIsPreserved() throws NoSuchMethodException {
        var method = OptionDecorators.BuilderBase.class.getMethod("legacyValue");

        assertThat(method.getReturnType(), sameInstance(int.class));
    }
}
