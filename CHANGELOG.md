# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.2] - 2026-11-02

### Fixed
- Release workflow: the npm job packed into a `release/` folder that did not exist, so 0.1.1 reached Maven Central but not npm or a GitHub Release. 0.1.2 ships the same code to both registries. CI now rehearses the release file handling on every pull request.

## [0.1.1] - 2026-11-02

### Added
- Property-based tests with fast-check: `fetchArrow` returns exactly what was encoded for random tables split into random chunk sizes, `sortOrder` is always an ordered permutation, and `formatValue` never throws.
- OpenSSF Scorecard, CodeQL and Dependabot, every GitHub Action pinned to a commit SHA, protected `main`.
- GitHub build-provenance attestations and a GitHub Release with signed artifacts, provenance bundles, `LICENSE` and `SHA256SUMS.txt` on every tag.
- OpenSSF Best Practices badge (passing), answers kept in `.bestpractices.json`.
- The Maven jars and source jars now contain `META-INF/LICENSE`.

### Fixed
- `ArrowGrid` sorting: `NaN` values (Java `Double.NaN`) made the comparator inconsistent, which could leave other rows out of order. `NaN` now sorts last with nulls. Found by the property tests.

### Changed
- Built with TypeScript 7.0.2. The published type declarations are identical to the 6.0 output.

## [0.1.0] - 2026-11-02

First release.

### Added
- `columnwire-core`: encodes Java records into Apache Arrow IPC streams in batches, straight from a `List`, `Stream`, `Iterable` or `Iterator`. Enums become dictionaries, Jackson `@JsonProperty` and `@JsonIgnore` are honoured, and a startup check names the missing `--add-opens` flag.
- `columnwire-spring-boot-starter`: a Spring MVC message converter that answers only when the client asks for `application/vnd.apache.arrow.stream`, keeps JSON for every other client, sends `Vary: Accept` on both formats and answers `406` for bodies Arrow cannot represent. Settings `columnwire.enabled` and `columnwire.batch-size`.
- npm package `columnwire`: `fetchArrow` streams record batches with a JSON fallback; `columnwire/react` adds `useArrow` and a virtualized, sortable `ArrowGrid` that reads columns without building row objects.
- JMH and V8 benchmarks, a Java to JavaScript contract test, a Spring Boot example and a live demo.

[Unreleased]: https://github.com/DHARANEESH-SRINIVASAN/columnwire/compare/v0.1.2...HEAD
[0.1.2]: https://github.com/DHARANEESH-SRINIVASAN/columnwire/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/DHARANEESH-SRINIVASAN/columnwire/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/DHARANEESH-SRINIVASAN/columnwire/releases/tag/v0.1.0
