import { printHtml, safeFilename, type PaperFormat } from '../shared/printDocument.ts';
export function openPrintPreview(reference: string, markup: string, paper: PaperFormat, customWidth: number) {
  const popup = window.open('', '_blank');
  if (!popup) throw new Error('Print preview blocked. Allow popups for this application.');
  try {
    popup.opener = null;
    popup.document.open();
    popup.document.write(printHtml(reference, markup, paper, customWidth, true));
    popup.document.close();
    popup.document.getElementById('print-now')?.addEventListener('click', () => popup.print());
  } catch (err) { popup.close(); throw err; }
}
export function downloadPrintDocument(reference: string, markup: string, paper: PaperFormat, customWidth: number) {
  const url = URL.createObjectURL(new Blob([printHtml(reference, markup, paper, customWidth)], {type:'text/html;charset=utf-8'}));
  const link = document.createElement('a');
  link.href = url; link.download = `${safeFilename(reference)}.html`;
  document.body.appendChild(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}
