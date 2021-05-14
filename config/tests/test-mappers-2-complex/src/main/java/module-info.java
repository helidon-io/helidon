/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

/**
 * Integration tests of ConfigMapper implementations available in 'module-mappers-1-base'
 * as well as 'module-mappers-2-override' module.
 */
module io.helidon.config.tests.mappers2 {

    requires io.helidon.config.tests.module.mappers1;
    requires io.helidon.config.tests.module.mappers2;

}
