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

package io.helidon.json;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

enum GeneratorMethod {
    WRITER {
        @Override
        Target createTarget() {
            StringWriter writer = new StringWriter();
            return new Target() {
                @Override
                public JsonGenerator createGenerator() {
                    return JsonGenerator.create(writer);
                }

                @Override
                public JsonGenerator createPrettyGenerator() {
                    return JsonGenerator.create(writer, true);
                }

                @Override
                public String generatedJson() {
                    return writer.toString();
                }
            };
        }
    },
    OUTPUT_STREAM {
        @Override
        Target createTarget() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            return new Target() {
                @Override
                public JsonGenerator createGenerator() {
                    return JsonGenerator.create(outputStream);
                }

                @Override
                public JsonGenerator createPrettyGenerator() {
                    return JsonGenerator.create(outputStream, true);
                }

                @Override
                public String generatedJson() {
                    return outputStream.toString(StandardCharsets.UTF_8);
                }
            };
        }
    };

    abstract Target createTarget();

    interface Target {
        JsonGenerator createGenerator();

        JsonGenerator createPrettyGenerator();

        String generatedJson();
    }
}
