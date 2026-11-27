package ph.printshop.pos;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Native system print handoff for an explicitly authorized immutable synthetic receipt.
 * Android owns cancellation and spooler status; no money or stock changes here.
 */
final class ReceiptPrintAdapter extends PrintDocumentAdapter {
    private final ReceiptDocument document;
    private ReceiptPdf.Layout layout;
    ReceiptPrintAdapter(ReceiptDocument document){this.document=document;}
    @Override public void onLayout(PrintAttributes oldAttributes,PrintAttributes attributes,CancellationSignal cancellation,LayoutResultCallback callback,Bundle extras){
        if(cancellation.isCanceled()){callback.onLayoutCancelled();return;}
        layout=null;
        try{
            PrintAttributes.MediaSize size=attributes.getMediaSize();
            PrintAttributes.Margins margins=attributes.getMinMargins();
            if(size==null)throw new IllegalArgumentException("Choose paper size");
            double width=size.getWidthMils()*25.4/1000,height=size.getHeightMils()*25.4/1000;
            if(width<30||width>500||height<30||height>1000)throw new IllegalArgumentException("Unsupported media dimensions");
            double margin=4;
            if(margins!=null)margin=Math.max(margin,Math.max(Math.max(margins.getLeftMils(),margins.getRightMils()),Math.max(margins.getTopMils(),margins.getBottomMils()))*25.4/1000);
            layout=ReceiptPdf.layout(document,true,new ReceiptDocument.Geometry(width,height,margin));
            if(cancellation.isCanceled()){layout=null;callback.onLayoutCancelled();return;}
            callback.onLayoutFinished(new PrintDocumentInfo.Builder("training-receipt.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(layout.pages.size()).build(),!attributes.equals(oldAttributes));
        }catch(RuntimeException ex){layout=null;callback.onLayoutFailed("Unsupported paper or printer margins");}
    }
    @Override public void onWrite(PageRange[] ranges,ParcelFileDescriptor destination,CancellationSignal cancellation,WriteResultCallback callback){
        if(cancellation.isCanceled()){callback.onWriteCancelled();return;}
        ReceiptPdf.Layout current=layout;
        if(current==null){callback.onWriteFailed("Receipt layout unavailable");return;}
        List<PageRange> written=new ArrayList<>();
        boolean[] selected=new boolean[current.pages.size()];
        for(int i=0;i<selected.length;i++)for(PageRange range:ranges)if(range.contains(i)){selected[i]=true;break;}
        for(int i=0;i<selected.length;i++)if(selected[i]){
            int start=i;while(i+1<selected.length&&selected[i+1])i++;
            written.add(new PageRange(start,i));
        }
        if(written.isEmpty()){callback.onWriteFailed("No requested receipt pages");return;}
        try(FileOutputStream out=new FileOutputStream(destination.getFileDescriptor())){
            ReceiptPdf.writePages(current,out,i->selected[i]);
            if(cancellation.isCanceled()){callback.onWriteCancelled();return;}
            callback.onWriteFinished(written.toArray(new PageRange[0]));
        }catch(IOException|RuntimeException ex){if(cancellation.isCanceled())callback.onWriteCancelled();else callback.onWriteFailed("Unable to prepare receipt PDF");}
    }
}
