# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- OpenSSF Scorecard, CodeQL and Dependabot.
- GitHub build-provenance attestations and a GitHub Release with signed artifacts on every tag.

## [0.1.0] - 2026-11-02

First release.

### Added
- `columnwire-core`: encodes Java records into Apache Arrow IPC streams in batches, straight from a `List`, `Stream`, `Iterable` or `Iterator`. Enums become dictionaries, Jackson `@JsonProperty` and `@JsonIgnore` are honoured, and a startup check names the missing `--add-opens` flag.
- `columnwire-spring-boot-starter`: a Spring MVC message converter that answers only when the client asks for `application/vnd.apache.arrow.stream`, keeps JSON for every other client, sends `Vary: Accept` on both formats and answers `406` for bodies Arrow cannot represent. Settings `columnwire.enabled` and `columnwire.batch-size`.
- npm package `columnwire`: `fetchArrow` streams record batches with a JSON fallback; `columnwire/react` adds `useArrow` and a virtualized, sortable `ArrowGrid` that reads columns without building row objects.
- JMH and V8 benchmarks, a Java to JavaScript contract test, a Spring Boot example and a live demo.

[Unreleased]: https://github.com/DHARANEESH-SRINIVASAN/columnwire/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/DHARANEESH-SRINIVASAN/columnwire/releases/tag/v0.1.0
