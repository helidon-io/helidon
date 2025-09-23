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

class SchemaDecorator implements Prototype.BuilderDecorator<Schema.BuilderBase<?, ?>> {

    @Override
    public void decorate(Schema.BuilderBase<?, ?> target) {
        target.rootObject().ifPresent(target::root);
        addRoot(target, target.rootArray());
        addRoot(target, target.rootInteger());
        addRoot(target, target.rootNumber());
        addRoot(target, target.rootString());
        addRoot(target, target.rootBoolean());
        addRoot(target, target.rootNull());
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static void addRoot(Schema.BuilderBase<?, ?> target, Optional<? extends SchemaItem> item) {
        if (target.root().isEmpty()) {
            item.ifPresent(target::root);
        } else if (item.isPresent()) {
            throw new JsonSchemaException("Only one root type is supported");
        }
    }

}
