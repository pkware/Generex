package com.mifmif.common.regex;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;


/**
 * @author Myk Kolisnyk
 */
public class GenerexTest {

    @ParameterizedTest
    @MethodSource("arguments")
    public void testMatchedStringsSizeShouldReturnExpectedValues(String regex, int totalExpectedMatches) {
        long size = new Generex(regex).matchedStringsSize();
        assertThat(size).isEqualTo(totalExpectedMatches);
    }

    @ParameterizedTest
    @MethodSource("arguments")
    public void testGetMatchedFirstMatchShouldBeTheSameAsMatchWithZeroIndex(String regex, int unused) {

        Generex generex = new Generex(regex);
        String firstMatch = generex.getFirstMatch();
        String matchStringZeroIndex = generex.getMatchedString(0);

        assertThat(firstMatch).matches(regex);
        assertThat(matchStringZeroIndex).matches(regex);
        assertThat(firstMatch).isEqualTo(matchStringZeroIndex);
    }

    @ParameterizedTest
    @MethodSource("arguments")
    public void testIterateThroughAllMatchesShouldReturnConsistentResults(String regex, int unused) {

        Generex generex = new Generex(regex);
        long total = generex.matchedStringsSize();
        for (int count = 1; count < total; count++) {
            String matchStringZeroIndex = generex.getMatchedString(count);
            assertWithMessage("The generated string '%s' doesn't match the pattern '%s' at iteration #%s", matchStringZeroIndex, regex, count)
                    .that(matchStringZeroIndex).matches(regex);
        }
    }

    @Test
    public void testSeed() {
        long seed = -5106534569952410475L;
        String pattern = "[0-9][a-zA-Z]";

        Generex firstGenerex = new Generex(pattern);
        firstGenerex.setSeed(seed);
        String firstValue = firstGenerex.random();

        Generex secondGenerex = new Generex(pattern);
        secondGenerex.setSeed(seed);
        String secondValue = secondGenerex.random();

        assertThat(firstValue).isEqualTo(secondValue);
    }

    @Test
    public void testSeedWithMinMaxQuantifier() {
        long seed = -5106534569952410475L;
        String pattern = "[A-Z]{1,10}";

        Generex firstGenerex = new Generex(pattern);
        firstGenerex.setSeed(seed);
        String firstValue = firstGenerex.random();

        Generex secondGenerex = new Generex(pattern);
        secondGenerex.setSeed(seed);
        String secondValue = secondGenerex.random();

        assertThat(firstValue).isEqualTo(secondValue);
    }

    static Stream<Arguments> arguments() {
        return Stream.of(
                // Sample multicharacter expression
                Arguments.of("[A-B]{5,9}", 992),
                // Sample expression
                Arguments.of("[0-3]([a-c]|[e-g]{1,2})", 60),
                // Number format
                Arguments.of("\\d{3,4}", 11000),
                // Any word
                Arguments.of("\\w{1,2}", 4032),
                // Empty string
                Arguments.of("", 1)
        );
    }
}
