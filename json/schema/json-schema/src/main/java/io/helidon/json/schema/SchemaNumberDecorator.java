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

class SchemaNumberDecorator implements Prototype.BuilderDecorator<SchemaNumber.BuilderBase<?, ?>> {

    @Override
    public void decorate(SchemaNumber.BuilderBase<?, ?> target) {
        Optional<Double> minimum = target.minimum();
        Optional<Double> exclusiveMinimum = target.exclusiveMinimum();
        Optional<Double> maximum = target.maximum();
        Optional<Double> exclusiveMaximum = target.exclusiveMaximum();
        if (minimum.isPresent() && exclusiveMinimum.isPresent()) {
            throw new JsonSchemaException("Both minimum and exclusive minimum cannot be set at the same time");
        }
        if (maximum.isPresent() && exclusiveMaximum.isPresent()) {
            throw new JsonSchemaException("Both maximum and exclusive maximum cannot be set at the same time");
        }
        Optional<Double> minimumNumber = minimum.or(() -> exclusiveMinimum);
        Optional<Double> maximumNumber = maximum.or(() -> exclusiveMaximum);
        if (minimumNumber.isPresent() && maximumNumber.isPresent()) {
            if (minimumNumber.get() > maximumNumber.get()) {
                throw new JsonSchemaException("Minimum value cannot be greater than the maximum value");
            }
        }
        if (minimumNumber.isPresent() && minimumNumber.get() < 0) {
            throw new JsonSchemaException("Minimum value cannot be lower than 0");
        }
        if (maximumNumber.isPresent() && maximumNumber.get() < 0) {
            throw new JsonSchemaException("Maximum value cannot be lower than 0");
        }
    }

}
