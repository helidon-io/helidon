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
package io.helidon.testing.junit5.suite.spi;

/**
 * Integration tests suite provider.
 * Any {@link io.helidon.testing.junit5.suite.TestSuite} class must implement this interface.
 * <p>
 * A Suite may provide methods annotated with {@link io.helidon.testing.junit5.suite.TestSuite.BeforeSuite} and/or
 * {@link io.helidon.testing.junit5.suite.TestSuite.AfterSuite}.
 * Currently supported parameter is the {@link io.helidon.testing.junit5.suite.SuiteStorage}.
 *
 * @deprecated this is a feature in progress of development, there may be backward incompatible changes done to it, so please
 *         use with care
 */
@Deprecated
public interface SuiteProvider {
}
