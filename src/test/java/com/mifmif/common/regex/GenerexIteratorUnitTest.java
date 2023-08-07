/*
 * Copyright 2015 y.mifrah
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *	 http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mifmif.common.regex;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.State;
import dk.brics.automaton.Transition;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@code GenerexIterator}.
 */
public class GenerexIteratorUnitTest {

	@Test
	public void shouldFailToCreateIteratorWithUndefinedInitialState() {
		assertThrows(NullPointerException.class, () -> new GenerexIterator(null));
	}

	@Test
	public void shouldNotHaveNextIfInitialStateIsNotAcceptedAndHasNoTransitions() {

		State initialStateWithoutTransitions = new State();
		GenerexIterator iterator = new GenerexIterator(initialStateWithoutTransitions);

		assertThat(iterator.hasNext()).isFalse();
	}

	@Test
	public void shouldHaveNextIfInitialStateIsAcceptedEvenIfHasNoTransitions() {

		State acceptedState = new State();
		acceptedState.setAccept(true);
		GenerexIterator iterator = new GenerexIterator(acceptedState);

		assertThat(iterator.hasNext()).isTrue();
		assertThat(iterator.next()).isEqualTo("");
	}

	@Test
	public void shouldNotHaveNextIfInitialStateHasJustTransitionToRejectedState() {

		State initialState = new State();
		State rejectedState = new State();
		initialState.addTransition(new Transition('a', rejectedState));
		GenerexIterator iterator = new GenerexIterator(initialState);
		assertThat(iterator.hasNext()).isFalse();
	}

	@Test
	public void shouldHaveNextIfInitialStateHasTransitionToAcceptedState() {

		State initialState = new State();
		State acceptedState = new State();
		acceptedState.setAccept(true);
		initialState.addTransition(new Transition('a', acceptedState));
		GenerexIterator iterator = new GenerexIterator(initialState);

		assertThat(iterator.hasNext()).isTrue();
		assertThat(iterator.next()).isEqualTo("a");
	}

	@Test
	public void shouldFailToObtainNextIfDoesNotHaveNext() {

		State rejectedInitialState = new State();
		GenerexIterator iterator = new GenerexIterator(rejectedInitialState);

		assertThrows(IllegalStateException.class, iterator::next);
	}

	@Test
	public void shouldReturnSameStringEvenIfHasNextIsCalledMultipleTimes() {

		State initialState = Automaton.makeChar('a').union(Automaton.makeChar('b')).getInitialState();
		GenerexIterator iterator = new GenerexIterator(initialState);

		boolean hasNext = iterator.hasNext() & iterator.hasNext() & iterator.hasNext();
		assertThat(hasNext).isTrue();
		assertThat(iterator.next()).isEqualTo("a");
	}

	@Test
	public void shouldReturnFollowingStringsIfNextIsCalledMultipleTimes() {

		State initialState = Automaton.makeChar('a').union(Automaton.makeChar('b')).getInitialState();
		GenerexIterator iterator = new GenerexIterator(initialState);

		String next = iterator.next();
		String followingNext = iterator.next();

		assertThat(next).isEqualTo("a");
		assertThat(followingNext).isEqualTo("b");
	}

	@Test
	public void shouldReturnFalseIfNoNextEvenIfHasNextIsCalledMultipleTimes() {

		State initialState = Automaton.makeEmpty().getInitialState();
		GenerexIterator iterator = new GenerexIterator(initialState);

		boolean hasNext = iterator.hasNext() | iterator.hasNext() | iterator.hasNext();
		assertThat(hasNext).isFalse();
	}

	@Test
	public void shouldIteratorOverRangeOfChars() {

		char min = Character.MIN_VALUE;
		char max = Character.MAX_VALUE;
		State initialState = Automaton.makeCharRange(min, max).getInitialState();
		GenerexIterator iterator = new GenerexIterator(initialState);

		for (int character = min; character <= max; character++) {
			assertThat(iterator.hasNext()).isTrue();
			assertThat(iterator.next()).isEqualTo(createString((char) character, 1));
		}

		assertThat(iterator.hasNext()).isFalse();
	}

	@Test
	public void shouldIteratorOverCharWithVariableLength() {

		char character = 'a';
		int minLength = 1;
		int maxLength = 15;
		State initialState = Automaton.makeChar(character).repeat(minLength, maxLength).getInitialState();
		GenerexIterator iterator = new GenerexIterator(initialState);

		for (int count = minLength; count <= maxLength; count++) {
			assertThat(iterator.hasNext()).isTrue();
			assertThat(iterator.next()).isEqualTo(createString(character, count));
		}

		assertThat(iterator.hasNext()).isFalse();
	}

	@Test
	public void shouldIteratorOverInfiniteState() {

		char character = 'a';
		int max = 1000;
		State initialState = Automaton.makeChar(character).repeat(1).getInitialState();
		GenerexIterator iterator = new GenerexIterator(initialState);

		for (int count = 1; count <= max; count++) {
			assertThat(iterator.hasNext()).isTrue();
			assertThat(iterator.next()).isEqualTo(createString(character, count));
		}

		assertThat(iterator.hasNext()).isTrue();
	}

	private static String createString(char character, int length) {
		StringBuilder strBuilder = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			strBuilder.append(character);
		}
		return strBuilder.toString();
	}
}
