# Clojure Code Style

## Basic Rules

We mostly follow the [Clojure Style Guide](https://guide.clojure.style/) with the additions listed below.

## Additional Rules

### Namespace Aliases

Every required namespace **MUST** have an alias. **DO NOT** use `:refer`. Use the last segment of the required namespace as the alias.
**EXCEPTION**: When the last segment of the namespace is `core`, use the segment before it as the alias.
**EXCEPTION**: use standard aliases from basic rules.

**Example**

Bad
```clojure
(ns some-ns
 (:require
  [my-project.utils :refer [ensure-coll]]))
```

Good
```clojure
(ns some-ns
 (:require
  [my-project.utils :as utils]]))
```

Good
```clojure
(ns some-ns
 (:require
  [my-project.db.core :as db]]))
```


### Placement of function arguments

Function arguments **MUST** be placed on the next line after the function name. This simplifies git diffs when you decide to add a docstring to the function.

**Example**

Bad
```clojure
(defn some-function [x]
 (inc x))
```

Good
```clojure
(defn some-function
 [x]
 (inc x))
```


### Function Names

Names of pure functions **SHOULD** describe the produced value (`learning-progress`, `current-challenge`, etc.). This clarifies intent and keeps naming precise.
It helps avoid vague names like `process-data` or `prepare-data`, which usually signal unclear understanding of the function's purpose.

**Example**

Bad
```clojure
(defn calcalate-learning-progress
  [word-reviews]
  ;; function body
  )
```

Bad
```clojure
(defn process-word-reviews
  [word-reviews]
  ;; function body
  )
```

Good
```clojure
(defn learning-progress
  [word-reviews]
  ;; function body
  )
```


NOTE: use namespace keywords in maps that represents meaningful entities

Good
```clojure
;; word from vocabulary
{:id    123
 :value "der Hund"}
```

Good
```clojure
;; word from vocabulary
{:word/id    123
 :word/value "der Hund"}
```
