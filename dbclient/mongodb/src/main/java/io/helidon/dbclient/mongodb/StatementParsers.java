/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient.mongodb;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.json.Json;

/**
 * Statement parameter parsers.
 */
final class StatementParsers {
    private StatementParsers() {
    }

    static StatementParser indexedParser(String statement, List<Object> indexedParams) {
        return new IndexedParser(statement, indexedParams);
    }

    static StatementParser namedParser(String statement, Map<String, Object> indexedParams) {
        return new NamedParser(statement, indexedParams);
    }

    @FunctionalInterface
    interface StatementParser {
        String convert();
    }

    // Slightly modified copy-paste from JDBC statement parser
    // Replaces "$name" named parameters with value from mappings if exists
    abstract static class Parser {
        /**
         * Parser ACTION to be performed.
         */
        @FunctionalInterface
        interface Action {
            void process(Parser parser);
        }

        /**
         * States used in state machine.
         */
        enum State {
            STMT, // Common statement
            STR,  // SQL string
            COL,  // After colon, expecting parameter name
            PAR  // Processing parameter name
        }

        /**
         * Do nothing.
         *
         * @param parser parser instance
         */
        static void noop(Parser parser) {
        }

        /**
         * Copy character from input string to output as is.
         *
         * @param parser parser instance
         */
        static void copy(Parser parser) {
            parser.sb.append(parser.c);
        }

        // Should be done much better!
        static String toJson(Object value) {
            if (value == null) {
                return "null";
            }
            if (value instanceof Number) {
                if (value instanceof Integer) {
                    return Json.createValue((Integer) value).toString();
                }
                if (value instanceof Long) {
                    return Json.createValue((Long) value).toString();
                }
                if (value instanceof Double) {
                    return Json.createValue((Double) value).toString();
                }
                if (value instanceof BigInteger) {
                    return Json.createValue((BigInteger) value).toString();
                }
                if (value instanceof BigDecimal) {
                    return Json.createValue((BigDecimal) value).toString();
                }
            }
            if (value instanceof String) {
                return Json.createValue((String) value).toString();
            }
            if (value instanceof Boolean) {
                return ((Boolean) value) ? "true" : "false";
            }
            return '"' + value.toString() + '"';
        }

        /**
         * SQL statement to be parsed.
         */
        private final String statement;
        /**
         * Target SQL statement builder.
         */
        private final StringBuilder sb;
        /**
         * Temporary string storage.
         */
        private final StringBuilder nap;

        /**
         * Character being currently processed.
         */
        private char c;

        private Parser(String statement) {
            this.sb = new StringBuilder(statement.length());
            this.nap = new StringBuilder(32);
            this.statement = statement;
            this.c = '\0';
        }

        String statement() {
            return statement;
        }

        StringBuilder sb() {
            return sb;
        }

        StringBuilder nap() {
            return nap;
        }

        char c() {
            return c;
        }

        void c(char newC) {
            c = newC;
        }
    }

    /**
     * Mapping parser state machine.
     */
    static final class NamedParser extends Parser implements StatementParser {

        /**
         * First character of named parameter identifier.
         */
        private static final char PAR_BEG = '$';

        /**
         * Character classes used in state machine.
         */
        private enum CharClass {
            LT,  // Letter
            NUM, // Number
            DQ,  // Double quote, begins/ends string in JSON
            DOL, // Dollar sign, begins named parameter
            OTH; // Other character// Other character

            // This is far from optimal code, but direct translation table would be too large for Java char
            private static CharClass charClass(char c) {
                // DO NOT replace with a single line combined ternary expression!
                if (Character.isLetter(c)) {
                    return LT;
                }
                if (Character.isDigit(c)) {
                    return NUM;
                }
                if (c == '"') {
                    return DQ;
                }
                if (c == PAR_BEG) {
                    return DOL;
                }

                return OTH;
            }
        }

        /**
         * States TRANSITION table.
         */
        private static final State[][] TRANSITION = {
                // LT          NUM         DQ          DOL        OTH
                {State.STMT, State.STMT, State.STR, State.COL, State.STMT}, // Transition from STMT
                {State.STR, State.STR, State.STMT, State.STR, State.STR}, // Transition from STR
                {State.PAR, State.PAR, State.STR, State.COL, State.STMT}, // Transition from COL
                {State.PAR, State.PAR, State.STMT, State.COL, State.STMT}  // Transition from PAR
        };

        static final Action COPY_ACTION = Parser::copy;
        static final Action FNAP_ACTION = NamedParser::fnap;
        static final Action NNAP_ACTION = NamedParser::nnap;
        static final Action NOOP_ACTION = Parser::noop;
        static final Action DOCH_ACTION = NamedParser::doch;
        static final Action CPDO_ACTION = NamedParser::cpdo;
        static final Action ENAP_ACTION = NamedParser::enap;

        /**
         * States TRANSITION ACTION table.
         */
        private static final Action[][] ACTION = {
                // LT         NUM          DQ           DOL          OTH
                {COPY_ACTION, COPY_ACTION, COPY_ACTION, NOOP_ACTION, COPY_ACTION}, // STMT
                {COPY_ACTION, COPY_ACTION, COPY_ACTION, COPY_ACTION, COPY_ACTION}, // STR
                {FNAP_ACTION, FNAP_ACTION, DOCH_ACTION, CPDO_ACTION, DOCH_ACTION}, // COL
                {NNAP_ACTION, NNAP_ACTION, ENAP_ACTION, ENAP_ACTION, ENAP_ACTION}  // PAR
        };

