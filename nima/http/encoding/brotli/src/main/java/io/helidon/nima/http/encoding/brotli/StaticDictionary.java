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

package io.helidon.nima.http.encoding.brotli;

class StaticDictionary {

    public static boolean brotliFindAllStaticDictionaryMatches(State state, int[] data, int dataIx, int minLength,
                                                               int maxLength, int[] dictMatches, int dictIx) {
        boolean has_found_match = false;
        EncoderDictionary dictionary = state.hasher.encDict;
        int offset = dictionary.buckets[hash(data, dataIx)];
        boolean end = offset == 0;
        while (!end) {
            DictWord w = dictionary.dictWords[offset++];
            int l = w.len & 0x1F;
            int n = 1 << DictionaryTable.SIZE_BITS_BY_LENGTH[l];
            int id = w.idx;
            end = convert(w.len & 0x80);
            w.len = l;
            if (w.transform == 0) {
                int matchlen = dictMatchLength(data, dataIx, id, l, maxLength);
                int s;
                int minlen;
                int maxlen;
                int len;
                /* Transform "" + BROTLI_TRANSFORM_IDENTITY + "" */
                if (matchlen == l) {
                    addMatch(id, l, l, dictMatches, dictIx);
                    has_found_match = true;
                }
                    /* Transforms "" + BROTLI_TRANSFORM_OMIT_LAST_1 + "" and
                      "" + BROTLI_TRANSFORM_OMIT_LAST_1 + "ing " */
                if (matchlen >= l - 1) {
                    addMatch(id + 12 * n, l - 1, l, dictMatches, dictIx);
                    if (l + 2 < maxLength &&
                            data[dataIx + l - 1] == 'i' && data[dataIx + l] == 'n' && data[dataIx + l + 1] == 'g' &&
                            data[dataIx + l + 2] == ' ') {
                        addMatch(id + 49 * n, l + 3, l, dictMatches, dictIx);
                    }
                    has_found_match = true;
                }
                /* Transform "" + BROTLI_TRANSFORM_OMIT_LAST_# + "" (# = 2 .. 9) */
                minlen = minLength;
                if (l > 9) {
                    minlen = Math.max(minlen, l - 9);
                }
                maxlen = Math.min(matchlen, l - 2);
                for (len = minlen; len <= maxlen; ++len) {
                    int cut = l - len;
                    int transform_id = (cut << 2) + (int) ((dictionary.cutoffTransforms >> (cut * 6)) & 0x3F);
                    addMatch(id + transform_id * n, len, l, dictMatches, dictIx);
                    has_found_match = true;
                }
                if (matchlen < l || l + 6 >= maxLength) {
                    continue;
                }
                /* Transforms "" + BROTLI_TRANSFORM_IDENTITY + <suffix> */
                if (data[dataIx + l] == ' ') {
                    addMatch(id + n, l + 1, l, dictMatches, dictIx);
                    if (data[dataIx + l + 1] == 'a') {
                        if (data[dataIx + l + 2] == ' ') {
                            addMatch(id + 28 * n, l + 3, l, dictMatches, dictIx);
                        } else if (data[dataIx + l + 2] == 's') {
                            if (data[dataIx + l + 3] == ' ') {
                                addMatch(id + 46 * n, l + 4, l, dictMatches, dictIx);
                            }
                        } else if (data[dataIx + l + 2] == 't') {
                            if (data[dataIx + l + 3] == ' ') {
                                addMatch(id + 60 * n, l + 4, l, dictMatches, dictIx);
                            }
                        } else if (data[dataIx + l + 2] == 'n') {
                            if (data[dataIx + l + 3] == 'd' && data[dataIx + l + 4] == ' ') {
                                addMatch(id + 10 * n, l + 5, l, dictMatches, dictIx);
                            }
                        }
                    } else if (data[dataIx + l + 1] == 'b') {
                        if (data[dataIx + l + 2] == 'y' && data[dataIx + l + 3] == ' ') {
                            addMatch(id + 38 * n, l + 4, l, dictMatches, dictIx);
                        }
                    } else if (data[dataIx + l + 1] == 'i') {
                        if (data[dataIx + l + 2] == 'n') {
                            if (data[dataIx + l + 3] == ' ') {
                                addMatch(id + 16 * n, l + 4, l, dictMatches, dictIx);
                            }
                        } else if (data[dataIx + l + 2] == 's') {
                            if (data[dataIx + l + 3] == ' ') {
                                addMatch(id + 47 * n, l + 4, l, dictMatches, dictIx);
                            }
                        }
                    } else if (data[dataIx + l + 1] == 'f') {
                        if (data[dataIx + l + 2] == 'o') {
                            if (data[dataIx + l + 3] == 'r' && data[dataIx + l + 4] == ' ') {
                                addMatch(id + 25 * n, l + 5, l, dictMatches, dictIx);
                            }
                        } else if (data[dataIx + l + 2] == 'r') {
                            if (data[dataIx + l + 3] == 'o' && data[dataIx + l + 4] == 'm' && data[dataIx + l + 5] == ' ') {
                                addMatch(id + 37 * n, l + 6, l, dictMatches, dictIx);
                            }
                        }
                    } else if (data[dataIx + l + 1] == 'o') {
                        if (data[dataIx + l + 2] == 'f') {
                            if (data[dataIx + l + 3] == ' ') {
                                addMatch(id + 8 * n, l + 4, l, dictMatches, dictIx);
                            }
                        } else if (data[dataIx + l + 2] == 'n') {
                            if (data[dataIx + l + 3] == ' ') {
                                addMatch(id + 45 * n, l + 4, l, dictMatches, dictIx);
                            }
                        }
                    } else if (data[dataIx + l + 1] == 'n') {
                        if (data[dataIx + l + 2] == 'o' && data[dataIx + l + 3] == 't' && data[dataIx + l + 4] == ' ') {
                            addMatch(id + 80 * n, l + 5, l, dictMatches, dictIx);
                        }
                    } else if (data[dataIx + l + 1] == 't') {
                        if (data[dataIx + l + 2] == 'h') {
                            if (data[dataIx + l + 3] == 'e') {
                                if (data[dataIx + l + 4] == ' ') {
                                    addMatch(id + 5 * n, l + 5, l, dictMatches, dictIx);
                                }
                            } else if (data[dataIx + l + 3] == 'a') {
                                if (data[dataIx + l + 4] == 't' && data[dataIx + l + 5] == ' ') {
                                    addMatch(id + 29 * n, l + 6, l, dictMatches, dictIx);
                                }
                            }
                        } else if (data[dataIx + l + 2] == 'o') {
                            if (data[dataIx + l + 3] == ' ') {
                                addMatch(id + 17 * n, l + 4, l, dictMatches, dictIx);
                            }
                        }
                    } else if (data[dataIx + l + 1] == 'w') {
                        if (data[dataIx + l + 2] == 'i' && data[dataIx + l + 3] == 't' && data[dataIx + l + 4] == 'h' && data[dataIx + l + 5] == ' ') {
                            addMatch(id + 35 * n, l + 6, l, dictMatches, dictIx);
                        }
                    }
                } else if (data[dataIx + l] == '"') {
                    addMatch(id + 19 * n, l + 1, l, dictMatches, dictIx);
                    if (data[dataIx + l + 1] == '>') {
                        addMatch(id + 21 * n, l + 2, l, dictMatches, dictIx);
                    }
                } else if (data[dataIx + l] == '.') {
                    addMatch(id + 20 * n, l + 1, l, dictMatches, dictIx);
                    if (data[dataIx + l + 1] == ' ') {
                        addMatch(id + 31 * n, l + 2, l, dictMatches, dictIx);
                        if (data[dataIx + l + 2] == 'T' && data[dataIx + l + 3] == 'h') {
                            if (data[dataIx + l + 4] == 'e') {
                                if (data[dataIx + l + 5] == ' ') {
                                    addMatch(id + 43 * n, l + 6, l, dictMatches, dictIx);
                                }
                            } else if (data[dataIx + l + 4] == 'i') {
                                if (data[dataIx + l + 5] == 's' && data[dataIx + l + 6] == ' ') {
                                    addMatch(id + 75 * n, l + 7, l, dictMatches, dictIx);
                                }
                            }
                        }
                    }
                } else if (data[dataIx + l] == ',') {
                    addMatch(id + 76 * n, l + 1, l, dictMatches, dictIx);
                    if (data[dataIx + l + 1] == ' ') {
                        addMatch(id + 14 * n, l + 2, l, dictMatches, dictIx);
                    }
                } else if (data[dataIx + l] == '\n') {
                    addMatch(id + 22 * n, l + 1, l, dictMatches, dictIx);
                    if (data[dataIx + l + 1] == '\t') {
                        addMatch(id + 50 * n, l + 2, l, dictMatches, dictIx);
                    }
                } else if (data[dataIx + l] == ']') {
                    addMatch(id + 24 * n, l + 1, l, dictMatches, dictIx);
                } else if (data[dataIx + l] == '\'') {
                    addMatch(id + 36 * n, l + 1, l, dictMatches, dictIx);
                } else if (data[dataIx + l] == ':') {
                    addMatch(id + 51 * n, l + 1, l, dictMatches, dictIx);
                } else if (data[dataIx + l] == '(') {
                    addMatch(id + 57 * n, l + 1, l, dictMatches, dictIx);
                } else if (data[dataIx + l] == '=') {
                    if (data[dataIx + l + 1] == '"') {
                        addMatch(id + 70 * n, l + 2, l, dictMatches, dictIx);
                    } else if (data[dataIx + l + 1] == '\'') {
                        addMatch(id + 86 * n, l + 2, l, dictMatches, dictIx);
                    }
                } else if (data[dataIx + l] == 'a') {
                    if (data[dataIx + l + 1] == 'l' && data[dataIx + l + 2] == ' ') {
                        addMatch(id + 84 * n, l + 3, l, dictMatches, dictIx);
                    }
                } else if (data[dataIx + l] == 'e') {
                    if (data[dataIx + l + 1] == 'd') {
                        if (data[dataIx + l + 2] == ' ') {
                            addMatch(id + 53 * n, l + 3, l, dictMatches, dictIx);
                        }
                    } else if (data[dataIx + l + 1] == 'r') {
                        if (data[dataIx + l + 2] == ' ') {
                            addMatch(id + 82 * n, l + 3, l, dictMatches, dictIx);
                        }
                    } else if (data[dataIx + l + 1] == 's') {
                        if (data[dataIx + l + 2] == 't' && data[dataIx + l + 3] == ' ') {
                            addMatch(id + 95 * n, l + 4, l, dictMatches, dictIx);
                        }
                    }
                } else if (data[dataIx + l] == 'f') {
                    if (data[dataIx + l + 1] == 'u' && data[dataIx + l + 2] == 'l' && data[dataIx + l + 3] == ' ') {
                        addMatch(id + 90 * n, l + 4, l, dictMatches, dictIx);
                    }
                } else if (data[dataIx + l] == 'i') {
                    if (data[dataIx + l + 1] == 'v') {
                        if (data[dataIx + l + 2] == 'e' && data[dataIx + l + 3] == ' ') {
                            addMatch(id + 92 * n, l + 4, l, dictMatches, dictIx);
                        }
                    } else if (data[dataIx + l + 1] == 'z') {
                        if (data[dataIx + l + 2] == 'e' && data[dataIx + l + 3] == ' ') {
                            addMatch(id + 100 * n, l + 4, l, dictMatches, dictIx);
                        }
                    }
                } else if (data[dataIx + l] == 'l') {
                    if (data[dataIx + l + 1] == 'e') {
                        if (data[dataIx + l + 2] == 's' && data[dataIx + l + 3] == 's' && data[dataIx + l + 4] == ' ') {
                            addMatch(id + 93 * n, l + 5, l, dictMatches, dictIx);
                        }
                    } else if (data[dataIx + l + 1] == 'y') {
                        if (data[dataIx + l + 2] == ' ') {
                            addMatch(id + 61 * n, l + 3, l, dictMatches, dictIx);
                        }
                    }
                } else if (data[dataIx + l] == 'o') {
                    if (data[dataIx + l + 1] == 'u' && data[dataIx + l + 2] == 's' && data[dataIx + l + 3] == ' ') {
                        addMatch(id + 106 * n, l + 4, l, dictMatches, dictIx);
                    }
                }
            } else {
                    /* Set is_all_caps=0 for BROTLI_TRANSFORM_UPPERCASE_FIRST and
                           is_all_caps=1 otherwise (BROTLI_TRANSFORM_UPPERCASE_ALL)
                       transform. */
                boolean is_all_caps = (w.transform != Constant.BROTLI_TRANSFORM_UPPERCASE_FIRST);
                if (!isMatch(w, data, 0, maxLength)) {
                    continue;
                }
                /* Transform "" + kUppercase{First,All} + "" */
                addMatch(id + (is_all_caps ? 44 : 9) * n, l, l, dictMatches, dictIx);
                has_found_match = true;
                if (l + 1 >= maxLength) {
                    continue;
                }
                /* Transforms "" + kUppercase{First,All} + <suffix> */
                if (data[dataIx + l] == ' ') {
                    addMatch(id + (is_all_caps ? 68 : 4) * n, l + 1, l, dictMatches, dictIx);
                } else if (data[dataIx + l] == '"') {
                    addMatch(id + (is_all_caps ? 87 : 66) * n, l + 1, l, dictMatches, dictIx);
                    if (data[dataIx + l + 1] == '>') {
                        addMatch(id + (is_all_caps ? 97 : 69) * n, l + 2, l, dictMatches, dictIx);
                    }
                } else if (data[dataIx + l] == '.') {
                    addMatch(id + (is_all_caps ? 101 : 79) * n, l + 1, l, dictMatches, dictIx);
                    if (data[dataIx + l + 1] == ' ') {
                        addMatch(id + (is_all_caps ? 114 : 88) * n, l + 2, l, dictMatches, dictIx);
                    }
                } else if (data[dataIx + l] == ',') {
                    addMatch(id + (is_all_caps ? 112 : 99) * n, l + 1, l, dictMatches, dictIx);
                    if (data[dataIx + l + 1] == ' ') {
                        addMatch(id + (is_all_caps ? 107 : 58) * n, l + 2, l, dictMatches, dictIx);
                    }
                } else if (data[dataIx + l] == '\'') {
                    addMatch(id + (is_all_caps ? 94 : 74) * n, l + 1, l, dictMatches, dictIx);
                } else if (data[dataIx + l] == '(') {
                    addMatch(id + (is_all_caps ? 113 : 78) * n, l + 1, l, dictMatches, dictIx);
                } else if (data[dataIx + l] == '=') {
                    if (data[dataIx + l + 1] == '"') {
                        addMatch(id + (is_all_caps ? 105 : 104) * n, l + 2, l, dictMatches, dictIx);
                    } else if (data[dataIx + l + 1] == '\'') {
                        addMatch(id + (is_all_caps ? 116 : 108) * n, l + 2, l, dictMatches, dictIx);
                    }
                }
            }
        }
        /* Transforms with prefixes " " and "." */
        if (maxLength >= 5 && (data[dataIx] == ' ' || data[dataIx] == '.')) {
            boolean is_space = (data[dataIx] == ' ');
            offset = dictionary.buckets[hash(data, 1)];
            end = offset == 0;
            while (!end) {
                DictWord w = dictionary.dictWords[offset++];
                int l = w.len & 0x1F;
                int n = 1 << DictionaryTable.SIZE_BITS_BY_LENGTH[l];
                int id = w.idx;
                end = convert(w.len & 0x80);
                w.len = l;
                if (w.transform == 0) {
                    if (!isMatch(w, data, 1, maxLength - 1)) {
                        continue;
                    }
                    /* Transforms " " + BROTLI_TRANSFORM_IDENTITY + "" and
                      "." + BROTLI_TRANSFORM_IDENTITY + "" */
                    addMatch(id + (is_space ? 6 : 32) * n, l + 1, l, dictMatches, dictIx);
                    has_found_match = true;
                    if (l + 2 >= maxLength) {
                        continue;
                    }
                    /* Transforms " " + BROTLI_TRANSFORM_IDENTITY + <suffix> and
                                  "." + BROTLI_TRANSFORM_IDENTITY + <suffix>
                    */
                    if (data[dataIx + l + 1] == ' ') {
                        addMatch(id + (is_space ? 2 : 77) * n, l + 2, l, dictMatches, dictIx);
                    } else if (data[dataIx + l + 1] == '(') {
                        addMatch(id + (is_space ? 89 : 67) * n, l + 2, l, dictMatches, dictIx);
                    } else if (is_space) {
                        if (data[dataIx + l + 1] == ',') {
                            addMatch(id + 103 * n, l + 2, l, dictMatches, dictIx);
                            if (data[dataIx + l + 2] == ' ') {
                                addMatch(id + 33 * n, l + 3, l, dictMatches, dictIx);
                            }
                        } else if (data[dataIx + l + 1] == '.') {
                            addMatch(id + 71 * n, l + 2, l, dictMatches, dictIx);
                            if (data[dataIx + l + 2] == ' ') {
                                addMatch(id + 52 * n, l + 3, l, dictMatches, dictIx);
                            }
                        } else if (data[dataIx + l + 1] == '=') {
                            if (data[dataIx + l + 2] == '"') {
                                addMatch(id + 81 * n, l + 3, l, dictMatches, dictIx);
                            } else if (data[dataIx + l + 2] == '\'') {
                                addMatch(id + 98 * n, l + 3, l, dictMatches, dictIx);
                            }
                        }
                    }
                } else if (is_space) {
                    /* Set is_all_caps=0 for BROTLI_TRANSFORM_UPPERCASE_FIRST and
                           is_all_caps=1 otherwise (BROTLI_TRANSFORM_UPPERCASE_ALL)
                       transform. */
                    boolean is_all_caps = (w.transform != Constant.BROTLI_TRANSFORM_UPPERCASE_FIRST);
                    if (!isMatch(w, data, 1, maxLength - 1)) {
                        continue;
                    }
                    /* Transforms " " + kUppercase{First,All} + "" */
                    addMatch(id + (is_all_caps ? 85 : 30) * n, l + 1, l, dictMatches, dictIx);
                    has_found_match = true;
                    if (l + 2 >= maxLength) {
                        continue;
                    }
                    /* Transforms " " + kUppercase{First,All} + <suffix> */
                    if (data[dataIx + l + 1] == ' ') {
                        addMatch(id + (is_all_caps ? 83 : 15) * n, l + 2, l, dictMatches, dictIx);
                    } else if (data[dataIx + l + 1] == ',') {
                        if (!is_all_caps) {
                            addMatch(id + 109 * n, l + 2, l, dictMatches, dictIx);
                        }
                        if (data[dataIx + l + 2] == ' ') {
                            addMatch(id + (is_all_caps ? 111 : 65) * n, l + 3, l, dictMatches, dictIx);
                        }
                    } else if (data[dataIx + l + 1] == '.') {
                        addMatch(id + (is_all_caps ? 115 : 96) * n, l + 2, l, dictMatches, dictIx);
                        if (data[dataIx + l + 2] == ' ') {
                            addMatch(id + (is_all_caps ? 117 : 91) * n, l + 3, l, dictMatches, dictIx);
                        }
                    } else if (data[dataIx + l + 1] == '=') {
                        if (data[dataIx + l + 2] == '"') {
                            addMatch(id + (is_all_caps ? 110 : 118) * n, l + 3, l, dictMatches, dictIx);
                        } else if (data[dataIx + l + 2] == '\'') {
                            addMatch(id + (is_all_caps ? 119 : 120) * n, l + 3, l, dictMatches, dictIx);
                        }
                    }
                }
            }
        }
        if (maxLength >= 6) {
            /* Transforms with prefixes "e ", "s ", ", " and "\xC2\xA0" */
            if ((
                    data[dataIx + 1] == ' ' &&
                            (data[dataIx] == 'e' || data[dataIx] == 's' || data[dataIx] == ',')) ||
                    (data[dataIx] == 0xC2 && data[dataIx + 1] == 0xA0)) {
                offset = dictionary.buckets[hash(data, 2)];
                end = offset == 0;
                while (!end) {
                    DictWord w = dictionary.dictWords[offset++];
                    int l = w.len & 0x1F;
                    int n = 1 << DictionaryTable.SIZE_BITS_BY_LENGTH[l];
                    int id = w.idx;
                    end = convert(w.len & 0x80);
                    w.len = l;
                    if (w.transform == 0 &&
                            isMatch(w, data, 2, maxLength - 2)) {
                        if (data[dataIx] == 0xC2) {
                            addMatch(id + 102 * n, l + 2, l, dictMatches, dictIx);
                            has_found_match = true;
                        } else if (l + 2 < maxLength && data[dataIx + l + 2] == ' ') {
                            int t = data[dataIx] == 'e' ? 18 : (data[dataIx] == 's' ? 7 : 13);
                            addMatch(id + t * n, l + 3, l, dictMatches, dictIx);
                            has_found_match = true;
                        }
                    }
                }
            }
        }
        if (maxLength >= 9) {
            /* Transforms with prefixes " the " and ".com/" */
            if ((
                    data[dataIx] == ' ' && data[dataIx + 1] == 't' && data[dataIx + 2] == 'h' &&
                            data[dataIx + 3] == 'e' && data[dataIx + 4] == ' ') ||
                    (
                            data[dataIx] == '.' && data[dataIx + 1] == 'c' && data[dataIx + 2] == 'o' &&
                                    data[dataIx + 3] == 'm' && data[dataIx + 4] == '/')) {
                offset = dictionary.buckets[hash(data, 5)];
                end = offset == 0;
                while (!end) {
                    DictWord w = dictionary.dictWords[offset++];
                    int l = w.len & 0x1F;
                    int n = 1 << DictionaryTable.SIZE_BITS_BY_LENGTH[l];
                    int id = w.idx;
                    end = convert(w.len & 0x80);
                    w.len = l;
                    if (w.transform == 0 &&
                            isMatch(w, data, 5, maxLength - 5)) {
                        addMatch(id + (data[dataIx] == ' ' ? 41 : 72) * n, l + 5, l, dictMatches, dictIx);
                        has_found_match = true;
                        if (l + 5 < maxLength) {

                            if (data[dataIx + l + 5] == ' ') {
                                if (l + 8 < maxLength &&
                                        data[dataIx + l + 5] == ' ' && data[dataIx + l + 6] == 'o' &&
                                        data[dataIx + l + 7] == 'f' && data[dataIx + l + 8] == ' ') {
                                    addMatch(id + 62 * n, l + 9, l, dictMatches, dictIx);
                                    if (l + 12 < maxLength &&
                                            data[dataIx + l + 9] == 't' &&
                                            data[dataIx + l + 10] == 'h' &&
                                            data[dataIx + l + 11] == 'e' &&
                                            data[dataIx + l + 12] == ' ') {
                                        addMatch(id + 73 * n, l + 13, l, dictMatches, dictIx);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return has_found_match;
    }

    public static boolean convert(int value) {
        return value != 0;
    }

    public static boolean isMatch(DictWord w, int[] data, int dataIx, int maxLength) {
        if (w.len > maxLength) {
            return false;
        } else {
            int offset = DictionaryTable.OFFSETS_BY_LENGTHS[w.len] + (w.len * w.idx);
            if (w.transform == 0) {
                /* Match against base dictionary word. */
                return Encoder.findMatchLengthWithLimit(DictionaryTable.DATA, offset, data, dataIx, w.len) == w.len;
            } else if (w.transform == 10) {
                /* Match against uppercase first transform.
                   Note that there are only ASCII uppercase words in the lookup table. */
                if (DictionaryTable.DATA.length < (dataIx + offset)) {
                    return false;
                }
                return (
                        DictionaryTable.DATA[dataIx + offset] >= 'a' && DictionaryTable.DATA[dataIx + offset] <= 'z' &&
                                (DictionaryTable.DATA[dataIx + offset] ^ 32) == data[dataIx + dataIx] &&
                                Encoder.findMatchLengthWithLimit(DictionaryTable.DATA, offset + 1, data, dataIx + 1, w.len - 1) ==
                                        w.len - 1);
            } else {
                  /* Match against uppercase all transform.
                     Note that there are only ASCII uppercase words in the lookup table. */
                int i;
                for (i = 0; i < w.len; ++i) {
                    if (DictionaryTable.DATA[dataIx + offset + i] >= 'a' && DictionaryTable.DATA[dataIx + offset + i] <= 'z') {
                        if ((DictionaryTable.DATA[dataIx + offset + i] ^ 32) != data[dataIx + dataIx + i]) {
                            return false;
                        }
                    } else {
                        if (DictionaryTable.DATA[dataIx + offset + i] != data[dataIx + dataIx + i]) {
                            return false;
                        }
                    }
                }
                return true;
            }
        }
    }

    public static int dictMatchLength(int[] data, int dataIx, int id, int len, int maxLength) {
        int offset = DictionaryTable.OFFSETS_BY_LENGTHS[len] + len * id;
        return Encoder.findMatchLengthWithLimit(DictionaryTable.DATA, offset, data, dataIx, Math.min(len, maxLength));
    }

    public static void addMatch(int distance, int len, int lenCode, int[] matches, int dictIx) {
        int match = (distance << 5) + lenCode;
        matches[dictIx + len] = Math.min(matches[dictIx + len], match);
    }

    private static int hash(int[] data, int index) {
        int h = Utils.get32Bits(data, index) * Constant.kDictHashMul32;
        return (h >> (32 - Constant.kDictNumBits)) & 0xFF;
    }
}
