# Native receivable migration contract (not exposed yet)

The current v15 checkout, cash-payment constraint and immutable drawer/report sources assume the full price was paid. Do not implement credit by pretending unpaid cash was received, retroactively editing a cash receipt, or recording an ordinary cash-in as a collection.

## Phase 1 — exact review policy
- FULL_CASH accepts tender >= total; paid=total; change=tender-total; owed=0.
- PARTIAL_CASH requires active customer snapshot and 0<tender<total; paid=tender; change=0; owed=total-paid.
- UNPAID requires active customer, zero tender, paid=0; owed=total.
- A subsequent collection applies oldest open debt first (opened timestamp, stable ID), using immutable source allocation before/after amounts. Aggregate customer balances use BigInteger; each debt, collection and drawer amount is bounded exact centavos.
- A reviewed collection must match freshly calculated allocations, drawer balance and customer balance under the same writer transaction. Changed review is rejected, not silently reallocated. Persist exact request identity and resolve/replay in one transaction; never change an already applied request.
- Credit sale reversal must be a dedicated policy; legacy full cash refund path is prohibited even when later collections have settled the credit. No automatic stock return.

## Phase 2 — persistence migration and gates
Use additive explicitly typed credit source, or deliberately version the old cash checkout schema/constraints/fingerprint (with actual migrations and exact v15 fixture preservation). No fake full-cash receipt. A new applied credit sale must have the same authoritative service/material snapshot and idempotent inventory semantics as cash checkout. Atomically link the authenticated operator's own shift, customer revision, original selling price, initial cash paid and original debt; append stock movement once. For unpaid sales there is no cash effect; partial payment changes cash by initial paid only.

Collections must retain a unique stable pending identity (operator, shift, customer, amount, expected drawer and exact allocation list), reject future/foreign/stale/duplicate debt links and overpayment, and apply all allocations plus one cash effect or roll back all. Pending collections must block conflicting drawer/checkout/shift close activity. Historical customer archive must not erase balances; permit authorized settlement of an existing debt. Exactly-once apply/cancel/replay and recovery after logout/restart are mandatory.

Report gross sales by total of actual applied sale, not amount paid. Report collected cash independently: upfront cash + applied collections - cash refunds. Collection is not new sales revenue. Preserve original cash-only reports, net sale and shift reconciliation. Reconcile refund policy before exposing credit: no generic drawer adjustment or full cash compensation for credit sources. Track original balance and separately applied debt allocations; no unbacked customer balance edits.

Required host SQLite + actual Android tests: migrate populated v15 (including archived customer and named pending/full paid receipts); no invented debt; partial and zero upfront paid; exact stock and cash; duplicate/stale/foreign/overpay; competing shifts; cancellation and crash/rollback at every insertion; reopening exact identity; cumulative allocations and reports; closed shift restrictions; role isolation; installed screen input review and receipt details. Existing 28 installed markers stay mandatory. No production promotion or real financial activity. Current policy is host-tested only; schema/UI remain disabled.
