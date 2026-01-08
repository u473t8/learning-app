---
name: format-clj
description: Format Clojure, ClojureScript, and EDN files. Use this skill whenever formatting .clj, .cljs, .cljc, or .edn files. Never manually format Clojure code - always use this skill.
---

# Format Clojure Code

Format Clojure/ClojureScript/EDN files using zprint with project-local config.

## Usage

Format specific files:
```
/format-clj src/client/application.cljs
```

Format multiple files:
```
/format-clj src/client/*.cljs
```

## Command

```bash
zprint -fw <files>
```

Flags:
- `-f` (formatted): only report files that were changed
- `-w` (write): write changes back to file

## Project Config

Project formatting rules in `.zprint.edn`:
- `:style [:community :respect-nl]` - Community style, preserves intentional newlines
- `:parse {:interpose "\n\n\n"}` - Two blank lines between top-level forms
- `:width 80` - 80 character line width

## When to Use

Run after writing or modifying Clojure code to ensure consistent formatting.
