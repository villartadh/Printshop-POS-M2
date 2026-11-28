# Printshop POS M2 touch-first interface direction
Adopt the following implementation standards across desktop and native Android while retaining existing financial contracts. This is a design direction, not a claim of device acceptance.

- Use consistent light surfaces, high-contrast navy text, controlled teal primary action, semantic warning/error/success, 8dp spacing rhythm and accessible labels. Do not use color as the only status signal.
- Cashier home: prioritized Sales, Quotations, Jobs, Customers, then secondary administration. Keep lock and current shift visible. Large controls with minimum 48dp Android touch targets; avoid endless dashboard button lists.
- Wide desktop/tablet: service tiles and fixed order review side by side. Phone: compact service selection and visible cart total with a dedicated checkout step. No unnecessary page scrolling on main cashier screen; support accessible fallback when content/font scale requires it. Never clip controls.
- Job workspace: clear Pending / In production / Quality check / Ready / Completed states, payment badge sourced only from financial journal, explicit transition confirmation and operator audit. Release action disabled with outstanding balance or unresolved compensation; retain server/database guards.
- Preserve stable IDs, keyboard focus, native inputs and protected fixture warnings. Review before committing, keep original request identities, show recovery rather than silently recreating transactions.
- History: bounded pages; previous/next only when actual overflow, no artificial pagination for short results.
- Acceptance: actual rendered screenshots and overflow checks at 390x844, 768x1024, 1280x720, 1920x1080; Android portrait and landscape and large font; distinguish viewport from physical Windows DPI and actual device testing. Android standalone native screens only; do not use WebView. No production or hardware claims based on emulator screenshots.
