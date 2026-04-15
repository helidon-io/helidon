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

import java.util.List;
import java.util.Map;

/**
 * Config metadata page.
 */
record CmPage(Kind kind,
              String key,
              String typeName,
              String fileName,
              String description,
              Tables tables,
              Map<String, String> mergedTypes,
              Map<String, String> dependentTypes,
              List<Usage> usages) {

    enum Kind {
        ROOT,
        CONFIG,
        CONTRACT,
        ENUM
    }

    record Row(String key,
               String type,
               String defaultValue,
               String description,
               String fileName,
               String anchor) {
    }

    record Usage(String path, String fileName, String anchor) {
    }

    record Table(List<Row> rows, boolean hasTypes, boolean hasDefaults) {
        boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    record Tables(Table standard, Table experimental, Table deprecated) {
        boolean isEmpty() {
            return standard.isEmpty() && experimental.isEmpty() && deprecated.isEmpty();
        }
    }
}
