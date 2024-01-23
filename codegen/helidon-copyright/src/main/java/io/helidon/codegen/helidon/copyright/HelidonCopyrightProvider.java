/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.codegen.helidon.copyright;

import java.time.LocalDate;

import io.helidon.codegen.spi.CopyrightProvider;
import io.helidon.common.Weight;
import io.helidon.common.types.TypeName;

/**
 * Java {@link java.util.ServiceLoader} provider implementation that generates copyright as used by the Helidon project.
 */
@Weight(100)
public class HelidonCopyrightProvider implements CopyrightProvider {
    private static final String COPYRIGHT_TEMPLATE = """
            /*
             * Copyright (c) {{year}} Oracle and/or its affiliates.
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
            """;

    @Override
    public String copyright(TypeName generator, TypeName trigger, TypeName generatedType) {
        return COPYRIGHT_TEMPLATE.replace("{{year}}", year());
    }

    private String year() {
        return String.valueOf(LocalDate.now().getYear());
    }
}
