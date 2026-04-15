/*
 * Copyright 2014 y.mifrah
 *

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

package com.pkware.generex;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.State;
import dk.brics.automaton.Transition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Java utility class that help generating string values that match a given regular expression.It generate all values
 * that are matched by the Regex, a random value, or you can generate only a specific string based on it's
 * lexicographical order .
 *
 * @author y.mifrah
 */
public class Generex implements Iterable<String> {

    /**
     * The predefined character classes supported by {@code Generex}.
     * <p>
     * An immutable map containing as keys the character classes and values the equivalent regular expression syntax.
     *
     * @see #createRegExp(String)
     */
    private static final Map<String, String> PREDEFINED_CHARACTER_CLASSES;
    private RegExp regExp;
    private Automaton automaton;
    private List<String> matchedStrings = new ArrayList<String>();
    private Node rootNode;
    private boolean isTransactionNodeBuilt;

    /**
     * Minimum length of any string this regex accepts. Populated on first access by
     * {@link #calculateLengthBounds()}; {@code null} until then (doubles as the cache-populated flag).
     */
    private Integer cachedMinLength;

    /**
     * The regex's own upper bound on generated string length. Populated on first access by
     * {@link #calculateLengthBounds()}. For infinite regexes this is {@link Integer#MAX_VALUE}
     * (no natural cap) so that {@code Math.min(userMax, cachedMaxLength)} collapses to the user's
     * value. Callers that need a default when the user supplied no max should use
     * {@link #DEFAULT_INFINITE_MAX_LENGTH} instead for infinite regexes.
     */
    private Integer cachedMaxLength;

    /**
     * Fallback maximum length used by {@link #random(int)} (and overloads that delegate to it)
     * when the regex is infinite and the caller did not supply their own {@code maxLength}.
     */
    public static final int DEFAULT_INFINITE_MAX_LENGTH = 50;

    static {
        Map<String, String> characterClasses = new HashMap<String, String>();
        characterClasses.put("\\\\d", "[0-9]");
        characterClasses.put("\\\\D", "[^0-9]");
        characterClasses.put("\\\\s", "[ \t\n\f\r]");
        characterClasses.put("\\\\S", "[^ \t\n\f\r]");
        characterClasses.put("\\\\w", "[a-zA-Z_0-9]");
        characterClasses.put("\\\\W", "[^a-zA-Z_0-9]");
        PREDEFINED_CHARACTER_CLASSES = Collections.unmodifiableMap(characterClasses);
    }

    public Generex(String regex) {
        this(regex, new Random());
    }

    public Generex(Automaton automaton) {
        this(automaton, new Random());
    }

    public Generex(String regex, Random random) {
        regex = requote(regex);
        regExp = createRegExp(regex);
        automaton = regExp.toAutomaton();
        this.random = random;
    }

    public Generex(Automaton automaton, Random random) {
        this.automaton = automaton;
        this.random = random;
    }

    /**
     * Creates a {@code RegExp} instance from the given regular expression.
     * <p>
     * Predefined character classes are replaced with equivalent regular expression syntax prior creating the instance.
     *
     * @param regex the regular expression used to build the {@code RegExp} instance
     * @return a {@code RegExp} instance for the given regular expression
     * @throws NullPointerException     if the given regular expression is {@code null}
     * @throws IllegalArgumentException if an error occurred while parsing the given regular expression
     * @throws StackOverflowError       if the regular expression has to many transitions
     * @see #PREDEFINED_CHARACTER_CLASSES
     * @see #isValidPattern(String)
     */
    private static RegExp createRegExp(String regex) {
        String finalRegex = convertToBricsRegex(regex);
        for (Entry<String, String> charClass : PREDEFINED_CHARACTER_CLASSES.entrySet()) {
            finalRegex = finalRegex.replaceAll(charClass.getKey(), charClass.getValue());
        }
        return new RegExp(finalRegex);
    }

