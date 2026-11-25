export type PaperFormat = '58mm' | '80mm' | 'A4' | 'Letter' | 'Custom';
export function paperLayout(format: PaperFormat, customMm = 80) {
  if (format === 'A4') return {width: 190, page: 'A4', margin: 10};
  if (format === 'Letter') return {width: 195.9, page: 'letter', margin: 10};
  const width = format === '58mm' ? 58 : format === '80mm' ? 80 : Math.min(300, Math.max(40, Number.isFinite(customMm) ? customMm : 80));
  return {width: width - 8, page: 'auto', margin: 4};
}
export function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));
}
export function safeFilename(reference: string) {
  return reference.replace(/[^a-zA-Z0-9_-]/g, '_').slice(0,100) || 'document';
}
export function documentKind(type: string | undefined, hasJob: boolean) {
  return type === 'quotation' || type === 'held' ? 'quotation' : type === 'sale' ? 'sales_receipt' : hasJob ? 'job_order_slip' : 'payment_acknowledgment';
}
// bodyMarkup must be rendered application DOM, not raw user HTML.
export function printHtml(reference: string, bodyMarkup: string, format: PaperFormat, customMm: number, controls = false) {
  const p = paperLayout(format, customMm);
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline';"><title>${escapeHtml(reference)}</title><style>
@page{size:${p.page};margin:${p.margin}mm} *{box-sizing:border-box} body{margin:0;background:white;color:black;font:11px/1.45 Arial,sans-serif} main{width:${p.width}mm;max-width:100%;margin:auto;overflow-wrap:anywhere} img{max-width:100%;max-height:18mm;object-fit:contain} .flex{display:flex;gap:4px}.justify-between{justify-content:space-between}.flex>span:last-child{text-align:right}.flex>span{min-width:0}.flex>span:first-child{flex:1}.text-center{text-align:center}.font-bold,.font-semibold{font-weight:bold}.text-sm{font-size:13px}.border-b{border-bottom:1px dashed #777;padding-bottom:5px;margin-bottom:5px}.border{border:1px solid #777}.uppercase{text-transform:uppercase}.mx-auto{display:block;margin:auto}.space-y-2>div+div,.space-y-1>div+div{margin-top:3px}.p-1{padding:3px}.mt-2{margin-top:5px}.pt-1{padding-top:3px} button{padding:10px;margin:8px} @media print{aside{display:none} main{margin:0;max-width:100%} .flex{break-inside:avoid}}
</style></head><body>${controls ? '<aside><button id="print-now">Print / Save as PDF</button> Select the matching paper in your printer settings. Printing or cancellation cannot be verified by this app.</aside>' : ''}<main>${bodyMarkup}</main></body></html>`;
}
