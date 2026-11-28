package ph.printshop.pos;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Deterministic pending-only inventory request format. Sale encoding is deliberately
 * unavailable until authoritative catalog, cashier and shift mapping exists. */
final class SyncRequestEncoder {
    private SyncRequestEncoder(){}
    static final class Adjustment {
        final SyncBatchPolicy.Mutation identity;
        final String itemId, note, occurredAt;
        final long hundredths;
        Adjustment(SyncBatchPolicy.Mutation identity,String itemId,long hundredths,String note,String occurredAt){
            if(identity==null||identity.kind!=SyncBatchPolicy.Kind.ADJUST_INVENTORY||
                itemId==null||!itemId.matches("[!-~]{1,200}")||
                hundredths==0||hundredths>100000000000L||hundredths< -100000000000L||
                note==null||note.length()>500||occurredAt==null||occurredAt.length()>40)
                throw invalid();
            // Require valid Unicode; avoid silently replacing malformed surrogate pairs.
            quote(note);
            try{Instant.parse(occurredAt);}catch(java.time.format.DateTimeParseException e){throw invalid();}
            this.identity=identity;this.itemId=itemId;this.hundredths=hundredths;this.note=note;this.occurredAt=occurredAt;
        }
    }
    static final class Prepared {
        final SyncBatchPolicy.Batch batch;
        private final byte[] encoded;
        private Prepared(SyncBatchPolicy.Batch batch,byte[] encoded){this.batch=batch;this.encoded=encoded;}
        byte[] bytes(){return encoded.clone();}
    }
    static Prepared prepare(String server,String owner,List<Adjustment> pending){
        if(pending==null||pending.isEmpty()||pending.size()>SyncBatchPolicy.MAX_BATCH)throw invalid();
        List<Adjustment> rows=new ArrayList<>(pending);
        List<SyncBatchPolicy.Mutation> identities=new ArrayList<>();
        for(Adjustment row:rows){if(row==null)throw invalid();identities.add(row.identity);}
        SyncBatchPolicy.Batch batch=SyncBatchPolicy.prepare(server,owner,identities);
        StringBuilder json=new StringBuilder("{\"mutations\":[");
        for(int i=0;i<rows.size();i++){
            Adjustment row=rows.get(i);
            if(i>0)json.append(',');
            json.append("{\"ownerId\":").append(quote(owner))
                .append(",\"clientMutationId\":").append(quote(row.identity.id))
                .append(",\"type\":\"ADJUST_INVENTORY\",\"payload\":{\"inventoryItemId\":").append(quote(row.itemId))
                .append(",\"movementType\":\"adjustment\",\"quantityDelta\":").append(BigDecimal.valueOf(row.hundredths,2).toPlainString())
                .append(",\"note\":").append(quote(row.note))
                .append(",\"referenceType\":\"manual\",\"occurredAt\":").append(quote(row.occurredAt)).append("}}");
        }
        byte[] bytes=json.append("]}").toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>SyncHttpsTransport.MAX_REQUEST_BYTES)throw invalid();
        return new Prepared(batch,bytes);
    }
    private static String quote(String value){
        StringBuilder out=new StringBuilder("\"");
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(Character.isHighSurrogate(c)){
                if(i+1>=value.length()||!Character.isLowSurrogate(value.charAt(i+1)))throw invalid();
                out.append(c).append(value.charAt(++i));
            }else if(Character.isLowSurrogate(c))throw invalid();
            else if(c=='"'||c=='\\')out.append('\\').append(c);
            else if(c<32){out.append("\\u00").append("0123456789abcdef".charAt(c>>4)).append("0123456789abcdef".charAt(c&15));}
            else out.append(c);
        }
        return out.append('"').toString();
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid pending inventory synchronization request");}
}
