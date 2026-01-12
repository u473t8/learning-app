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

### Threading Form Placement

Prefer keeping the full pipeline inside the threading macro.
This rule applies to `->`, `->>`, `some->`, and `some->>`.

Bad
```clojure
(fn-3 (-> value fn-1 fn-2))
```

Good
```clojure
(-> value fn-1 fn-2 fn-3)
```

### Top-level Spacing

Separate top-level forms with **EXACTLY** two empty lines. This makes form visually distinguishable.

Bad
```clojure
(defn fn-1 [] ,,,)

(defn fn-2 [] ,,,)
```

Good
```clojure
(defn fn-1 [] ,,,)


(defn fn-2 [] ,,,)
```


### Map Key Sorting

Map keys **SHOULD** be sorted alphabetically, with `:id` and `:_id` keys always first. This provides consistent key ordering across the codebase.

**Example**

Bad
```clojure
{:name "test"
 :id 1
 :value 42
 :active true}
```

Good
```clojure
{:id 1
 :active true
 :name "test"
 :value 42}
```


### Map and Binding Alignment

Map values and let binding values **SHOULD** be aligned when keys/names have similar lengths. Alignment is disabled when the length variance exceeds 15 characters to avoid excessive whitespace.

**Example**

Good — similar key lengths, aligned
```clojure
{:id    1
 :name  "test"
 :value 42}
```

Good — similar binding lengths, aligned
```clojure
(let [x      1
      longer 2
      y      3]
  body)
```

Good — long key, no alignment
```clojure
{:id 1
 :name "test"
 :this-is-a-very-long-key-name "value"}
```


### No Commas in Maps

Do **NOT** use commas to separate map key-value pairs. Commas are whitespace in Clojure and add visual noise. While commas can aid readability in single-line maps, zprint cannot selectively apply them, so we omit them everywhere for consistency.

**Example**

Bad
```clojure
{:id 1, :name "test", :value 42}
```

Good
```clojure
{:id 1 :name "test" :value 42}
```
