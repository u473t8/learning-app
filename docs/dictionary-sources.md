# Dictionary Sources

## Overview

The autocomplete dictionary requires German lemmas, inflected surface forms, parts of speech, and Russian translations. This document describes the chosen data sources, their licenses, and the strategy for CEFR level coverage.

**Source strategy:**
- **Kaikki/Wiktextract** provides the full German lexicon (lemmas, forms, POS, translations).
- **Goethe-Institut word lists** provide official CEFR labels for A1–B1.
- **Frequency-based ranking** fills the B2–C2 gap where no open word lists exist.

## Primary source: Kaikki/Wiktextract (enwiktionary)

Kaikki provides machine-readable extracts of Wiktionary data produced by the [wiktextract](https://github.com/tatuylonen/wiktextract) tool.

### What it provides

- ~344K distinct German word forms
- Lemmas with part-of-speech tags
- Inflected forms via `forms[]` arrays (declensions, conjugations)
- Russian translations in the `translations[]` array (language code `"ru"`)
- IPA pronunciation data in `sounds[]`

### Data format

JSONL (one JSON object per line), ~933 MB for the full German extract. Key fields per entry:

| Field            | Description                                |
|------------------|--------------------------------------------|
| `word`           | Headword / lemma                           |
| `pos`            | Part of speech (noun, verb, adj, etc.)     |
| `forms[]`        | Inflected surface forms with tags          |
| `senses[]`       | Definitions and semantic data              |
| `translations[]` | Translations with `lang` and `code` fields |
| `sounds[]`       | IPA and audio references                   |

### Download

German extract available from `kaikki.org/dictionary/German/` or the raw data downloads page. Latest dump: 2026-01-01.

### License

- **Content:** CC BY-SA 3.0 (Wiktionary)
- **Tool:** MIT (wiktextract)
- Attribution to Wiktionary contributors is required.

## CEFR level source: Goethe-Institut word lists

The Goethe-Institut publishes official CEFR vocabulary lists for German levels A1, A2, and B1.

### Coverage

| Level     | Approximate stems |
|-----------|-------------------|
| A1        | ~500              |
| A2        | ~1,000            |
| B1        | ~2,400            |
| **Total** | **~3,900**        |

### Machine-readable source

The [sprach-o-mat](https://github.com/technologiestiftung/sprach-o-mat) project (Technologiestiftung Berlin) provides a CSV extraction of the official Goethe PDFs:

- File: `dictionary_a1a2b1_onlystems.csv`
- License: MIT
- Contains stems only (no inflected forms — those come from Kaikki)

## CEFR gap: B2–C2 ranking

No open-source CEFR word lists exist for levels B2, C1, or C2.

### Strategy

- Entries matched to Goethe A1–B1 lists receive an explicit CEFR level **and** a frequency-derived rank.
- All remaining entries receive a frequency-derived rank only, with no CEFR label.
- Higher frequency → lower rank number (i.e., rank 1 = most common).
- Frequency data is derived from the Kaikki extract (sense/usage prevalence as a proxy).

This means B2+ words are implicitly ordered by difficulty via frequency, even without an official CEFR tag.

## Not used: MERLIN-DE

[MERLIN-DE](https://merlin-platform.com/) is a learner essay corpus containing 2,286 CEFR-rated German texts written by language learners. It is **not** a vocabulary word list and cannot serve as a primary dictionary source.

- **License:** CC BY-SA 4.0
- **Potential future use:** Validation source — cross-referencing which words learners at each CEFR level actually produce, to refine frequency-based ranking.

## Source / license matrix

| Source                                        | Type                     | License                                      | Used for                               |
|-----------------------------------------------|--------------------------|----------------------------------------------|----------------------------------------|
| Kaikki / Wiktextract (enwiktionary)           | Lexical database (JSONL) | CC BY-SA 3.0 (content), MIT (tool)           | Lemmas, forms, POS, translations       |
| Goethe-Institut word lists (via sprach-o-mat) | CEFR vocabulary CSV      | MIT (extraction), Goethe-Institut (original) | A1–B1 CEFR labels                      |
| MERLIN-DE                                     | Learner essay corpus     | CC BY-SA 4.0                                 | Not used (potential future validation) |
