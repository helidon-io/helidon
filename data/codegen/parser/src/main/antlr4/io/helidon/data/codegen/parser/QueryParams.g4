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

lexer grammar QueryParams;

@header {/*
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
}

@members {
public void reset(CharStream input) {
    reset();
    setInputStream(input);
}
}

/*
    Query named parameter:
     - ordinal parameter: decimal integer prefixed with the '?' character, for example, "?1"
     - named parameter: legal Java identifier prefixed with the ':' character, for example, ":name"
     Positional and named parameters may not be mixed in a single query

     java_identifier :: [a-zA-Z_$][a-zA-Z0-9_$]*
*/

// Decimal digit
fragment DIGIT: [0-9];
// Decimal number
fragment NUMBER: [0-9]+;
// 1st character of Java identifier (simplified)
fragment ID_START: [a-zA-Z_$];
// next character of Java identifier (simplified)
fragment ID_NEXT: ID_START | DIGIT;
// Java whitespace charaqcters
fragment WHITESPACE: ( ' ' | '\t'| '\r'| '\n' | '\u000C' );
// Java non-whitespace characters
fragment NOT_WHSP: ~( ' ' | '\t'| '\r'| '\n' | '\u000C' );
// Not 1st parameter character and non-whitespace characters
fragment NOT_ID_BEG: ~( ':' | '$' | ' ' | '\t'| '\r'| '\n' | '\u000C' );

// Ordinal parameter
OrdinalParameter: '$' NUMBER;
// Named parameter
NamedParameter: ':' ID_START ID_NEXT*;
// Anything not parameter
NotParameter: NOT_ID_BEG NOT_WHSP+ -> skip;
// Whitespaces
WhiteSpace: WHITESPACE+ -> skip;