    /**
     * Converts a regex pattern to brics-compatible syntax for use with Generex.
     *
     * <p>Performs the following transformations:
     * <ul>
     *   <li>Removes an unescaped {@code ^} anchor at the start of the pattern, as brics treats
     *       {@code ^} as a literal character rather than a start-of-input assertion.</li>
     *   <li>Removes an unescaped {@code $} anchor at the end of the pattern, as brics treats
     *       {@code $} as a literal character rather than an end-of-input assertion.</li>
     *   <li>Converts non-capturing groups {@code (?:...)} to plain capturing groups {@code (...)},
     *       since brics does not support non-capturing group syntax. This is a lossless
     *       transformation because Generex only generates strings and never extracts capture
     *       groups.</li>
     * </ul>
     *
     * <p>The conversion is performed in a single pass that tracks escape sequences and character
     * class boundaries to avoid incorrect replacements.
     *
     * @param regex The Java regex pattern to convert.
     * @return the brics-compatible regex.
     */
    @NotNull
    private static String convertToBricsRegex(@NotNull String regex) {
        if (regex.isEmpty()) return regex;

        StringBuilder result = new StringBuilder(regex.length());
        boolean escaped = false;
        boolean inCharClass = false;
        int start = 0;

        // Strip leading ^ anchor (not escaped since it's the first character)
        if (regex.charAt(0) == '^') {
            start = 1;
        }

        for (int i = start; i < regex.length(); i++) {
            char c = regex.charAt(i);

            if (escaped) {
                result.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                result.append(c);
                escaped = true;
                continue;
            }

            if (inCharClass) {
                if (c == ']') inCharClass = false;
                result.append(c);
                continue;
            }

            if (c == '[') {
                inCharClass = true;
                result.append(c);
                int next = i + 1;
                // Per regex standard, ] right after [ or [^ is a literal ] inside the class, not the closing bracket.
                if (next < regex.length() && regex.charAt(next) == '^') {
                    result.append('^');
                    next++;
                }
                if (next < regex.length() && regex.charAt(next) == ']') {
                    result.append(']');
                    i = next;
                }
                continue;
            }

            // Convert (?:...) to (...) — only outside character classes and not escaped
            if (c == '(' && i + 2 < regex.length() && regex.charAt(i + 1) == '?' && regex.charAt(i + 2) == ':') {
                result.append('(');
                i += 2;
                continue;
            }

            result.append(c);
        }

        // Strip trailing $ anchor if the last character is an unescaped $
        if (result.length() > 0 && result.charAt(result.length() - 1) == '$') {
            // Count preceding backslashes — odd means $ is escaped, even means $ is an anchor
            int backslashes = 0;
            for (int i = result.length() - 2; i >= 0 && result.charAt(i) == '\\'; i--) {
                backslashes++;
            }
            if (backslashes % 2 == 0) {
                result.deleteCharAt(result.length() - 1);
            }
        }

        return result.toString();
    }

    /**
     * initialize the random instance used with a seed value  to generate a
     * pseudo random suite of strings based on the passed seed and matches the used regular expression
     * instance
     *
     * @param seed
     */
    public void setSeed(long seed) {
        random = new Random(seed);
    }

    /**
     * @param indexOrder ( 1&lt;= indexOrder &lt;=n)
     * @return The matched string by the given pattern in the given it's order in the sorted list of matched String.<br>
     * <code>indexOrder</code> between 1 and <code>n</code> where <code>n</code> is the number of matched String.<br> If
     * indexOrder &gt;= n , return an empty string. if there is an infinite number of String that matches the given Regex,
     * the method throws {@code StackOverflowError}
     */
    public String getMatchedString(int indexOrder) {
        buildRootNode();
        if (indexOrder == 0)
            indexOrder = 1;
        String result = buildStringFromNode(rootNode, indexOrder);
        result = result.substring(1, result.length() - 1);
        return result;
    }

