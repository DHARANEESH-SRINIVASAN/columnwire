# Security policy

## Supported versions

| Version | Supported |
|---|---|
| 0.1.x | Yes |

## Reporting a vulnerability

Please report vulnerabilities privately through GitHub: open the repository's **Security** tab and choose **Report a vulnerability**. Do not open a public issue.

You will get an acknowledgement within 3 days and an assessment within 7. Confirmed issues are fixed in a patch release, credited to the reporter unless they prefer otherwise, and published as a GitHub security advisory.

## How releases are protected

- Maven Central artifacts are signed with the release key `B9F0 51CF 9545 B2AB 7282 9518 908A 18CE E22E 2846`, published on keys.openpgp.org and keyserver.ubuntu.com.
- The npm package is published from GitHub Actions with npm provenance, recorded in the Sigstore transparency log.
- Release jars and the npm tarball carry GitHub build-provenance attestations: `gh attestation verify <file> -R DHARANEESH-SRINIVASAN/columnwire`.
- Every GitHub Action is pinned to a commit SHA, workflows run with read-only tokens by default, and CodeQL, Dependabot and OpenSSF Scorecard run on every change.
