/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

/**
 * Type required for template processing for config reference.
 */
public class CmReference {
    private final String file;
    private final String title;

    CmReference(String file, String title) {
        this.file = file;
        this.title = title;
    }

    /**
     * File name that was generated.
     *
     * @return file name
     */
    public String file() {
        return file;
    }

    /**
     * Title of the file.
     *
     * @return title
     */
    public String title() {
        return title;
    }
}