    private String buildStringFromNode(Node node, int indexOrder) {
        String result = "";
        long passedStringNbr = 0;
        long step = node.getNbrMatchedString() / node.getNbrChar();
        for (char usedChar = node.getMinChar(); usedChar <= node.getMaxChar(); ++usedChar) {
            passedStringNbr += step;
            if (passedStringNbr >= indexOrder) {
                passedStringNbr -= step;
                indexOrder -= passedStringNbr;
                result = result.concat("" + usedChar);
                break;
            }
        }
        long passedStringNbrInChildNode = 0;
        if (result.length() == 0)
            passedStringNbrInChildNode = passedStringNbr;
        for (Node childN : node.getNextNodes()) {
            passedStringNbrInChildNode += childN.getNbrMatchedString();
            if (passedStringNbrInChildNode >= indexOrder) {
                passedStringNbrInChildNode -= childN.getNbrMatchedString();
                indexOrder -= passedStringNbrInChildNode;
                result = result.concat(buildStringFromNode(childN, indexOrder));
                break;
            }
        }
        return result;
    }

    /**
     * Tells whether or not the given pattern (or {@code Automaton}) is infinite, that is, generates an infinite number
     * of strings.
     * <p>
     * For example, the pattern "a+" generates an infinite number of strings whether "a{5}" does not.
     *
     * @return {@code true} if the pattern (or {@code Automaton}) generates an infinite number of strings, {@code false}
     * otherwise
     */
    public boolean isInfinite() {
        return !automaton.isFinite();
    }

    /**
     * @return first string in lexicographical order that is matched by the given pattern.
     */
    public String getFirstMatch() {
        buildRootNode();
        Node node = rootNode;
        String result = "";
        while (node.getNextNodes().size() > 0) {
            result = result.concat("" + node.getMinChar());
            node = node.getNextNodes().get(0);
        }
        result = result.substring(1);
        return result;
    }

    /**
     * @return the number of strings that are matched by the given pattern.
     * @throws StackOverflowError if the given pattern generates a large, possibly infinite, number of strings.
     */
    public long matchedStringsSize() {
        buildRootNode();
        return rootNode.getNbrMatchedString();
    }

    /**
     * Prepare the rootNode and it's child nodes so that we can get matchedString by index
     */
    private void buildRootNode() {
        if (isTransactionNodeBuilt)
            return;
        isTransactionNodeBuilt = true;
        rootNode = new Node();
        rootNode.setNbrChar(1);
        List<Node> nextNodes = prepareTransactionNodes(automaton.getInitialState());
        rootNode.setNextNodes(nextNodes);
        rootNode.updateNbrMatchedString();
    }

    private int matchedStringCounter = 0;

    private void generate(String strMatch, State state, int limit) {
        if (matchedStringCounter == limit)
            return;
        ++matchedStringCounter;
        List<Transition> transitions = state.getSortedTransitions(true);
        if (transitions.size() == 0) {
            matchedStrings.add(strMatch);
            return;
        }
        if (state.isAccept()) {
            matchedStrings.add(strMatch);
        }
        for (Transition transition : transitions) {
            for (char c = transition.getMin(); c <= transition.getMax(); ++c) {
                generate(strMatch + c, transition.getDest(), limit);
            }
        }
    }

    /**
     * Build list of nodes that present possible transactions from the <code>state</code>.
     *
     * @param state
     * @return
     */
    private List<Node> prepareTransactionNodes(State state) {

        List<Node> transactionNodes = new ArrayList<Node>();
        if (preparedTransactionNode == Integer.MAX_VALUE / 2)
            return transactionNodes;
        ++preparedTransactionNode;

        if (state.isAccept()) {
            Node acceptedNode = new Node();
            acceptedNode.setNbrChar(1);
            transactionNodes.add(acceptedNode);
        }
        List<Transition> transitions = state.getSortedTransitions(true);
        for (Transition transition : transitions) {
            Node trsNode = new Node();
            int nbrChar = transition.getMax() - transition.getMin() + 1;
            trsNode.setNbrChar(nbrChar);
            trsNode.setMaxChar(transition.getMax());
            trsNode.setMinChar(transition.getMin());
            List<Node> nextNodes = prepareTransactionNodes(transition.getDest());
            trsNode.setNextNodes(nextNodes);
            transactionNodes.add(trsNode);
        }
        return transactionNodes;
    }

