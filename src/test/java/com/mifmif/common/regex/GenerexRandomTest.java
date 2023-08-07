package com.mifmif.common.regex;

import kotlin.ranges.IntRange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;

/**
 * @author Myk Kolisnyk
 *
 */
public class GenerexRandomTest {

    @ParameterizedTest
    @MethodSource("arguments")
    public void testSimpleRandom(String pattern, int unused1, int unused2) {

        Generex generex = new Generex(pattern);
        assertThat(generex.random()).matches(pattern);
    }

    @ParameterizedTest
    @MethodSource("arguments")
    public void testRandomWithMinLength(String pattern, int minLength, int unused) {
        Generex generex = new Generex(pattern);
        String result = generex.random(minLength);

        assertThat(result).matches(pattern);
        assertThat(result.length()).isAtLeast(minLength);
    }

    @ParameterizedTest
    @MethodSource("arguments")
    public void testRandomWithMaxLength(String pattern, int minLength, int maxLength) {
        Generex generex = new Generex(pattern);
        String result = generex.random(minLength, maxLength);

        assertThat(result).matches(pattern);
        assertThat(result.length()).isIn(new IntRange(minLength, maxLength));
    }

    static Stream<Arguments> arguments() {
        return Stream.of(
                // Sample multicharacter expression
                Arguments.of("[A-Z]{5,9}", 4 , 8),
                // Sample expression
                Arguments.of("[0-3]([a-c]|[e-g]{1,2})", 1 , 3),
                // E-mail format
                Arguments.of("([a-z0-9]+)[@]([a-z0-9]+)[.]([a-z0-9]+)", 8 , 24),
                // Any number
                Arguments.of("(\\d+)", 4 , 8),
                // Any non-number
                Arguments.of("(\\D+)", 4 , 8),
                // Any word
                Arguments.of("(\\w+)", 4 , 8),
                // Any non-word
                Arguments.of("(\\W+)", 4 , 8),
                // Any text
                Arguments.of("(.*)", 4 , 8)
        );
    }
}