        /**
         * Copy previous colon character to output.
         *
         * @param parser parser instance
         */
        private static void cpdo(Parser parser) {
            parser.sb.append(PAR_BEG);
        }

        /**
         * Copy previous colon and current input string character to output.
         *
         * @param parser parser instance
         */
        private static void doch(Parser parser) {
            parser.sb.append(PAR_BEG);
            parser.sb.append(parser.c);
        }

        /**
         * Store 1st named parameter letter.
         *
         * @param parser parser instance
         */
        private static void fnap(Parser parser) {
            parser.nap.setLength(0);
            parser.nap.append(parser.c);
        }

        /**
         * Store next named parameter letter.
         *
         * @param parser parser instance
         */
        private static void nnap(Parser parser) {
            parser.nap.append(parser.c);
        }

        /**
         * Finish stored named parameter and copy current character from input string to output as is.
         *
         * @param parser parser instance
         */
        private static void enap(Parser parser) {
            String parName = parser.nap.toString();
            if (((NamedParser) parser).mappings.containsKey(parName)) {
                parser.sb.append(toJson(((NamedParser) parser).mappings.get(parName)));
            } else {
                parser.sb.append(PAR_BEG);
                parser.sb.append(parName);
            }
            parser.sb.append(parser.c);
        }

        private final Map<String, Object> mappings;
        /**
         * Character class of character being currently processed.
         */
        private CharClass cl;

        private NamedParser(String statement, Map<String, Object> mappings) {
            super(statement);
            this.mappings = mappings;
            this.cl = null;
        }

        @Override
        public String convert() {
            State state = State.STMT;  // Initial state: common statement processing
            int len = statement().length();
            for (int i = 0; i < len; i++) {
                c(statement().charAt(i));
                cl = CharClass.charClass(c());
                ACTION[state.ordinal()][cl.ordinal()].process(this);
                state = TRANSITION[state.ordinal()][cl.ordinal()];
            }
            // Process end of statement
            if (state == State.PAR) {
                String parName = nap().toString();
                if (mappings.containsKey(parName)) {
                    sb().append(toJson(mappings.get(parName)));
                } else {
                    sb().append(PAR_BEG);
                    sb().append(parName);
                }
            }
            return sb().toString();
        }

    }

    static final class IndexedParser extends Parser implements StatementParser {

        /**
         * First character of named parameter identifier.
         */
        private static final char PAR_LT = '?';

        /**
         * Character classes used in state machine.
         */
        private enum CharClass {
            LT,  // Letter
            NUM, // Number
            DQ,  // Double quote, begins/ends string in JSON
            QST, // Question mark, parameter
            OTH; // Other character

            // This is far from optimal code, but direct translation table would be too large for Java char
            private static CharClass charClass(char c) {
                // DO NOT replace with a single line combined ternary expression!
                if (Character.isLetter(c)) {
                    return LT;
                }

                if (Character.isDigit(c)) {
                    return NUM;
                }

                if (c == '"') {
                    return DQ;
                }

                if (c == PAR_LT) {
                    return QST;
                }

                return OTH;
            }

        }

        /**
         * States TRANSITION table.
         */
        private static final State[][] TRANSITION = {
                // LT                      NUM                    DQ                     QST                   OTH
                {State.STMT, State.STMT, State.STR, State.STMT, State.STMT}, // Transition from STMT
                {State.STR, State.STR, State.STMT, State.STR, State.STR} // Transition from STR
        };

        private static final Action COPY_ACTION = Parser::copy;
        private static final Action VAAP_ACTION = IndexedParser::vaap;
        /**
         * States TRANSITION ACTION table.
         */
        private static final Action[][] ACTION = {
                // LT         NUM          DQ           QST          OTH
                {COPY_ACTION, COPY_ACTION, COPY_ACTION, VAAP_ACTION, COPY_ACTION}, // STMT
                {COPY_ACTION, COPY_ACTION, COPY_ACTION, COPY_ACTION, COPY_ACTION} // STR
        };

        /**
         * Append next parameter from parameters list.
         *
         * @param parser parser instance
         */
        private static void vaap(Parser parser) {
            if (((IndexedParser) parser).parIt.hasNext()) {
                parser.sb.append(toJson(((IndexedParser) parser).parIt.next()));
            } else {
                parser.sb.append(parser.c);
            }
        }

        private final List<Object> parameters;
        private final ListIterator<Object> parIt;
        /**
         * Character class of character being currently processed.
         */
        private CharClass cl;

        private IndexedParser(String statement, List<Object> parameters) {
            super(statement);
            this.parameters = parameters;
            this.parIt = parameters.listIterator();
            this.cl = null;
        }

        @Override
        public String convert() {
            State state = State.STMT;  // Initial state: common statement processing
            int len = statement().length();
            for (int i = 0; i < len; i++) {
                c(statement().charAt(i));
                cl = CharClass.charClass(c());
                ACTION[state.ordinal()][cl.ordinal()].process(this);
                state = TRANSITION[state.ordinal()][cl.ordinal()];
            }
            return sb().toString();
        }

    }
}