    private int preparedTransactionNode;
    private Random random;

    /**
     * Generate all Strings that matches the given Regex.
     *
     * @return
     */
    public List<String> getAllMatchedStrings() {
        matchedStrings = new ArrayList<String>();
        generate("", automaton.getInitialState(), Integer.MAX_VALUE);
        return matchedStrings;

    }

    /**
     * Generate subList with a size of <code>limit</code> of Strings that matches the given Regex. the Strings are
     * ordered in lexicographical order.
     *
     * @param limit
     * @return
     */
    public List<String> getMatchedStrings(int limit) {
        matchedStrings = new ArrayList<String>();
        generate("", automaton.getInitialState(), limit);
        return matchedStrings;

    }

    /**
     * See {@link #random(int, int)}
     */
    public String random() {
        return random(1);
    }

    /**
     * See {@link #random(int, int)}
     */
    public String random(int minLength) {
        calculateLengthBounds();
        // cachedMaxLength is Integer.MAX_VALUE for infinite regexes; fall back to the friendlier
        // default since the caller didn't specify their own cap.
        int defaultMaxLength = isInfinite() ? DEFAULT_INFINITE_MAX_LENGTH : cachedMaxLength;
        return random(minLength, defaultMaxLength);
    }

    /**
     * Attempts to generate a string that would match the regex stored by this {@link Generex} instance that has a
     * length in the range {@code [minLength, maxLength]}.
     * <p></p>
     * If the provided regex can't generate a matching string less than or equal to {@code maxLength}, then a random
     * string that matches the start of the regex will be generated with a length of {@code maxLength}. If the provided
     * regex can't generate a matching string greater than or equal to {@code minLength}, then a string with the maximum
     * length possible is returned.
     * <p></p>
     * When a string is generated that may be an infinite length, it is attempted to be randomly generated in a way
     * where repeated generations will produce a uniform distribution of lengths between `minLength` and `maxLength`. In
     * practice, the accuracy of this depends on the complexity of the regex.
     *
     * @param minLength Minimum wanted length of the generated string. The generated string may be smaller if the given
     *                  regex can't produce a string of the wanted length. Assumed to be less than or equal to {@code maxLength}.
     * @param maxLength Maximum wanted length of the generated string. The generated string <b>will be trimmed</b> to
     *                  this length if the regex can't generate a string less than this value. Assumed to be greater than or equal to
     *                  {@code minLength}.
     * @return A string between {@code minLength} and {@code maxLength} if the regex provided can match a string in the
     * given range. Otherwise, see the {@code minLength} and {@code maxLength} docs.
     */
    public String random(int minLength, int maxLength) {
        calculateLengthBounds();

        // Calculate actual valid range by comparing the regex and the user defined bounds.
        // For infinite regexes cachedMaxLength is Integer.MAX_VALUE, so the min() leaves maxLength alone.
        int actualMinLength = Math.max(minLength, cachedMinLength);
        int actualMaxLength = Math.min(maxLength, cachedMaxLength);

        // Pre-select target length uniformly from valid range
        int targetLength;
        if (actualMinLength > actualMaxLength) {
            targetLength = actualMaxLength;
        } else {
            targetLength = actualMinLength + random.nextInt(actualMaxLength - actualMinLength + 1);
        }

        String result = prepareRandom("", automaton.getInitialState(), minLength, maxLength, targetLength, isInfinite() ? new AttemptBudget() : null);
        // Substring in case a length of 'maxLength + 1' is returned, which is possible if a smaller string can't be produced.
        return result.substring(0, Math.min(maxLength, result.length()));
    }

