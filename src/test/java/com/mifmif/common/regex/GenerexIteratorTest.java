package com.mifmif.common.regex;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

/**
 * @author Myk Kolisnyk
 */
public class GenerexIteratorTest {

    @ParameterizedTest
    @MethodSource("regexData")
    public void testIterateThroughAllGeneratedStrings(String pattern) {

        for (String result : new Generex(pattern)) {
            assertThat(result).matches(pattern);
        }
    }

    @ParameterizedTest
    @MethodSource("regexData")
    public void testIterateShouldReturnTheSameAsGetMatchedStrings(String pattern) {

        Generex generex = new Generex(pattern);
        int count = 1;
        for (String result : generex) {
            String matchedResult = generex.getMatchedString(count);
            assertWithMessage("Iteration %s mismatch", count++).that(matchedResult).matches(result);
        }

        assertWithMessage("Incorrect number of iterated strings").that(generex.matchedStringsSize()).isEqualTo(count - 1);
    }

    static Stream<Arguments> regexData() {
        return Stream.of(
                // Sample multicharacter expression
                Arguments.of("[A-B]{5,9}"),
                // Sample expression
                Arguments.of("[0-3]([a-c]|[e-g]{1,2})"),
                // Number format
                Arguments.of("\\d{3,4}"),
                // Any word
                Arguments.of("\\w{1,2}"),
                // Empty string
                Arguments.of("")
        );
    }
}
