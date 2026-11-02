# Security policy

## Supported versions

| Version | Supported |
|---|---|
| 0.1.x | Yes |

## Security contact

Dharaneesh S, maintainer: GitHub [@DHARANEESH-SRINIVASAN](https://github.com/DHARANEESH-SRINIVASAN). Reach the maintainer privately through the reporting link below, which is monitored.

## Reporting a vulnerability

Report vulnerabilities privately at https://github.com/DHARANEESH-SRINIVASAN/columnwire/security/advisories/new (the repository's **Security** tab, **Report a vulnerability**). Please do not open a public issue, pull request or discussion for a suspected vulnerability.

Include the affected version, the record type or request that triggers the problem, and the impact you observed.

## Coordinated disclosure

- Acknowledgement within 3 days and a first assessment within 7 days.
- Confirmed vulnerabilities are fixed in a patch release, normally within 30 days, and disclosed through a GitHub security advisory with a CVE when one applies.
- The reporter is credited in the advisory unless they ask not to be.
- Please keep the details private until the advisory is published, or until 90 days after the report, whichever comes first.

## How releases are protected

- Maven Central artifacts are signed with the release key `B9F0 51CF 9545 B2AB 7282 9518 908A 18CE E22E 2846`, published on keys.openpgp.org and keyserver.ubuntu.com.
- The npm package is published from GitHub Actions with npm provenance, recorded in the Sigstore transparency log.
- Release jars and the npm tarball carry GitHub build-provenance attestations: `gh attestation verify <file> -R DHARANEESH-SRINIVASAN/columnwire`.
- Every GitHub Action is pinned to a commit SHA, workflows run with read-only tokens by default, and CodeQL, Dependabot and OpenSSF Scorecard run on every change.
