/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.data.tests.codegen.mysql.scripts;

import java.util.List;

import io.helidon.data.DataConfig;
import io.helidon.data.DataRegistry;
import io.helidon.data.tests.application.ApplicationData;
import io.helidon.data.tests.model.Type;
import io.helidon.data.tests.repository.TypeRepository;
import io.helidon.testing.junit5.suite.Suite;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@Suite(MySqlSuite.class)
@Testcontainers(disabledWithoutDocker = true)
public class TestScripts {

    private static final System.Logger LOGGER = System.getLogger(MySqlSuite.class.getName());

    // Database shall be created and populated with data from create script.
    @Test
    void testScripts(DataConfig config) {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testScripts");
        ApplicationData applicationData = new ApplicationData(config);
        assertThat(applicationData.data(), notNullValue());
        DataRegistry data = applicationData.data();
        TypeRepository kindRepository = data.repository(TypeRepository.class);
        List<Type> kinds = kindRepository.findAll().toList();
        // KIND content initialized from script
        assertThat(kinds, hasSize(18));
        LOGGER.log(System.Logger.Level.DEBUG, "Finished testScripts");
    }

}