    /**
     * Mutable counter shared by reference across recursive calls to {@link #prepareRandom},
     * used to cap the total number of iterations and prevent exponential backtracking
     * for infinite regexes.
     */
    private static class AttemptBudget {
        private static final int MAX_ATTEMPTS = 1000;
        int count = 0;

        boolean isExhausted() {
            return count >= MAX_ATTEMPTS;
        }

        void increment() {
            count++;
        }
    }

    /**
     * Recursive function used to generate a regex as defined by {@link Generex#random(int, int)}.
     *
     * @param currentMatch A string built from the accumulation of previous transitions.
     * @param state        Current state of the regex.
     * @param minLength    Minimum wanted length of the produced string.
     * @param maxLength    Maximum wanted length of produced string.
     * @param targetLength The desired length of the produced string, pre-selected uniformly from the valid range.
     * @param budget       Shared attempt counter to limit recursion for infinite regexes, or {@code null} for finite regexes.
     * @return A string built from the accumulation of previous transitions.
     */
    private String prepareRandom(String currentMatch, State state, int minLength, int maxLength, int targetLength, AttemptBudget budget) {

        // Return a string of length 'maxLength + 1' to indicate a dead branch.
        if (currentMatch.length() > maxLength || state.getTransitions().isEmpty()) return currentMatch;

        // For infinite regexes, the automaton has cycles that can cause exponential recursion.
        // This budget limit caps total recursive iterations to prevent hanging.
        if (budget != null && budget.isExhausted()) return currentMatch;

        String returnValue = null;

        if (state.isAccept()) {
            // Set the current match to the value to return, just in case this would happen to be the cloest match to
            // the target length.
            returnValue = currentMatch;

            if (currentMatch.length() == targetLength) return currentMatch;
        }

        // Make a copy so the original set is never modified.
        Set<Transition> possibleTransitions = new HashSet<>(state.getTransitions());
        int totalWeightedTransitions = calculateTotalWeightedTransitions(possibleTransitions);

        // Will never start as empty due to the initial if statement in the function.
        while (!possibleTransitions.isEmpty()) {
            if (budget != null) {
                budget.increment();
                if (budget.isExhausted()) break;
            }

            Transition randomTransition = pickRandomWeightedTransition(possibleTransitions, totalWeightedTransitions);
            int subTransitions = getWeightedTransitions(randomTransition);
            totalWeightedTransitions -= subTransitions;
            possibleTransitions.remove(randomTransition);

            char randomChar = (char) (random.nextInt(subTransitions) + randomTransition.getMin());
            String result = prepareRandom(currentMatch + randomChar, randomTransition.getDest(), minLength, maxLength, targetLength, budget);

            // Greedily return the first valid result found that is of the wanted length..
            if (result.length() == targetLength) return result;

            returnValue = getBestMatch(result, returnValue, minLength, maxLength, targetLength);
        }

        // For infinite regexes, if budget was exhausted before reaching an accept state, return currentMatch
        // as a fallback instead of null.
        return returnValue != null ? returnValue : currentMatch;
    }

    /**
     * Determines if the new generation is better than the current generation.
     * <p></p>
     * The new generation is better if it is within the bounds and is closer to the target length than the current
     * generation. Otherwise, the current generation is better.
     *
     * @param newMatch the new generation to compare against the current generation.
     * @param currentMatch the current generation.
     * @param min minimum length of the generated string.
     * @param max maximum length of the generated string.
     * @param target the target length of the generated string.
     * @return the best match between the new generation and the current generation.
     */
    private String getBestMatch(String newMatch, String currentMatch, int min, int max, int target) {

        if (currentMatch == null) return newMatch;
        if (newMatch.length() > max && currentMatch.length() > min) return currentMatch;

        boolean newInRange = newMatch.length() >= min;
        boolean currentInRange = currentMatch.length() >= min && currentMatch.length() <= max;

        if (newInRange && !currentInRange) return newMatch;
        if (currentInRange && !newInRange) return currentMatch;

        int currentTargetDistance = Math.abs(currentMatch.length() - target);
        int newTargetDistance = Math.abs(newMatch.length() - target);

        if (newTargetDistance < currentTargetDistance) return newMatch;
        return currentMatch;
    }

