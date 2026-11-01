# Security Policy

## Supported Versions

Only the latest active major/minor release line receives security updates and patches.

| Version | Build Code | Supported          |
| ------- | ---------- | ------------------ |
| 2.1.x   | >= 18      | :white_check_mark: |
| < 2.1   | < 18       | :x:                |

---

## Reporting a Vulnerability

We take the security of this Android application and client communication seriously. If you believe you have discovered a vulnerability, **please do not disclose it publicly or open a public issue.**

### Recommended: Private Vulnerability Reporting
Please report vulnerabilities privately through GitHub's built-in **Private Vulnerability Reporting**:
👉 [Open a Private Security Advisory Report](https://github.com/dinesh-tadi/my-pi-mobile/security/advisories/new)

When submitting a report, please include:
1. **Description:** Clear explanation of the potential vulnerability and its impact (e.g. token storage, TLS verification, intent injection).
2. **Steps to Reproduce:** Exact steps or proof of concept (PoC) to reproduce the issue.
3. **Affected Version / Device:** Android OS version, device architecture, and app build code.
4. **Proposed Mitigation:** Any proposed patch or mitigation.

---

## Response & Disclosure Process

- **Acknowledgment:** We will acknowledge receipt of your vulnerability report within **48 hours**.
- **Investigation & Assessment:** We will validate the issue and determine severity and scope.
- **Remediation & Testing:** A fix will be developed in a private security fork, verified against build checks, and distributed via OTA update.
- **Coordinated Disclosure:** Once the fix is published and verified, a security advisory will be published crediting the reporter.
