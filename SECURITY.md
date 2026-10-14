# Security Policy

## Scope and intended use
SKY ROCKET SHOOTER (SKIARKT) is a **planning and visualization tool only**. It does not control sensors, effectors or
weapons. Its coverage, RF-effectiveness and intercept-altitude results are planning estimates. Their accuracy depends
on DTED quality and simplified propagation models. Do not use them as the only basis for engagement decisions.

## Operational and classified use
- Using SKIARKT operationally, including on classified or otherwise controlled networks, is **solely the
  responsibility of the deploying user and organization**. That responsibility covers accreditation, data handling,
  classification marking and compliance with applicable policy.
- The plugin shares plans as Cursor-on-Target (CoT) over whatever transport ATAK is configured to use (TAK Server, mesh). Check what your network will
  carry before you share plans.
- The classification field in a plan is a label only. It does not enforce access controls.

## Gov / Mil deployment and signing
- Production ATAK builds (Play Store ATAK-CIV, ATAK-MIL / GOV) load only plugins signed through the
  **TAK Product Center** third-party signing pipeline. Request signing at [tak.gov](https://tak.gov).
- The debug keystore workflow in [keystores/README.md](keystores/README.md) is for development only.
  **Never commit keystores or signing credentials** to this repository.
- Build release APKs against the official ATAK SDK obtained from tak.gov, not the bundled API stubs.

## Reporting a vulnerability
Report security issues through [GitHub Issues](https://github.com/widjit/skyrocket-shooter/issues).
**Do not include classified, CUI or operationally sensitive information** in an issue. If a report needs that kind
of detail, open an issue asking for a private contact channel first.

## Supported versions
| Version | Supported |
|---|---|
| 1.0.x (ATAK 4.8) | ✅ |
