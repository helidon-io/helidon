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

package io.helidon.declarative.codegen.http.webserver.compat;

import java.util.List;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.declarative.codegen.http.webserver.AbstractParametersProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class AbstractParametersProviderCompatibilityTest {
    @SuppressWarnings("removal")
    @Test
    void legacyProtectedHelperKeepsPreBackportOutput() {
        LegacyProvider provider = new LegacyProvider();
        TypeName optionalList = TypeName.builder(TypeNames.OPTIONAL)
                .addTypeArgument(TypeName.builder(TypeNames.LIST)
                                         .addTypeArgument(TypeNames.STRING)
                                         .build())
                .build();

        String generated = provider.generate(optionalList, "fieldNames", true);

        assertThat(generated, containsString(".first(\"fieldNames\").as(List<String>.class).asOptional()"));
        assertThat(generated, not(containsString("GenericType")));
        assertThat(generated, not(containsString("mappers.map(")));
    }

    private static final class LegacyProvider extends AbstractParametersProvider {
        String generate(TypeName parameterType, String parameterName, boolean optional) {
            TestContentBuilder contentBuilder = new TestContentBuilder();
            codegenFromParameters(contentBuilder, parameterType, parameterName, optional);
            return contentBuilder.generatedString();
        }

        @Override
        protected String providerType() {
            return "Query parameter";
        }
    }

    private static final class TestContentBuilder implements ContentBuilder<TestContentBuilder> {
        private final StringBuilder content = new StringBuilder();

        @Override
        public TestContentBuilder content(List<String> content) {
            this.content.setLength(0);
            this.content.append(String.join("\n", content));
            return this;
        }

        @Override
        public TestContentBuilder addContent(String line) {
            content.append(line);
            return this;
        }

        @Override
        public TestContentBuilder addTypeToContent(String typeName) {
            content.append(typeName.replace("java.lang.", "")
                                   .replace("java.util.", ""));
            return this;
        }

        @Override
        public TestContentBuilder padContent() {
            return this;
        }

        @Override
        public TestContentBuilder padContent(int repetition) {
            return this;
        }

        @Override
        public TestContentBuilder increaseContentPadding() {
            return this;
        }

        @Override
        public TestContentBuilder decreaseContentPadding() {
            return this;
        }

        @Override
        public TestContentBuilder clearContent() {
            content.setLength(0);
            return this;
        }

        String generatedString() {
            return content.toString();
        }
    }
}
