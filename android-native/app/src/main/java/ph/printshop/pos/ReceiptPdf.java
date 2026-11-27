package ph.printshop.pos;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Native PDF renderer. Does not claim successful spooler/physical printing. */
final class ReceiptPdf {
    private ReceiptPdf(){}
    static final class Layout {
        final int width,height;final float margin,step,ascent;
        final Paint paint;final List<List<String>> pages;
        Layout(int width,int height,float margin,float step,float ascent,Paint paint,List<List<String>> pages){
            this.width=width;this.height=height;this.margin=margin;this.step=step;this.ascent=ascent;this.paint=paint;this.pages=pages;
        }
    }
    static Layout layout(ReceiptDocument doc,boolean copy,ReceiptDocument.Geometry geometry){
        if(doc==null||geometry==null)throw new IllegalArgumentException("Receipt and output required");
        int width=(int)Math.round(geometry.widthMm*72/25.4),height=(int)Math.round(geometry.heightMm*72/25.4);
        float margin=(float)Math.ceil(geometry.marginMm*72/25.4);
        float available=width-2*margin;
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);paint.setTypeface(Typeface.MONOSPACE);paint.setTextSize(9f);
        Paint.FontMetrics metrics=paint.getFontMetrics();
        float step=(float)Math.ceil(metrics.descent-metrics.ascent+2);
        int capacity=(int)Math.floor((height-2*margin)/step);
        if(available<=0||capacity<1)throw new IllegalArgumentException("Receipt page too small");
        List<String> lines=new ArrayList<>();
        for(String row:doc.lines(copy))wrap(row,paint,available,lines);
        List<List<String>> pages=ReceiptDocument.paginate(lines,capacity);
        return new Layout(width,height,margin,step,metrics.ascent,paint,pages);
    }
    static int write(ReceiptDocument doc,boolean copy,ReceiptDocument.Geometry geometry,OutputStream output)throws IOException{
        Layout layout=layout(doc,copy,geometry);
        writePages(layout,output,i->true);return layout.pages.size();
    }
    static void writePages(Layout layout,OutputStream output,java.util.function.IntPredicate selected)throws IOException{
        if(output==null||selected==null)throw new IllegalArgumentException("Receipt output required");
        PdfDocument pdf=new PdfDocument();
        try{
            for(int i=0;i<layout.pages.size();i++){
                if(!selected.test(i))continue;
                PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(layout.width,layout.height,i+1).create());
                Canvas canvas=page.getCanvas();float baseline=layout.margin-layout.ascent;
                for(String row:layout.pages.get(i)){canvas.drawText(row,layout.margin,baseline,layout.paint);baseline+=layout.step;}
                pdf.finishPage(page);
            }
            pdf.writeTo(output);
        }finally{pdf.close();}
    }
    private static void wrap(String row,Paint paint,float width,List<String> lines){
        if(row.isEmpty()){lines.add("");return;}
        int start=0;
        while(start<row.length()){
            int end=start,lastSpace=-1;
            while(end<row.length()){
                int next=end+Character.charCount(row.codePointAt(end));
                if(paint.measureText(row,start,next)>width)break;
                if(Character.isWhitespace(row.codePointAt(end)))lastSpace=end;
                end=next;
            }
            if(end==start)throw new IllegalArgumentException("Receipt character exceeds page width");
            if(end<row.length()&&lastSpace>start)end=lastSpace;
            lines.add(row.substring(start,end));
            start=end;
            while(start<row.length()&&Character.isWhitespace(row.codePointAt(start)))start+=Character.charCount(row.codePointAt(start));
        }
    }
}
