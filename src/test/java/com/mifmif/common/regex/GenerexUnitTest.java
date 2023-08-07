/*
 * Copyright 2015 y.mifrah
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
package com.mifmif.common.regex;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dk.brics.automaton.Automaton;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@code Generex}.
 */
public class GenerexUnitTest {

	@Test
	public void shouldFailToCreateAnInstanceWithUndefinedPattern() {
		assertThrows(NullPointerException.class, () -> new Generex((String) null));
	}

	@Test
	public void shouldNotFailToCreateAnInstanceWithUndefinedAutomaton() {
		assertThat(new Generex((Automaton) null)).isNotNull();
	}

	@Test
	public void shouldReturnTrueWhenQueryingIfInfiniteWithInfinitePattern() {

		String infinitePattern = "a+";
		Generex generex = new Generex(infinitePattern);

		assertThat(generex.isInfinite()).isTrue();
	}

	@Test
	public void shouldReturnFalseWhenQueryingIfInfiniteWithFinitePattern() {

		String finitePattern = "a{5}";
		Generex generex = new Generex(finitePattern);

		assertThat(generex.isInfinite()).isFalse();
	}

	@Test
	public void shouldFailWhenQueryingIfInfiniteWithUndefinedAutomaton() {

		Generex generex = new Generex((Automaton) null);
		assertThrows(Exception.class, generex::isInfinite);
	}

	@Test
	public void shouldReturnTrueWhenQueryingIfInfiniteWithInfiniteAutomaton() {

		Automaton infiniteAutomaton = Automaton.makeChar('a').repeat(1); // same as "a+"
		Generex generex = new Generex(infiniteAutomaton);

		assertThat(generex.isInfinite()).isTrue();
	}

	@Test
	public void shouldReturnFalseWhenQueryingIfInfiniteWithFiniteAutomaton() {

		Automaton finiteAutomaton = Automaton.makeChar('a').repeat(5, 5); // same as "a{5}"
		Generex generex = new Generex(finiteAutomaton);

		assertThat(generex.isInfinite()).isFalse();
	}

	@Test
	public void shouldReturnIteratorOfAPattern() {

		Generex generex = new Generex("a");
		assertThat(generex.iterator()).isNotNull();
	}

	@Test
	public void shouldReturnIteratorOfAnAutomaton() {

		Automaton finiteAutomaton = Automaton.makeChar('a');
		Generex generex = new Generex(finiteAutomaton);

		assertThat(generex.iterator()).isNotNull();
	}

	@Test
	public void shouldFailToReturnIteratorOfUndefinedAutomaton() {

		Generex generex = new Generex((Automaton) null);
		assertThrows(NullPointerException.class, generex::iterator);
	}

	@Test
	public void shouldFailToValidateUndefinedPattern() {
		assertThrows(NullPointerException.class, () -> Generex.isValidPattern(null));
	}

	@Test
	public void shouldReturnTrueWhenValidatingValidPattern() {

		String validPattern = "[a-z0-9]{1,3}";
		assertThat(Generex.isValidPattern(validPattern)).isTrue();
	}

	@Test
	public void shouldReturnTrueWhenValidatingValidPatternWithPredefinedClasses() {

		String validPattern = "\\d{2,3}\\w{1}";
		assertThat(Generex.isValidPattern(validPattern)).isTrue();
	}

	@Test
	public void shouldReturnFalseWhenValidatingInvalidPattern() {

		String invalidPattern = "a)";
		assertThat(Generex.isValidPattern(invalidPattern)).isFalse();
	}

	@Test
	public void shouldReturnFalseWhenValidatingPatternWithRepetitionsHigherThanMaxIntegerValue() {

		String invalidPattern = "[a-z0-9]{" + (1L + Integer.MAX_VALUE) + "}";
		assertThat(Generex.isValidPattern(invalidPattern)).isFalse();
	}

	@Test
	public void shouldReturnFalseWhenValidatingPatternWithHigherNumberOfTransitions() {

		String invalidPattern = createPatternWithTransitions(1000000);
		assertThat(Generex.isValidPattern(invalidPattern)).isFalse();
	}

	private static String createPatternWithTransitions(int numberOfTransitions) {
		StringBuilder strBuilder = new StringBuilder(numberOfTransitions * 2);
		for (int i = 1; i < numberOfTransitions; i++) {
			strBuilder.append('a').append('|');
		}
		if (numberOfTransitions > 0) {
			strBuilder.append('a');
		}
		return strBuilder.toString();
	}
}
