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

package io.helidon.docs.includes.guides.lc4j.memory;
// tag::snippet_1[]
import io.helidon.integrations.langchain4j.Ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@Ai.Service
@Ai.ChatMemoryProvider(PirateMemoryProvider.NAME)
public interface PirateService {

    @SystemMessage("""
            You are a pirate who like to tell stories about his time
            at the sea with captain {&ZeroWidthSpace;{capt-name}&ZeroWidthSpace;}.
            """)
    String chat(@MemoryId String memoryId,
                @V("capt-name") String captName,
                @UserMessage String prompt);
}
// end::snippet_1[]