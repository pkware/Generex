We want to try to match the length of the generated regex with a predefined length of a uniform distribution. Note that this would theoretically only really make the length distribution even, not necessarily the regexes generated.

Works with the most simplest of cases, but currently breaks down even for the length distribution if we have a variable length element somewhere not at the end.
- THis is because we might skip past the wanted length with the variable length start, and then never bother rewinding because we still would accept a valid match in the min-max range.
- This isn't a problem for the variable ending regexes, because we are forced to grow until we reach the wanted length, and in that case we terminate.

For groups of repeating text, we also don't do a great job because we still pick a value from the uniform distribution even if its not possible to generate a regex of that value. In that case, we always grow to the longest value, so distribution wise things get skewed.
- Can sort of "fix" this by having a "best non ideal match" where we try to match the length closest to the target value.
- I don't super care if this gets accurately fixed or not, but we should probably have something that tries to make it so we don't have a huge skew towards the longest match.

Arguments.of("a*123", 5, 20),
Arguments.of("(abcde){1,5}", 5, 25),