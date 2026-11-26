# Philippine BIR readiness gap analysis — 2026-11-26

Scope: Printshop POS v0.9.7, engineering review only. Not a tax opinion, accreditation, Permit to Use, Permit to Issue or official compliance determination. No real taxpayer information was inspected; VAT status, taxpayer classification, RDO, branch registration, transaction types and applicable authorizations are unknown. No registration or submission performed.

## Official sources reviewed

- [RMO 24-2023, official digest](https://bir-cdn.bir.gov.ph/local/pdf/RMO%20No.%2024-2023%20Digest%20FINAL.pdf), June 26, 2023; [full issuance and annex index](https://www.bir.gov.ph/2023-Revenue-Memorandum-Order?label=2023+Revenue+Memorandum+Orders&to=2023-Revenue-Memorandum-Order&type=PAGE).
- [RMC 77-2024](https://bir-cdn.bir.gov.ph/BIR/pdf/RMC%20No.%2077-2024.pdf), July 11, 2024, clarifies RR 7-2024 and RR 11-2024.
- [RR 26-2025, official digest](https://bir-cdn.bir.gov.ph/BIR/pdf/RR%20No.%2026-2025%20Digest.pdf), issued October 16, 2025, amending RR 11-2025.
- [RMC 98-2026, official digest](https://bir-cdn.bir.gov.ph/BIR/pdf/RMC%20No.%2098-2026%20Digest.pdf), September 22, 2026; [2026 issuance index](https://www.bir.gov.ph/2026-Revenue-Memorandum-Circulars?label=2026&type=PAGE), includes RMC 110-2026 regarding electronic invoicing service providers and RMC 112-2026 regarding withholding tax presentation.
- [BIR Citizen's Charter](https://www.bir.gov.ph/citizencharter), [secondary registration](https://www.bir.gov.ph/secondary-registration), [eAccReg help](https://eaccreg.bir.gov.ph/ACCREG/help.html), [eSales](https://www.bir.gov.ph/esales).

## Applicable paths, not a classification decision

RMO 24-2023 covers POS software that generates primary sales documents, including self-developed solutions. Accreditation and registration/PTU are separate from electronic invoice authority. Internal/supplementary-only special purpose systems may still require registration/PTU; the non-tax label is not an exemption. Ask the RDO which path applies before operating commercially.

Under EOPT, invoices are primary evidence for goods and services. RMC 77-2024 describes VAT invoice issuance on every sale and non-VAT issuance at the PHP 500 threshold, buyer request and daily aggregation conditions. Check threshold updates and complete RR 7-2024 field requirements before implementation. Do not hard-code one regime without verified taxpayer classification.

RR 26-2025 and RMC 98-2026 establish December 31, 2026 for specified covered taxpayers; the latter describes the micro-taxpayer exception. POS use alone does not establish that deadline for every shop: separate implementing issuances and other covered activities matter. Electronic invoicing and electronic sales reporting obligations must be assessed separately. A PDF, HTML or generic JSON export is not an approved electronic invoice. Voluntary adoption also needs applicable authority. Confirm current issuances, classification and route with the RDO.

## Engineering gap register

| Control | Current evidence | Gap / required next action |
| --- | --- | --- |
| Accreditation / PTU | Versioned binaries and evidence | External BIR inspection and approval; obtain applicable annex checklist; no invented authority numbers |
| Invoice identity | Internal unique OR-prefixed sale reference and snapshots | Separate immutable approved invoice sequence, registered identity, branch/machine identifiers, controlled issuance; preserve old IDs |
| Invoice data | Generic branding, quantities, prices, discounts, tax and totals | Validate actual required fields against applicable registered invoice type and current full issuances; VAT/non-VAT, exempt/zero-rated, buyer details and special discounts/withholding as applicable |
| Pilot output | Sales slip and invoice draft labeled non-tax; universal fixed notice added | Keep all output non-tax until formal authorization; cannot change via branding |
| Corrections | Atomic reversal audit; original sale retained | Separate authorized corrective document and references for tax records; no rewriting issued invoice |
| Reporting | Date-range finance, shift reconciliation, JSON export | Develop official-format fiscal reports and machine-level reconciliation as applicable; verify requirements against annexes and eSales route |
| Structured e-invoice | Internal JSON, HTML and PDF | Not EIS schema; controlled document delivery, conversion, correction references and certification/authority as applicable; no live transmission |
| Downtime | Durable offline sale queue and replay protection | For regulated use, separate authorized manual invoice contingency and later reference linkage; provisional slip cannot replace authorized manual invoice |
| Record protection | Verified backups, audit rows, transaction identities, access control | Retention rules, export integrity, restore audit, tamper evidence and complete role review; SQLite backup alone does not establish legal record retention |
| Device and branch coverage | Browser/hosted Windows evidence | Register applicable machines/branches, physical acceptance, official approval; no automatic claim of certification |
| Tax configuration | Generic tax settings | Establish actual taxpayer regime and accountant-approved calculation fixtures before making fiscal claims |

## Ordered remediation

1. Keep fixed non-tax marking on every output type, including summary, preview, HTML and PDF. Add real UI regression checks.
2. Complete full RMO 24-2023 annex-by-annex technical checklist and current full invoicing field requirements; record applicability and evidence without fabricating legal identity.
3. Build non-fiscal structured review export and immutable audit/reconciliation controls with synthetic fixtures; do not describe these as approved invoice formats.
4. Prepare separate proposed fiscal document model and tests, with issuance disabled until external approval and verified configuration. Preserve the accepted pilot, existing receipt numbers and records.
5. Obtain owner/accountant/RDO confirmation of taxpayer class, applicable registration, approved invoice format and numbering, branch/MIN, PTU/PTI and reporting obligations; no credentials or submissions through autonomous development.

Disposition: **NOT BIR APPROVED / NON-TAX SOFTWARE PILOT**. Further legal review is necessary; this initial review uses official summaries for several issuances and is not an exhaustive annex-level audit.
