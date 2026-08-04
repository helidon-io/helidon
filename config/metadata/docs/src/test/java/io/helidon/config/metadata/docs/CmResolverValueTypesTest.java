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

package io.helidon.config.metadata.docs;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.config.metadata.model.CmModel;
import io.helidon.config.metadata.model.CmModel.CmModule;
import io.helidon.config.metadata.model.CmModel.CmOption;
import io.helidon.config.metadata.model.CmModel.CmType;
import io.helidon.config.metadata.model.CmResolver;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class CmResolverValueTypesTest {

    @Test
    void testSupportedValueTypesDoNotProduceWarnings() {
        var options = new ArrayList<>(List.of(
                        "io.helidon.common.Size",
                        "java.io.File",
                        "java.lang.Class",
                        "java.net.URL",
                        "java.nio.charset.Charset",
                        "java.nio.file.Path",
                        "java.time.LocalTime",
                        "java.time.OffsetTime",
                        "java.time.Period",
                        "java.time.YearMonth",
                        "java.time.ZoneId",
                        "java.time.ZoneOffset",
                        "java.util.UUID",
                        "java.util.regex.Pattern")
                .stream()
                .map(typeName -> CmOption.builder()
                        .key(CmType.simpleTypeName(typeName))
                        .type(typeName)
                        .build())
                .toList());
        options.add(CmOption.builder()
                .key("unsupported")
                .type("com.acme.Unsupported")
                .build());
        var model = CmModel.of(List.of(CmModule.of("com.acme", List.of(CmType.builder()
                .type("com.acme.AcmeConfig")
                .options(options)
                .build()))));
        var warnings = new ArrayList<String>();
        var logger = Logger.getLogger(CmResolver.class.getPackageName());
        var previousLevel = logger.getLevel();
        var previousUseParentHandlers = logger.getUseParentHandlers();
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                var parameters = record.getParameters();
                if (parameters == null) {
                    warnings.add(record.getMessage());
                } else {
                    warnings.add(MessageFormat.format(record.getMessage(), parameters));
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.WARNING);

        try {
            logger.addHandler(handler);
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.WARNING);

            CmResolver.create(model);
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }

        assertThat(warnings, contains(
                "Unresolved config metadata option type: com.acme.AcmeConfig.unsupported uses com.acme.Unsupported"));
    }
}
