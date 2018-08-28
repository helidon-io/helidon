/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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
 * Testing module that contains other Config Parser implementations, overrides some from config core module.
 */
module io.helidon.config.tests.module.parsers1 {

    requires transitive io.helidon.config;
    requires io.helidon.common;

    exports io.helidon.config.tests.module.parsers1;

    provides io.helidon.config.spi.ConfigParser with
            io.helidon.config.tests.module.parsers1.Parsers1Priority100ConfigParser,
            io.helidon.config.tests.module.parsers1.Parsers1Priority300ConfigParser;

}
