/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tools;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateHelperTest {

    @Test
    void bogusTemplateName() {
        TemplateHelper helper = TemplateHelper.create();
        ToolsException e = assertThrows(ToolsException.class,
                                              () -> helper.safeLoadTemplate("bogus.hbs"));
        assertThat(e.getMessage(), equalTo("failed to load: templates/pico/default/bogus.hbs"));
    }

    @Test
    void requiredArguments() {
        TemplateHelper helper = TemplateHelper.create();
        Set<String> args = helper.requiredArguments("this is {a} little {test}", Optional.of("{"), Optional.of("}"));
        assertThat(args, contains("a", "test"));

        args = helper.requiredArguments("this is a little test");
        assertThat(args, empty());

        args = helper.requiredArguments("this is a {{little}} test");
        assertThat(args, contains("little"));
    }

    @Test
    public void applyMustacheSubstitutions() {
        TemplateHelper helper = TemplateHelper.create();
        Map<String, Object> props = Collections.singletonMap("little", "big");

        String val = helper.applySubstitutions("", props, true);
        assertThat(val, equalTo(""));

        val = helper.applySubstitutions("this is a {{little}} test", props, true);
        assertThat(val, equalTo("this is a big test"));
    }

    @Test
    public void moduleInfoTemplate() {
        Map<String, Object> subst = new HashMap<>();
        TemplateHelper helper = TemplateHelper.create();
        String template = helper.safeLoadTemplate("module-info.hbs");

        Set<String> args = helper.requiredArguments(template);
        assertThat(args,
                   containsInAnyOrder("description", "hasdescription", "header", "generatedanno", "precomments", "items", "name"));

        String codegen = helper.applySubstitutions(template, subst, true).trim();
        assertThat(codegen,
                   equalTo("module  { \n"
                                   + "}"));

        subst.put("name", "my-module-name");
        subst.put("description", List.of("Description 1.", "Description 2."));
        subst.put("hasdescription", true);
        subst.put("header", "/*\n  Header Line 1\n  Header Line 2\n */");
        subst.put("generatedanno", helper.defaultGeneratedStickerFor("generator"));
        codegen = helper.applySubstitutions(template, subst, true);
        assertThat(codegen,
                   equalTo("/*\n"
                                   + "  Header Line 1\n"
                                   + "  Header Line 2\n"
                                   + " */\n"
                                   + "/**\n"
                                   + " * Description 1.\n"
                                   + " * Description 2.\n"
                                   + " */\n"
                                   + "// @Generated({\"provider=oracle\", \"generator=generator\", \"version=1\"})\n"
                                   + "module my-module-name { \n"
                                   + "}\n"));
    }

}
