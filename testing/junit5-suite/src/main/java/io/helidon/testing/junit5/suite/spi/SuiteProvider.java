/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.Api;

/**
 * Internal contract implemented by providers used with {@link io.helidon.testing.junit5.suite.TestSuite.Suite}.
 * <p>
 * A provider may define methods annotated with {@link io.helidon.testing.junit5.suite.TestSuite.BeforeSuite} and/or
 * {@link io.helidon.testing.junit5.suite.TestSuite.AfterSuite}.
 * Providers that also implement {@link io.helidon.testing.junit5.suite.SuiteStorage} receive
 * {@link io.helidon.testing.junit5.suite.Storage} in those callbacks.
 */
@Api.Internal
public interface SuiteProvider {
}
