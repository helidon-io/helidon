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
package io.helidon.data.jdbc.tests.imperative.h2;

import io.helidon.data.jdbc.tests.contract.AbstractJdbcScalarContract;
import io.helidon.data.jdbc.tests.database.H2Database;
import io.helidon.data.jdbc.tests.support.TestConfigFactory;

/**
 * Executes the imperative scalar contract against H2.
 */
class H2ImperativeScalarTest extends AbstractJdbcScalarContract {
    @Override
    protected void beforeStartApplication() {
        TestConfigFactory.config(H2Database.config());
    }
}
