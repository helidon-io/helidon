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

package io.helidon.builder.test.testsubjects;

import java.util.List;
import java.util.Set;

import io.helidon.builder.api.Prototype;

final class OptionDecoratorsSupport {
    private OptionDecoratorsSupport() {
    }

    static final class FooDecorator
            implements Prototype.OptionDecorator<OptionDecorators.BuilderBase<?, ?>, OptionDecoratorsBlueprint.Foo> {
        FooDecorator() {
            super();
        }

        @Override
        public void decorate(OptionDecorators.BuilderBase<?, ?> builder, OptionDecoratorsBlueprint.Foo optionValue) {
        }

        @Override
        public void decorateSetList(OptionDecorators.BuilderBase<?, ?> builder,
                                    List<OptionDecoratorsBlueprint.Foo> optionValues) {

        }

        @Override
        public void decorateAddList(OptionDecorators.BuilderBase<?, ?> builder,
                                    List<OptionDecoratorsBlueprint.Foo> optionValues) {

        }

        @Override
        public void decorateSetSet(OptionDecorators.BuilderBase<?, ?> builder,
                                   Set<OptionDecoratorsBlueprint.Foo> optionValues) {

        }

        @Override
        public void decorateAddSet(OptionDecorators.BuilderBase<?, ?> builder,
                                   Set<OptionDecoratorsBlueprint.Foo> optionValues) {

        }
    }

    static final class StringDecorator implements Prototype.OptionDecorator<OptionDecorators.BuilderBase<?, ?>, String> {
        StringDecorator() {
            super();
        }

        @Override
        public void decorate(OptionDecorators.BuilderBase<?, ?> builder, String optionValue) {
        }

        @Override
        public void decorateSetList(OptionDecorators.BuilderBase<?, ?> builder,
                                    List<String> optionValues) {

        }

        @Override
        public void decorateAddList(OptionDecorators.BuilderBase<?, ?> builder,
                                    List<String> optionValues) {

        }

        @Override
        public void decorateSetSet(OptionDecorators.BuilderBase<?, ?> builder, Set<String> optionValues) {
            Prototype.OptionDecorator.super.decorateSetSet(builder, optionValues);
        }

        @Override
        public void decorateAddSet(OptionDecorators.BuilderBase<?, ?> builder, Set<String> optionValues) {
            Prototype.OptionDecorator.super.decorateAddSet(builder, optionValues);
        }
    }

}
