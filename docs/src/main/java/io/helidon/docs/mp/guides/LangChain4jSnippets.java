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

package io.helidon.docs.mp.guides;

import java.util.function.Supplier;

import io.helidon.integrations.langchain4j.Ai;
import io.helidon.service.registry.Service;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@SuppressWarnings("ALL")
class LangChain4jSnippets {
    class Base {
        // tag::base_ai_service[]
        @Ai.Service
        public interface PirateService {

            @SystemMessage("""
            You are a pirate who like to tell stories about his time.
            """)
            String chat(String prompt);
        }
        // end::base_ai_service[]

        // tag::base_resource[]
        @Path("/chat")
        public class PirateResource {

            @Inject
            PirateService pirateService; //<1>

            @POST
            public String chat(String message) {
                return pirateService.chat(message);
            }
        }
        // end::base_resource[]
    }

    class Template {
        // tag::template_ai_service[]
        @Ai.Service
        public interface PirateService {

            @SystemMessage("""
            You are a pirate who like to tell stories about his time
            at the sea with captain {&ZeroWidthSpace;{capt-name}&ZeroWidthSpace;}.
            """)
            String chat(@V("capt-name") String captName,
                        @UserMessage String prompt);
        }
        // end::template_ai_service[]

        // tag::template_resource[]
        @Path("/chat")
        public class PirateResource {

            @Inject
            PirateService pirateService;

            @POST
            public String chat(String message) {
                return pirateService.chat("Frank", message);
            }

        }
        // end::template_resource[]
    }

    class Memory {
        // tag::memory_provider[]
        @Service.Singleton
        @Service.Named(PirateMemoryProvider.NAME)
        public class PirateMemoryProvider implements Supplier<ChatMemoryProvider> {

            static final String NAME = "pirate-memory";

            @Override
            public ChatMemoryProvider get() {
                return memoryId -> MessageWindowChatMemory.builder()
                        .maxMessages(10)
                        .id(memoryId)
                        .chatMemoryStore(new InMemoryChatMemoryStore()).build();
            }
        }
        // end::memory_provider[]

        // tag::memory_ai_service[]
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
        // end::memory_ai_service[]

        // tag::memory_resource[]
        @Path("/chat")
        public class PirateResource {

            @Inject
            PirateService pirateService;

            @POST
            public String chat(@HeaderParam("conversation-id") String conversationId,
                               String message) {
                return pirateService.chat(conversationId, "Frank", message);
            }
        }
        // end::memory_resource[]
    }
}
