/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.json.schema;

import java.util.Optional;

import io.helidon.builder.api.Prototype;

class SchemaArrayDecorator implements Prototype.BuilderDecorator<SchemaArray.BuilderBase<?, ?>> {

    @Override
    public void decorate(SchemaArray.BuilderBase<?, ?> target) {
        target.schemaType(SchemaType.ARRAY);
        target.itemsObject().ifPresent(target::items);
        addRoot(target, target.itemsArray());
        addRoot(target, target.itemsInteger());
        addRoot(target, target.itemsNumber());
        addRoot(target, target.itemsString());
        addRoot(target, target.itemsBoolean());
        addRoot(target, target.itemsNull());

        Optional<Integer> minItems = target.minItems();
        Optional<Integer> maxItems = target.maxItems();
        if (minItems.isPresent() && maxItems.isPresent()) {
            if (minItems.get() > maxItems.get()) {
                throw new JsonSchemaException("Minimum items value cannot be greater than the maximum value");
            }
        }
        if (minItems.isPresent() && minItems.get() < 0) {
            throw new JsonSchemaException("Minimum items cannot be lower than 0");
        }
        if (maxItems.isPresent() && maxItems.get() < 0) {
            throw new JsonSchemaException("Maximum items cannot be lower than 0");
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static void addRoot(SchemaArray.BuilderBase<?, ?> target, Optional<? extends SchemaItem> item) {
        if (target.items().isEmpty()) {
            item.ifPresent(target::items);
        } else if (item.isPresent()) {
            throw new JsonSchemaException("Only one array items type is supported");
        }
    }

}
