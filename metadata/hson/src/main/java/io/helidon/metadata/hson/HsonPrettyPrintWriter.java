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

package io.helidon.metadata.hson;

import java.io.PrintWriter;
import java.io.Writer;

class HsonPrettyPrintWriter extends PrintWriter {
    private static final String IDENT_UNIT = "   "; // 3 spaces

    private int indentLevel = 0;
    private boolean inString = false;
    private boolean escape = false;

    HsonPrettyPrintWriter(Writer out) {
        super(out);
    }

    @Override
    public void write(int c) {
        char ch = (char) c;

        // Track string state (to avoid inserting indentation inside strings)
        if (inString) {
            if (escape) {
                escape = false;
            } else if (ch == '\\') {
                escape = true;
            } else if (ch == '"') {
                inString = false;
            }
            super.write(c);
            return;
        } else if (ch == '"') {
            inString = true;
            super.write(c);
            return;
        }

        switch (ch) {
        case '{':
        case '[':
            super.write(ch);
            indentLevel++;
            super.write('\n');
            write(IDENT_UNIT.repeat(indentLevel));
            break;
        case '}':
        case ']':
            indentLevel--;
            super.write('\n');
            write(IDENT_UNIT.repeat(indentLevel));
            super.write(ch);
            break;
        case ',':
            super.write(ch);
            super.write('\n');
            write(IDENT_UNIT.repeat(indentLevel));
            break;
        case ':':
            super.write(": ");
            break;
        default:
            super.write(ch);
        }
    }

}
