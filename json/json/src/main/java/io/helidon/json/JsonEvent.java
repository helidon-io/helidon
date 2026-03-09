package io.helidon.json;

enum JsonEvent {

    OBJECT_START('{'),
    OBJECT_END('}'),
    ARRAY_START('['),
    ARRAY_END(']'),
    NULL('n'),
    STRING('"'),
    KEY('"'),
    NUMBER('1'),
    TRUE('t'),
    FALSE('f');

    private final byte token;

    JsonEvent(char token) {
        this.token = (byte) token;
    }

    byte token() {
        return token;
    }
}
