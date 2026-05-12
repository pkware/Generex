# Generex limitations

Generex generates strings from a regex, but it doesn't understand the full Java regex dialect.
This is a reference for what won't work, and what to do about it.

**Rule of thumb**: always validate generated output with
`Pattern.compile(yourPattern).matcher(generated).matches()` before trusting it. If that fails,
your pattern is in one of the categories below.

---

## Patterns that don't work

| Pattern feature | Example | Workaround |
| --- | --- | --- |
| Lookahead / lookbehind | `(?=...)`, `(?<!...)` | Restructure the regex to not need zero-width assertions. |
| Backreferences | `\1`, `\k<name>` | No workaround — restructure the regex without them. |
| Named groups | `(?<name>...)` | Use plain `(...)`. |
| Inline flags | `(?i)`, `(?s)`, `(?m)`, `(?x)`, `(?i:...)` | Encode case-insensitivity by hand: `[Aa][Bb][Cc]`. |
| Unicode property escapes | `\p{L}`, `\p{Digit}`, `\P{...}` | List the characters you actually want explicitly. |
| Word boundary | `\b`, `\B` | Not expressible; restructure. |
| Character-class intersection | `[a-z&&[^aeiou]]` | List the actual characters: `[b-df-hj-np-tv-z]`. |
| Possessive / reluctant quantifiers | `*+`, `++`, `*?`, `+?` | Use the plain forms — generation doesn't care about greediness. |
| Octal / hex / control escapes | `\012`, `\x1F`, `\cX` | Embed the character literally. |

If your pattern uses any of these, `Generex.isValidPattern(...)` may still return `true` and
generation may still produce output — it'll just be the wrong language. Always round-trip
through `Pattern.matches(...)`.

---

## Characters that are special to Generex even when Java treats them as literals

Generex parses patterns with a Brics-flavored engine that treats these as operators **outside**
character classes:

| Character | What Generex does | Escape as |
| --- | --- | --- |
| `&` | Intersection | `\&` |
| `~` | Complement | `\~` |
| `#` | Empty language | `\#` |
| `@` | Any string | `\@` |
| `"..."` | Literal string | `\"...\"` |
| `<10-99>` | Numerical range | `\<10-99\>` |

If your pattern contains any of these as data, escape them before constructing the `Generex`.

---

## Operational caveats

- **Infinite regexes (`a*`, `(ab)+`, `\w+`) default to a 50-character cap** when calling
  `generex.random()` with no arguments. Pass explicit `random(min, max)` to override.
- **Infinite regexes use a 1000-iteration budget.** If a pattern is structured so that finding a
  match would require more, Generex returns the closest partial match it found — which may not
  actually match the regex.
- **`matchedStringsSize()` / `getMatchedString(n)` overflow silently** on languages with more
  than `Long.MAX_VALUE` matches (`[a-zA-Z0-9]{1,30}` etc.).
- **`getAllMatchedStrings()` materializes the entire language.** Prefer `iterator()` for anything
  non-trivial.
- **`\D`, `\S`, `\W` cover the full Unicode BMP** — including control characters, surrogates,
  and unassigned codepoints. `[\D]` will happily produce a NUL byte or `￾`. If you need
  printable output, list the allowed characters explicitly (e.g. `[a-zA-Z !-.]`).
