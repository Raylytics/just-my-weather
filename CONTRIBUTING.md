# Contributing to Just My Weather

Thanks for looking under the hood. This project treats legibility as a feature:
the goal is that adding a data point, an alert subject, or a theme is obvious
rather than surgery. If something was hard to change, that's a bug in the code's
clarity — say so.

## Setup

Requires **JDK 17+** and the **Android SDK**. Point Gradle at your SDK:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # adjust path
```

No global Gradle needed — use the wrapper (`./gradlew`).

## The quality gate

Run these before opening a PR; the pre-commit hook runs the first two on any
Kotlin/resource change:

```bash
./gradlew :app:testDebugUnitTest    # JVM unit tests (JUnit 5)
./gradlew :app:ktlintCheck          # style gate
./gradlew :app:ktlintFormat         # auto-fix most style issues
./gradlew :app:assembleDebug        # build the debug APK
scripts/hooks/install.sh            # install the project gate (test + ktlint)
pre-commit install --hook-type pre-commit --hook-type commit-msg   # once per clone
```

The second line installs the standard hooks (`.pre-commit-config.yaml`:
ktlint, gitleaks, whitespace, and the commit-message checks); CI's
`pre-commit` job runs the same set. `--no-verify` is never used: if a gate is
wrong, fix the gate.

## Branches, commits and pull requests

Branch from `main` as `type/short-kebab`. Commits follow
[Conventional Commits 1.0](https://www.conventionalcommits.org/en/v1.0.0/):
`type(scope): subject`, subject at most 72 characters, imperative, then a body
that says why. Types: `feat`, `fix`, `perf`, `refactor`, `style`, `docs`,
`test`, `build`, `ci`, `chore`, `revert`. No AI bylines; the commit-msg hook
refuses them. Every change lands through a pull request, squash-merged with
CI green; the PR title becomes the squash subject, so it must be a
Conventional Commit subject too. This repo follows the Raylytics Version
Control Standard (`STANDARD.md` in the `raylytics` repo); the `Version
control` section of [CLAUDE.md](CLAUDE.md) is the short form.

## Conventions

- **Kotlin + Compose only**, Material 3. ktlint enforces style (`.editorconfig`:
  120-column lines, required trailing commas, `@Composable`s may be PascalCase).
  File-level doc comments use `/* */`, not `/** */`.
- **Pure logic stays pure.** Conversions, the alert evaluator, codecs, and the
  transition/quiet-hours logic take data in and return data out — no I/O, no
  clock reads, no Android types — so they unit-test on the JVM. Push side effects
  to the edges (transport, repositories, the worker, view models).
- **One seam per concern.** Swapping the weather source or adding a cache happens
  only in `WeatherRepository`; the NWS wire DTOs never escape `data/nws`.
- **Comment the _why_, not the _what_.** A well-named simple piece beats a clever
  abstraction.
- **Tests:** JUnit 5 (`useJUnitPlatform`), Mockito-Kotlin, `runTest` for
  coroutines. Unit tests mirror the main package under `app/src/test/`. New pure
  logic should come with tests; new persisted shapes should round-trip and
  degrade gracefully on bad data.

A known Kotlin trap: inside a property accessor, a bare `field` is the
backing-field keyword, not a constructor property of the same name. Write
`this.field` when you mean the property (see `FieldSetting.label` in
`view/ViewConfig.kt`).

## Where things live

See **[docs/architecture.md](docs/architecture.md)** for the package map and
data flow, and **[docs/extending.md](docs/extending.md)** for step-by-step
recipes (add a data point, an alert subject, a theme accent, a forecast window).

## Pull requests

- Keep PRs focused; describe the _why_.
- Make sure the gate is green and the change is legible to someone seeing it
  cold.
- By contributing you agree your contribution is licensed under the project's
  [Apache 2.0 License](LICENSE).
