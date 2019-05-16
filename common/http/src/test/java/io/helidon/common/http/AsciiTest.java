package io.helidon.common.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for class {@link Ascii}.
 *
 * @see Ascii
 */
public class AsciiTest {

    @Test
    public void testIsLowerCaseOne() {
        assertFalse(Ascii.isLowerCase('{'));
    }


    @Test
    public void testIsLowerCaseReturningTrue() {
        assertTrue(Ascii.isLowerCase('o'));
    }


    @Test
    public void testIsLowerCaseTwo() {
        assertFalse(Ascii.isLowerCase('\"'));
    }


    @Test
    public void testToLowerCaseTakingCharSequenceOne() {
        StringBuilder stringBuilder = new StringBuilder("uhho^s} b'jdwtym");

        assertEquals("uhho^s} b'jdwtym", Ascii.toLowerCase(stringBuilder));
    }


    @Test
    public void testToLowerCaseTakingCharSequenceTwo() {
        assertEquals("uhho^s} b'jdwtym", Ascii.toLowerCase((CharSequence) "uHHO^S} b'jDwTYM"));
    }


    @Test
    public void testToLowerCaseTakingString() {
        assertEquals("", Ascii.toLowerCase(""));
    }

}