    /**
     * Calculate the possible bounds of the generated string by traversing the regex.
     * <br>
     * For finite automatons, both {@code cachedMinLength} and {@code cachedMaxLength} are populated
     * from the DFS. For infinite automatons, {@code cachedMinLength} is computed from a BFS to the
     * nearest accepting state, and {@code cachedMaxLength} is set to {@link Integer#MAX_VALUE}
     * (meaning "no natural upper bound").
     */
    private void calculateLengthBounds() {
        if (cachedMinLength != null) return;

        if (automaton.isFinite()) {
            int[] bounds = dfsLengthBounds(automaton.getInitialState(), new HashMap<>());
            cachedMinLength = bounds[0];
            cachedMaxLength = bounds[1];
        } else {
            cachedMinLength = bfsMinLength(automaton.getInitialState());
            cachedMaxLength = Integer.MAX_VALUE;
        }
    }

    /**
     * Uses a memoized depth first search to calculate the minimum and maximum length of the regex
     * by traversing through the automaton.
     * <br>
     * Assumes the automaton is finite (acyclic). Under that assumption each state's bounds depend
     * only on the state itself, so results can be cached in {@code memo}. Without memoization,
     * automatons shaped like a chain of states with multiple parallel transitions (e.g.
     * {@code [a-zA-Z0-9]{1,100}}, which determinizes to ~3 range-transitions per state) would be
     * explored along every path — exponential in the chain length. Memoization makes this linear
     * in the number of states.
     *
     * @param state the current state of the automaton.
     * @param memo  cached bounds for states whose subtree has already been computed.
     * @return an int array containing the minimum and maximum length of the regex.
     */
    private int[] dfsLengthBounds(State state, Map<State, int[]> memo) {
        int[] cached = memo.get(state);
        if (cached != null) return cached;

        int minLength = state.isAccept() ? 0 : Integer.MAX_VALUE;
        int maxLength = 0;

        for (Transition transition : state.getTransitions()) {
            int[] bounds = dfsLengthBounds(transition.getDest(), memo);
            if (bounds[0] != Integer.MAX_VALUE) {
                minLength = Math.min(minLength, bounds[0] + 1);
            }
            maxLength = Math.max(maxLength, bounds[1] + 1);
        }

        int[] result = {minLength, maxLength};
        memo.put(state, result);
        return result;
    }

