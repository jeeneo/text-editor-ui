The JSON files in `grammars/` are TextMate grammars from the [`tm-grammars`](https://github.com/shikijs/textmate-grammars-themes/tree/main/packages/tm-grammars) package.

`racket.json`, `stata.json`, and `wikitext.json` contain small local normalization fixes so their capture and repository entries match the TextMate schema expected by tm4e.

`alias-*.json` files are local: a few grammars in the package include a scope name that the
package itself no longer declares, because that name is the one the original VS Code extension
used (for example `source.vue` for `text.html.vue`, or `source.scss` for `source.css.scss`).
Each alias declares the missing scope name and forwards to the bundled grammar, so the including
grammar keeps its embedded highlighting instead of dropping the rule. They are not languages:
the editor skips them when building the language list and the file-type map.

| alias scope            | forwards to          |
| ---------------------- | -------------------- |
| `source.vue`           | `text.html.vue`      |
| `source.twig`          | `text.html.twig`     |
| `source.scss`          | `source.css.scss`    |
| `source.less`          | `source.css.less`    |
| `source.pug`           | `text.pug`           |
| `source.fs`            | `source.fsharp`      |
| `text.html.php`        | `source.php`         |
| `source.css.sass`      | `source.sass`        |
| `source.postcss`       | `source.css.postcss` |
| `text.html.asciidoc`   | `text.asciidoc`      |
| `source.raku`          | `source.perl.6`      |
| `source.c++`           | `source.cpp`         |
| `text.md`              | `text.html.markdown` |
| `source.gfm`           | `text.html.markdown` |
| `source.js.jquery`     | `source.js`          |
| `source.dosbatch`      | `source.batchfile`   |
| `source.vbnet`         | `source.asp.vb.net`  |
| `source.asm`           | `source.asm.x86_64`  |
| `source.x86`           | `source.asm.x86_64`  |
| `source.x86_64`        | `source.asm.x86_64`  |
| `source.lisp`          | `source.emacs.lisp`  |
| `text.json`            | `source.json`        |
| `text.xml.svg`         | `text.xml`           |
| `source.lean4.markdown` | `text.html.markdown` |
| `text.html.javadoc`    | `text.html.basic`    |
| `text.plain`           | nothing (no patterns, so plain text stays plain) |

The remaining grammars that the package references but that exist in no bundled grammar
(`source.js.regexp`, `source.yang`, `source.agda`, `source.dts`, `source.kconfig`, `text.slim`,
`inline.es6-htmlx`, `source.arm`, `source.postscript`, `source.asy`) have no alias: forwarding
them would highlight the construct as the wrong language, and
`source.cpp.embedded.latex`, `text.tex.markdown_latex_combined`, and `text.elixir` are included
by the grammar that defines them, so an alias would make that grammar include itself.

For those, the dead references are removed instead: an `include` that points at a scope no
bundled grammar provides, at a `#rule` its own grammar does not define, or at a
`scope#rule` its target grammar does not define, can never resolve — tm4e logs
`CANNOT find grammar/rule for scopeName [...]` and drops the enclosing rule. Those includes are
deleted from the affected files (60 of them differ from the package for this reason), so the
construct falls back to the surrounding grammar instead of being dropped at load. The only ones
left are two nested-repository references in `objective-cpp.json` (`#probably_a_parameter`,
`#function-innards`) that would need the grammar itself to be restructured.
