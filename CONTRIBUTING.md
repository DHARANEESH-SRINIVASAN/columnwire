# Contributing to Columnwire

Thanks for your interest. Bug reports, benchmark results from other machines, and pull requests are all welcome.

## Reporting bugs and asking for features

Open an issue with the bug or feature template. For bugs, include the Java and Spring Boot versions, the record type that misbehaves, and the `Accept` header the client sent. Security issues go through [SECURITY.md](SECURITY.md), not public issues.

## Building

Requirements: JDK 17 or newer (CI runs 17, 21 and 25), Maven 3.9, Node 22.

```sh
mvn verify
cd js && npm ci && npm test && npm run typecheck -w columnwire
```

## Pull requests

- One change per pull request, with tests. New behaviour needs a test in the module it touches; changes to the wire format need a case in the Java to JavaScript contract test (`js/packages/columnwire/test/contract.test.ts`, fixtures written by `columnwire-benchmark`'s `Fixtures`).
- Keep the existing conventions: Java records and final classes, no new runtime dependencies without discussion, TypeScript strict mode.
- Performance claims need numbers: run the JMH benchmark or `js/bench/parse.mjs` before and after, and put both in the pull request.
- CI must be green on all three JDKs and on Node.

## Releases

Maintainers tag `vX.Y.Z`. The release workflow publishes to Maven Central and npm, attaches signed artifacts and provenance to a GitHub Release, and uses the matching section of [CHANGELOG.md](CHANGELOG.md) as the notes.
