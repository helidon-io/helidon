/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.features.processor;

import java.io.PrintWriter;

class ModuleDescriptor {
    private boolean aotSupported = true;
    private String aotDescription;

    private boolean preview = false;
    private boolean incubating = false;
    private boolean deprecated = false;
    private String deprecatedSince;

    private String[] in;
    private String[] invalidIn;

    private String name;
    private String since;
    private String description;
    private String[] path;
    private String moduleName;


    ModuleDescriptor deprecated(boolean deprecated) {
        this.deprecated = deprecated;
        return this;
    }

    ModuleDescriptor deprecatedSince(String deprecatedSince) {
        this.deprecatedSince = deprecatedSince;
        return this;
    }

    ModuleDescriptor moduleName(String moduleName) {
        this.moduleName = moduleName;
        return this;
    }

    ModuleDescriptor aotSupported(boolean aotSupported) {
        this.aotSupported = aotSupported;
        return this;
    }

    ModuleDescriptor aotDescription(String aotDescription) {
        this.aotDescription = aotDescription;
        return this;
    }

    ModuleDescriptor preview(boolean preview) {
        this.preview = preview;
        return this;
    }

    ModuleDescriptor incubating(boolean incubating) {
        this.incubating = incubating;
        return this;
    }

    ModuleDescriptor invalidIn(String[] invalidIn) {
        this.invalidIn = invalidIn;
        return this;
    }

    ModuleDescriptor in(String[] in) {
        this.in = in;
        return this;
    }

    ModuleDescriptor since(String since) {
        this.since = since;
        return this;
    }

    ModuleDescriptor name(String name) {
        this.name = name;
        return this;
    }

    String name() {
        return name;
    }

    ModuleDescriptor description(String description) {
        this.description = description;
        return this;
    }

    ModuleDescriptor path(String[] path) {
        this.path = path;
        return this;
    }

    boolean noDeprecatedSince() {
        return deprecatedSince == null;
    }

    void write(PrintWriter metaWriter) {
        write(metaWriter, "m", moduleName);
        write(metaWriter, "n", name);
        write(metaWriter, "d", description);
        write(metaWriter, "s", since);
        write(metaWriter, "aotd", aotDescription);
        write(metaWriter, "in", in);
        write(metaWriter, "not", invalidIn);

        if (path != null && path.length != 0) {
            if (path.length != 1 || !name.equals(path[0])) {
                write(metaWriter, "p", path);
            }
        }

        if (!aotSupported) {
            write(metaWriter, "aot", false);
        }

        if (incubating) {
            write(metaWriter, "i", true);
        }
        if (preview) {
            write(metaWriter, "pr", true);
        }
        if (deprecated) {
            write(metaWriter, "dep", true);
            write(metaWriter, "deps", deprecatedSince);
        }
    }

    private void write(PrintWriter pw, String key, boolean value) {
        write(pw, key, String.valueOf(value));
    }

    private void write(PrintWriter pw, String key, String[] value) {
        if (value == null || value.length == 0) {
            return;
        }
        write(pw, key, String.join(",", value));
    }

    private void write(PrintWriter pw, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        pw.print(key);
        pw.print('=');
        pw.println(value);
    }
}
