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
    static int write(ReceiptDocument doc,boolean copy,ReceiptDocument.Geometry geometry,OutputStream output)throws IOException{
        if(doc==null||geometry==null||output==null)throw new IllegalArgumentException("Receipt and output required");
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
        PdfDocument pdf=new PdfDocument();
        try{
            int number=0;
            for(List<String> rows:pages){
                PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(width,height,++number).create());
                Canvas canvas=page.getCanvas();
                float baseline=margin-metrics.ascent;
                for(String row:rows){canvas.drawText(row,margin,baseline,paint);baseline+=step;}
                pdf.finishPage(page);
            }
            pdf.writeTo(output);
        }finally{pdf.close();}
        return pages.size();
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
