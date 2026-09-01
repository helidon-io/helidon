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
package io.helidon.data.jdbc.tests.contract;

import io.helidon.data.jdbc.tests.declarative.repository.DeclarativeScalarBindingRepository;

import org.junit.jupiter.api.Test;

/**
 * Real-driver scalar contract for generated declarative repositories.
 */
public abstract class AbstractDeclarativeScalarContract extends AbstractJdbcScalarContract {

    /**
     * Returns the generated scalar repository from the active test registry.
     *
     * @return scalar repository
     */
    protected final DeclarativeScalarBindingRepository repository() {
        return service(DeclarativeScalarBindingRepository.class);
    }

    @Override
    protected final void bindAll(ScalarValues values) {
        repository().bindAll(values.booleanValue(),
                             values.byteValue(),
                             values.shortValue(),
                             values.integerValue(),
                             values.longValue(),
                             values.floatValue(),
                             values.doubleValue(),
                             values.decimalValue(),
                             values.stringValue(),
                             values.bytesValue(),
                             values.localDateValue(),
                             values.localTimeValue(),
                             values.localDateTimeValue(),
                             values.offsetTimeValue(),
                             values.offsetDateTimeValue(),
                             values.dateValue(),
                             values.timeValue(),
                             values.timestampValue());
    }

    /**
     * Proves generated code supplies the canonical JDBC null type for every supported reference scalar.
     */
    @Test
    protected final void bindsEveryGeneratedTypedNullThroughTheRealDriver() {
        bindAll(nullValues());
        assertAllNull();
    }
}
