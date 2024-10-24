# Security Policy

[playstrategy.org](https://playstrategy.org) is a free and open source abstract games server, we are non-profit and don't answer to any shareholders, only our users. This is reflected in our discussions and decisions every day.

Like all contributions to PlayStrategy, security reviews and pentesting are appreciated.

If you believe you've found a security issue in our platform, we encourage you to notify us. We welcome working with you to resolve the issue promptly.

## Reporting vulnerabilities

Please report security issues to contact@playstrategy.org

Vulnerabilities are relevant even when they are not directly exploitable, for example XSS mitigated by CSP.

We believe transport encryption will probably be sufficient for all reports. If you insist on using PGP, please clarify the nature of the message in the plain-text subject and encrypt for [multiple recipients](https://lichess.org/.well-known/gpg.asc).

## Scope

This security policy applies to all of [PlayStrategy's source repositories](https://playstrategy.org/source) and infrastructure.

## Rules for testing production infrastructure

- Perform testing only on assets that are in scope.
- Make good faith efforts to avoid privacy violations, destruction of data, interruption or degradation of service, and any annoyance or inconvenience to PlayStrategy users, including spam.
- If a vulnerability provides unintended access to data, limit the amount of data you access to the minimum required for effectively demonstrating a Proof of Concept.
- Do not create more than 5 user accounts.
- All forms of social engineering (e.g., phishing) are strictly prohibited.
- Respect HTTP rate limits, i.e., slow down when you receive HTTP 429.

## Response targets

We aim to meet the following response targets:

- Time to first response: 2 days after report submission
- Time to resolution: 30 days

## Disclosure

All vulnerabilities will be disclosed via GitHub once they have been confirmed and resolved.

## Rewards

We do not currently pay cash bounties.

## Safe Harbor

Any activities conducted in a manner consistent with this policy will be considered authorized conduct (even without prior coordination) and we will not initiate legal action against you. If legal action is initiated by a third party against you in connection with activities conducted under this policy, we will take steps to make it known that your actions were conducted in compliance with this policy.

Thank you for helping keep PlayStrategy users safe!
