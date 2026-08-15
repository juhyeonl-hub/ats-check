# ats-check

Decide whether a job posting is worth applying to, in ten seconds, without sending it anywhere.

![ats-check judging three postings and then a whole directory](docs/demo.gif)

Reading a posting to the end takes two or three minutes. Finding the Finnish
requirement in its last paragraph — after you have already started writing a
cover letter — takes longer. This moves that discovery to the front.

**This is not a résumé optimiser.** It does not rewrite your CV to beat an ATS.
It filters postings so you spend your time on the ones you can actually get.

---

## What it does

Three stages, and it stops as soon as a posting is disqualified.

**Stage 1 — hard filters.** Any one of these ends the analysis:

| Check | Disqualifies when |
|---|---|
| Language | Finnish or Swedish is stated as a **requirement** |
| Experience | Required years exceed yours, beyond your tolerance |
| Degree | A master's or doctorate is **required** |

**Stage 2 — level.** Reads seniority from the job title. Exceeding your ceiling
is a warning, not a rejection — a senior title can still be worth a shot.

**Stage 3 — skill gap.** Lists what the posting asks for that you do not have,
separated into required and nice-to-have.

Verdicts are `APPLY`, `REVIEW`, or `SKIP`, and they double as exit codes.

---

## The hard part

Keyword matching gets this wrong immediately:

```
"Fluent Finnish required"          →  must reject
"Finnish is a plus"                →  must pass
"Finnish is not required"          →  must pass
"Working knowledge of Finnish"     →  genuinely unclear
```

Four signals decide each sentence:

| Signal | Reads |
|---|---|
| **Section** | Which heading it falls under — `Requirements:` or `Nice to have:` |
| **Tone** | `must`, `required`, `is a plus`, `ideally` |
| **Hedge** | `working knowledge`, `basic`, `conversational` |
| **Negation** | `not required`, `no need for` |

When they agree, the sentence is graded confidently. When they conflict, it is
marked ambiguous and the verdict becomes `REVIEW` — with the original sentence
printed so you can judge it yourself.

**Refusing to guess is the point.** A wrong `SKIP` costs you a job you wanted.
An honest `REVIEW` costs you thirty seconds.

Unknown headings reset the section, which is why a Finnish course listed under
`Benefits:` does not read as a language requirement.

---

## Install

Download a binary for your platform from [Releases](../../releases), verify it,
and put it on your `PATH`:

```bash
curl -LO <release-url>/ats-check-linux-amd64
curl -LO <release-url>/checksums.txt
sha256sum -c checksums.txt --ignore-missing
chmod +x ats-check-linux-amd64
sudo mv ats-check-linux-amd64 /usr/local/bin/ats-check
```

One file. No JVM, no runtime, no container. It starts in about 4 ms.

---

## Use

```bash
ats-check init                             # write a starter profile
```

Edit `~/.config/ats-check/profile.yml`:

```yaml
years_experience: 2
years_tolerance: 1          # years above yours that still count
max_seniority: mid          # junior | mid | senior | lead
languages: [english]        # languages you can work in
degree: bachelor            # none | bachelor | master | phd
skills:
  - java
  - spring boot
  - postgresql
```

Then check postings:

```bash
pbpaste | ats-check                        # paste and judge
ats-check --job posting.txt                # from a file
ats-check --job-dir ./jobs                 # a whole directory
ats-check --json                           # for scripts
```

### Keeping postings

A verdict you cannot trace back to the posting is not much use, so postings are
saved with their URL:

```bash
ats-check save --url "https://example.com/jobs/12345"
```

That writes a Markdown file with YAML front matter — readable, editable, and
yours:

```
---
url: https://example.com/jobs/12345
company: Northgate Systems
title: Backend Engineer
saved_at: 2026-08-15T14:32:00+03:00
status: new
---

We are looking for a Backend Engineer...
```

Then judge them together:

```
$ ats-check --job-dir ./jobs

VERDICT  COMPANY     TITLE                REASON                 URL
SKIP     Alten       Java Developer       Finnish required       linkedin.com/jobs/view/111
SKIP     Siili       Backend Architect    At least 7 years       linkedin.com/jobs/view/222
APPLY    Wolt        Backend Engineer     missing: Kotlin, K8s   linkedin.com/jobs/view/333
APPLY    Ravogen     Fullstack Developer  full match             ravogen.fi/careers/12
REVIEW   Solita      Node.js Developer    Finnish - ambiguous    solita.fi/careers/456

5 jobs · 2 apply · 1 review · 2 skip
```

URLs are terminal hyperlinks when you are on a terminal, and plain text when
piped. Open one, or all the ones worth applying to:

```bash
ats-check open wolt
ats-check open --all-apply
```

### Exit codes

`check` reports its verdict:

| Code | Meaning |
|---|---|
| 0 | `APPLY` |
| 1 | `REVIEW` |
| 2 | `SKIP` |
| 64 | Usage error |
| 70 | Internal error |

Batch mode returns the worst verdict it found. Other subcommands use `0` for
success, so a successful `save` is never mistaken for an `APPLY`.

```bash
pbpaste | ats-check --json > verdict.json && echo "worth applying"
```

---

## What it deliberately does not do

| Not this | Why |
|---|---|
| Scrape LinkedIn or Indeed | Against their terms, and it can get **your job-hunting account suspended** ([ADR-001](docs/adr/ADR-001-no-scraping.md)) |
| Call an LLM | Non-deterministic, and it invents evidence. Postings are formulaic enough for rules ([ADR-002](docs/adr/ADR-002-no-llm.md)) |
| Talk to the network at all | Your postings and profile stay on your machine |
| Parse PDF résumés | PDFBox drags AWT into the native image and breaks single-file distribution ([ADR-005](docs/adr/ADR-005-no-pdf-parsing-in-v0.1.md)) |

---

## Build

Needs GraalVM CE 21.

```bash
./gradlew build                # tests
./gradlew :cli:nativeCompile   # binary at cli/build/native/nativeCompile/ats-check
```

`core` holds the rules and has **zero runtime dependencies** — no file access,
no network, not even standard output. `cli` handles everything that touches the
world. The split kept PDF support removable when it turned out not to work
([ADR-004](docs/adr/ADR-004-core-cli-split.md)).

Rule changes are pinned by golden files: a posting and its expected verdict,
discovered automatically from `core/src/test/resources/golden/`. Adding a case
means dropping in two files.

---

## Status

v0.1. Judges postings, keeps them, and judges them in bulk.

Planned for v0.2: fetching postings from public job APIs as a **separate**
subcommand, so a broken API can never take the checker down with it, and
another attempt at résumé parsing.
