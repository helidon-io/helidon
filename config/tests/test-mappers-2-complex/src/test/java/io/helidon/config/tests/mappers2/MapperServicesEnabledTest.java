/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.tests.mappers2;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests ConfigMapper implementations loaded automatically from {@code module-mappers-1-base}
 * as well as {@code module-mappers-2-override} modules on path.
 */
public class MapperServicesEnabledTest extends AbstractMapperServicesTest {

    /**
     * Logger ConfigMapper used from {@code module-mappers-1-base} module.
     */
    @Test
    public void testLogger() {
        assertThat(getLogger().getName(), is("io.helidon.config.tests.mappers2.MapperServicesEnabledTest"));
    }

    /**
     * Locale ConfigMapper used from {@code module-mappers-2-override} module.
     */
    @Test
    public void testLocale() {
        assertThat(getLocale(), is(new Locale("m2:cs", "m2:CZ", "m2:Praha")));
    }

}
