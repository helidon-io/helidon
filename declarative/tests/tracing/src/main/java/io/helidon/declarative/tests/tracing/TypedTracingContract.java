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

package io.helidon.declarative.tests.tracing;

import io.helidon.http.Http;
import io.helidon.service.registry.Service;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracing;

@SuppressWarnings("deprecation")
@Service.Contract
@Http.Path("/typed")
interface TypedTracingContract {
    @Http.GET
    @Http.Path("/method")
    @Tracing.Traced(value = "contract-method",
                    tags = @Tracing.Tag(key = "source", value = "contract-method"),
                    kind = Span.Kind.SERVER)
    String contractMethod();
}
