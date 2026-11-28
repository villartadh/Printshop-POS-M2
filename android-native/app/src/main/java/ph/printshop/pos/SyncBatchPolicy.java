package ph.printshop.pos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Transport-independent contract for server-authoritative pending mutations.
 * No local training rows or credentials are exported by this class. */
final class SyncBatchPolicy {
    static final int MAX_BATCH=50;
    enum Kind { CREATE_TRANSACTION, ADJUST_INVENTORY }
    enum Resolution { ACKNOWLEDGED, RETAIN_PENDING, RETAIN_CONFLICT }
    private SyncBatchPolicy(){}
    private static String identity(String value){
        if(value==null||value.isEmpty()||value.length()>200)throw new IllegalArgumentException("Invalid sync identity");
        for(int i=0;i<value.length();i++)if(value.charAt(i)<=32||value.charAt(i)>126)
            throw new IllegalArgumentException("Invalid sync identity");
        return value;
    }
    static final class Mutation {
        final String server,owner,id;
        final Kind kind;
        Mutation(String server,String owner,String id,Kind kind){
            this.server=identity(server);this.owner=identity(owner);this.id=identity(id);
            if(kind==null)throw new IllegalArgumentException("Unsupported mutation");
            this.kind=kind;
        }
    }
    static final class Batch {
        final String server,owner;
        final List<Mutation> mutations;
        private Batch(String server,String owner,List<Mutation> mutations){
            this.server=server;this.owner=owner;this.mutations=Collections.unmodifiableList(new ArrayList<>(mutations));
        }
    }
    static Batch prepare(String server,String owner,List<Mutation> pending){
        identity(server);identity(owner);
        if(pending==null||pending.isEmpty()||pending.size()>MAX_BATCH)throw new IllegalArgumentException("Invalid sync batch size");
        Map<String,Mutation> distinct=new LinkedHashMap<>();
        for(Mutation m:pending){
            if(m==null||!server.equals(m.server)||!owner.equals(m.owner)||distinct.put(m.id,m)!=null)
                throw new IllegalArgumentException("Invalid mutation scope or duplicate identity");
        }
        return new Batch(server,owner,pending);
    }
    static final class Ack {
        final String id,status,error;
        final Boolean retryable;
        Ack(String id,String status,String error,Boolean retryable){
            this.id=id;this.status=status;this.error=error;this.retryable=retryable;
        }
    }
    /** Validate the entire response before returning any resolution.
     * Transport must authenticate the bound server and session; persistence must apply
     * results atomically against the exact submitted row versions. */
    static Map<String,Resolution> resolve(Batch submitted,String server,String owner,String syncedAt,List<Ack> response){
        if(submitted==null||!submitted.server.equals(server)||!submitted.owner.equals(owner))
            throw new IllegalArgumentException("Sync session changed");
        try{
            if(syncedAt==null||syncedAt.length()>40)throw new IllegalArgumentException("Invalid acknowledgment time");
            Instant.parse(syncedAt);
        }catch(java.time.format.DateTimeParseException e){throw new IllegalArgumentException("Invalid acknowledgment time",e);}
        if(response==null||response.size()!=submitted.mutations.size())throw new IllegalArgumentException("Incomplete acknowledgment");
        Map<String,Resolution> result=new LinkedHashMap<>();
        for(Mutation m:submitted.mutations)result.put(m.id,null);
        for(Ack ack:response){
            if(ack==null||!result.containsKey(ack.id)||result.get(ack.id)!=null)throw new IllegalArgumentException("Unexpected acknowledgment ID");
            Resolution resolution;
            if("applied".equals(ack.status)||"duplicate".equals(ack.status)){
                if(ack.retryable!=null||ack.error!=null)throw new IllegalArgumentException("Invalid success acknowledgment");
                resolution=Resolution.ACKNOWLEDGED;
            }else if("error".equals(ack.status)){
                // Never persist arbitrary server error text in financial audit/log records.
                if(ack.error!=null&&ack.error.length()>2048)throw new IllegalArgumentException("Invalid acknowledgment error");
                resolution=Boolean.TRUE.equals(ack.retryable)?Resolution.RETAIN_PENDING:Resolution.RETAIN_CONFLICT;
            }else throw new IllegalArgumentException("Unsupported acknowledgment status");
            result.put(ack.id,resolution);
        }
        if(result.containsValue(null))throw new IllegalArgumentException("Incomplete acknowledgment");
        return Collections.unmodifiableMap(result);
    }
}
