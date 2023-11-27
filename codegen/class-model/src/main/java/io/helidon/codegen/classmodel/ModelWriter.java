/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.codegen.classmodel;

import java.io.IOException;
import java.io.Writer;

import static io.helidon.codegen.classmodel.ClassModel.PADDING_TOKEN;

class ModelWriter extends Writer {

    private final Writer delegate;
    private final String padding;
    private String currentPadding = ""; //no padding
    private int paddingLevel = 0;
    private boolean firstWrite = true;

    ModelWriter(Writer delegate, String padding) {
        this.delegate = delegate;
        this.padding = padding;
    }

    void increasePaddingLevel() {
        paddingLevel++;
        currentPadding = padding.repeat(paddingLevel);
    }

    void decreasePaddingLevel() {
        paddingLevel--;
        currentPadding = padding.repeat(paddingLevel);
    }

    void writeLine(String str) throws IOException {
        write(str);
        write("\n");
    }

    /**
     * Separator line is line which is completely empty and with no padding.
     *
     * @throws IOException If an I/O error occurs
     */
    void writeSeparatorLine() throws IOException {
        delegate.write("\n");
    }

    @Override
    public void write(String str) throws IOException {
        if (firstWrite) {
            delegate.write(currentPadding);
            firstWrite = false;
        }
        String padded = str.replaceAll("\n", "\n" + currentPadding);
        padded = padded.replaceAll(PADDING_TOKEN, padding);
        delegate.write(padded);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        delegate.write(cbuf, off, len);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
