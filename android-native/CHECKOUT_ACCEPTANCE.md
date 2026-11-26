# Native cash checkout foundation — preview, not transaction issuance

This is the first critical native checkout milestone. It deliberately performs no sale, payment, tax issuance, stock movement, network call or live data access. No production or physical acceptance claim.

## Implemented contract

- `CheckoutPlan`: immutable single-service cash confirmation from a saved active service, saved pricing revision, saved recipe revision and authoritative material snapshots. Uses integer centavos and exact PricingMath for count/document/area/linear/bulk, not editable quote price. Nonpositive sales, underpayment, hidden stale fields, unsupported quantities, duplicate materials and invalid revisions fail closed. Cash tender capped at maximum supported line total. Partial credit and electronic payments are not supported.
- Saved recipe materials use an explicit billable-unit or produced-item basis where advanced pricing requires it. Per-material required milliunits must be exactly representable in the existing stock ledger (fractional milliunits rejected, never silently rounded). Check pending stock request, exact available stock and catalog/stock revision per material. Cost is separate from selling price, summed before rounding.
- `CatalogStore.prepareCheckout`: one consistent SQLite read transaction reads catalog, pricing, recipe, material rows, ledger balances and pending request state. Revision mismatch requires fresh quote; no mutation or checkout table added. Preview cannot be used as evidence of paid sale.
- `Proposal.sameSnapshot` and `fingerprint()`: compare exact identity, inputs, revisions, selected rate, amount received/change, stock and material snapshots in sorted material order. Version tag CHECKOUT-PLAN-V1, canonical Java DataOutputStream fields (big-endian primitives, writeUTF with modified UTF-8; nullable fields have a presence flag); lowercase SHA-256. Fingerprint is for durable idempotency, not authentication/signature. Stale confirmation must fail, even if replenishment restores same balance.
- `CheckoutPreview.render`: plain-text confirmation preview, marked NOT RECORDED and NOT A TAX DOCUMENT OR PROOF OF PAYMENT. No native screen or print integration yet.

## Actual test evidence

- JDK21 compiler module with `-source 17 -target 17`: 68 new CheckoutPlan tests and 126 preexisting tests = 194 Java checks passed. Compiler warned about system modules not set; real JDK17 build required.
- Existing host SQLite fixture suite: 76 checks across v1–v4 passed. Total host 270 checks.
- CatalogStore and NativeQuoteSmoke Java syntax parsed with JavacTask, 2 files / zero errors; this is not Android type or build verification.
- Mandatory new hosted marker NATIVE_CHECKOUT_PLAN_PASS exercises actual store preparation, document tier, cash/change, exact stock, stale revision fences, pending stock and shortage, replenishment/revision fence and no movement. Retains every prior native marker. No hosted Android acceptance yet.

## Next continuation after parent integrations

1. Confirm #10 full verified current Android and Windows/Linux gates against develop; merge. Retarget #11, validate and merge; then #12 stock recovery, validate and merge. Retarget this one major checkout branch against current develop and reconcile all existing markers/documentation without dropping tests. Verify complete software/Android CI.
2. Introduce additive schema v6 only after schema v5 parent is verified. Durable request payload and versioned fingerprint, non-reusable state and exact retry across restarts. In one write transaction rebuild plan from authoritative data, check expected snapshot, check permission/shift as applicable, write immutable sale, cash payment and corresponding stock ledger movements, all or nothing; calculate change server/store-side. Replays return original saved results; identity reuse with changed payload is rejected. Audit cancellation/void/refund independently; do not automatically discard paid or ambiguous requests.
3. Add actual native confirmation/recovery screens and receipt preview from recorded sale only, clearly non-tax unless independently accepted. Native authentication and shift controls before real cash, material consumption and issuance. No real shop data. Audit exact monetary totals, negative/duplicate/overflow, concurrent stock/checkout, stale plan, rollback injection, database restart and idempotency; actual emulator and full regression. Physical device, printer and BIR separately.
