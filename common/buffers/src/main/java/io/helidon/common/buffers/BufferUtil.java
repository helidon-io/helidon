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

package io.helidon.common.buffers;

final class BufferUtil {
    static final BufferData EMPTY_BUFFER = new FixedBufferData(0);

    private static final String ZEROES = "00000000";
    private static final String BINARY_LINE =
            "+--------+----------+\n";
    private static final String BINARY_HEADER =
            BINARY_LINE
                    + "|  index | 01234567 |\n"
                    + BINARY_LINE;
    private static final String HEX_LINE =
            "+--------+-------------------------------------------------+----------------+\n";
    private static final String HEX_HEADER =
            HEX_LINE
                    + "|   index|  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |            data|\n"
                    + HEX_LINE;

    private BufferUtil() {
    }

    static String toBinaryString(int value) {
        String binary = Integer.toBinaryString(value);
        return ZEROES.substring(binary.length()) + binary;
    }

    static String debugDataBinary(byte[] bytes, int position, int length) {
        StringBuilder stringBuilder = new StringBuilder(BINARY_LINE.length() * length // each byte
                                                                + BINARY_LINE.length() // last line
                                                                + BINARY_HEADER.length()); //first line

        stringBuilder.append(BINARY_HEADER);

        int counter = 0;
        for (int i = position; i < length; i++) {
            byte toPrint = bytes[i];

            stringBuilder.append("|")
                    .append(toIndexString(counter))
                    .append("| ")
                    .append(toBinaryString(toPrint & 0xFF))
                    .append(" |\n");
            counter++;
        }
        stringBuilder.append(BINARY_LINE);
        return stringBuilder.toString();
    }

    static String debugDataHex(byte[] bytes, int position, int length) {
        /*
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 47 45 54 20 2f 6c 6f 6f 6d 2f 71 75 69 63 6b 20 |GET /loom/quick |
|00000010| 48 54 54 50 2f 31 2e 31 0d 0a 68 6f 73 74 3a 20 |HTTP/1.1..host: |

+--------+-------------------------------------------------+----------------+
|   index|  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |            data|
+--------+-------------------------------------------------+----------------+
|       0| 55    |U |
+--------+-------------------------------------------------+----------------+
         */
        StringBuilder stringBuilder = new StringBuilder(HEX_LINE.length() * (length / 16) // each 16 bytes
                                                                + 1 // to cover the first line
                                                                + HEX_LINE.length() // last line
                                                                + HEX_LINE.length()); //first line

        stringBuilder.append(HEX_HEADER);
        StringBuilder line = new StringBuilder(16);

        int counter = 0;
        for (int i = position; i < length; i++) {
            // | position |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |text....|

            if (counter % 16 == 0) {
                line.setLength(0);
                stringBuilder.append("|")
                        .append(toIndexString(counter))
                        .append("| ");
            }

            byte toPrint = bytes[i];
            int toPrintInt = toPrint & 0xFF;
            String hex = Integer.toHexString(toPrintInt);
            hex = ZEROES.substring(hex.length() + 6) + hex;
            stringBuilder.append(hex)
                    .append(" ");
            appendChar(line, toPrintInt);

            counter++;
            if (counter % 16 == 0) {
                stringBuilder.append("|");
                stringBuilder.append(line);
                stringBuilder.append("|\n");
            }
        }
        // we need to finish the line (find out how many missing
        int missing = 16 - (counter % 16);
        if (counter != 0 && missing != 16) {
            stringBuilder.append("   ".repeat(missing));
            line.append(" ".repeat(missing));
            stringBuilder.append("|");
            stringBuilder.append(line);
            stringBuilder.append("|\n");
        }

        stringBuilder.append(HEX_LINE);
        return stringBuilder.toString();
    }

    private static String toIndexString(int index) {
        String positionString = Integer.toHexString(index);
        int len = positionString.length();
        if (len >= 8) {
            return positionString;
        }
        return ZEROES.substring(len) + positionString;
    }

    private static void appendChar(StringBuilder line, int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);

        if (!Character.isISOControl(codePoint)
                && block != null
                && block != Character.UnicodeBlock.SPECIALS) {
            line.append((char) codePoint);
        } else {
            line.append('.');
        }
    }
}