    /**
     * Computes the minimum length of any string the automaton accepts, via a breadth-first search
     * from {@code initial} to the nearest accepting state.
     * <br>
     * Used for infinite (cyclic) automatons where the acyclic-memoized DFS assumption does not
     * hold. Returns {@code 0} if {@code initial} itself is accepting. Returns
     * {@link Integer#MAX_VALUE} if no accepting state is reachable (not expected for a valid regex).
     *
     * @param initial the state to search from.
     * @return the shortest number of transitions needed to reach an accepting state.
     */
    private int bfsMinLength(State initial) {
        Set<State> visited = new HashSet<>();
        ArrayDeque<State> currentLevel = new ArrayDeque<>();
        ArrayDeque<State> nextLevel = new ArrayDeque<>();

        currentLevel.add(initial);
        visited.add(initial);

        int depth = 0;
        while (!currentLevel.isEmpty()) {
            for (State state : currentLevel) {
                if (state.isAccept()) return depth;
                for (Transition transition : state.getTransitions()) {
                    State dest = transition.getDest();
                    if (visited.add(dest)) nextLevel.add(dest);
                }
            }
            ArrayDeque<State> tmp = currentLevel;
            currentLevel = nextLevel;
            nextLevel = tmp;
            nextLevel.clear();
            depth++;
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Returns a {@link Transition} from the given collection randomly based on the total number of characters that
     * {@link Transition} can produce.
     *
     * @param transitions              Collection of transitions to choose from.
     * @param totalWeightedTransitions The sum of the total number of characters each transition could produce.
     * @throws IllegalArgumentException When {@code totalWeightedTransitions} is not equal to the sum of all weighted
     *                                  transitions in {@code transitions}.
     */
    private Transition pickRandomWeightedTransition(Collection<Transition> transitions, int totalWeightedTransitions) {

        int value = random.nextInt(totalWeightedTransitions) + 1;
        for (Transition transition : transitions) {

            value -= getWeightedTransitions(transition);
            if (value <= 0) return transition;
        }

        throw new IllegalArgumentException(
                "totalWeightedTransitions was greater than the total number of weighted transitions in the supplied collection."
        );
    }

    /**
     * Calculates the sum of all the {@link #getWeightedTransitions(Transition) weighted transitions} from the given collection.
     */
    private static int calculateTotalWeightedTransitions(Collection<Transition> transitions) {

        int totalWeight = 0;
        for (Transition transition : transitions) totalWeight += getWeightedTransitions(transition);

        return totalWeight;
    }

    /**
     * Calculates the number of different characters a {@link Transition} could produce.
     */
    private static int getWeightedTransitions(Transition transition) {
        return transition.getMax() - transition.getMin() + 1;
    }

    public Iterator<String> iterator() {
        return new GenerexIterator(automaton.getInitialState());
    }

    /**
     * Tells whether or not the given regular expression is a valid pattern (for {@code Generex}).
     *
     * @param regex the regular expression that will be validated
     * @return {@code true} if the regular expression is valid, {@code false} otherwise
     * @throws NullPointerException if the given regular expression is {@code null}
     */
    public static boolean isValidPattern(String regex) {
        try {
            createRegExp(regex);
            return true;
        } catch (IllegalArgumentException ignore) { // NOPMD - Not valid.
        } catch (StackOverflowError ignore) { // NOPMD - Possibly valid but stack not big enough to handle it.
        }
        return false;
    }

    /**
     * Requote a regular expression by escaping some parts of it from generation without need to escape each special
     * character one by one. <br> this is done by setting the part to be interpreted as normal characters (thus, quote
     * all meta-characters) between \Q and \E , ex : <br> <code> minion_\d{3}\Q@gru.evil\E </code> <br> will be
     * transformed to : <br> <code> minion_\d{3}\@gru\.evil </code>
     *
     * @param regex
     * @return
     */
    private static String requote(String regex) {
        final Pattern patternRequoted = Pattern.compile("(?s)(?<!\\\\)(?:\\\\{2})*+\\\\Q(.*?)\\\\E");
        // http://stackoverflow.com/questions/399078/what-special-characters-must-be-escaped-in-regular-expressions
        // adding "@" prevents StackOverflowError inside generex: https://github.com/mifmif/Generex/issues/21
        final Pattern patternSpecial = Pattern.compile("[.^$*+?(){|\\[\\\\@]");

        StringBuilder sb = new StringBuilder();
        Matcher matcher = patternRequoted.matcher(regex);
        int index = 0;

        while (matcher.find()) {
            sb.append(regex, index, matcher.start(1) - 2); // Don't include \Q
            sb.append(patternSpecial.matcher(matcher.group(1)).replaceAll("\\\\$0"));
            index = matcher.end(1) + 2; // Don't include \E
        }

        sb.append(regex.substring(index));
        return sb.toString();
    }
}
