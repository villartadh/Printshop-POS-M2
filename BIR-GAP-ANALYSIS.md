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

## Detailed technical review (2026-11-26)
Full [RMO 24-2023](https://bir-cdn.bir.gov.ph/local/pdf/RMO%20No.%2024-2023.pdf), sections II and IV(5), and [RR 7-2024](https://bir-cdn.bir.gov.ph/BIR/pdf/RR%20No.%207-%202024.pdf), sections 3 and 6, reviewed. [RR 6-2026 official BIR announcement](https://bir-cdn.bir.gov.ph/BIR/pdf/PR114OCT1026.pdf) identifies separate presentation of creditable withholding tax when applicable; [2026 circular index](https://www.bir.gov.ph/2026-Revenue-Memorandum-Circulars?label=2026&type=PAGE) lists RMC 112-2026 transitional guidance. Verify full current issuances and exact field/format applicability with the RDO. This is a technical crosswalk, not a signed Annex F checklist; individual annex templates have not all been reviewed.

| RMO 24-2023 IV(5) | Engineering status / gap |
| --- | --- |
| a | Accumulated sales capacity and reset counter: no compliant fiscal counter; design independently of pilot references |
| b | Sales record protection: transaction identity and reversal audit exist; demo controls and restore capability incompatible with a claim of tamper-free fiscal production; isolate training deployment, design controlled recovery |
| c | Activity records: partial audit coverage, no comprehensive before/after or remote access audit |
| d | Persistent records: verified backup and recovery; regulated retention/access still requires policy and approval |
| e | Daily electronic journal: not implemented; requires preserved issued document and adjustment/report records, not mutable reconstruction |
| f / D-1 D-2 | Cashier/day readings: pilot shifts and date reports only; no immutable day close, Z-counter, closed-day rejection or fiscal reprint |
| g / E-1 to E-5 | Backend reports: missing official tax categories, special discount books and manual invoice linkage |
| h | Sequential fiscal numbers: current internal references are not approved running invoice numbers |
| i | Reprint: pilot marks copies; original issued content and timestamped authorized reprint evidence still required |
| j | EIS submission: not implemented and applicability unconfirmed |
| k | Validation seal: no approved EIS verification mechanism |
| l | Retention: backups do not establish statutory retention or registered-site access |

Added read-only non-fiscal ledger review for verified current-schema backups. It checks recorded sales, payment/collection links, reversals and customer balance arithmetic; outputs IDs and issue codes, not payment details or customer names. SHA-256 identifies the input snapshot; this does not establish authenticity, tamper-proofing, or a fiscal journal. Demo data are outside the review scope; shift, inventory, historic issuance and tax totals require separate gates. Never repair records automatically from findings.
