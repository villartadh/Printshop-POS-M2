package ph.printshop.pos;
import java.util.*;
public final class SyncBatchPolicyTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("sync policy check "+checks);}
    private static void reject(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("invalid sync policy accepted "+checks);}
    static SyncBatchPolicy.Mutation m(String server,String owner,String id){return new SyncBatchPolicy.Mutation(server,owner,id,SyncBatchPolicy.Kind.CREATE_TRANSACTION);}
    static SyncBatchPolicy.Ack a(String id,String status,Boolean retry){return new SyncBatchPolicy.Ack(id,status,null,retry);}
    public static void main(String[] args){
        String server="synthetic-server",owner="synthetic-owner",time="2026-11-28T05:00:00.000Z";
        List<SyncBatchPolicy.Mutation> rows=new ArrayList<>(List.of(m(server,owner,"a"),m(server,owner,"b"),m(server,owner,"c"),m(server,owner,"d")));
        SyncBatchPolicy.Batch batch=SyncBatchPolicy.prepare(server,owner,rows);
        rows.clear();check(batch.mutations.size()==4);
        List<SyncBatchPolicy.Ack> valid=List.of(a("d","error",false),a("c","error",true),a("b","duplicate",null),a("a","applied",null));
        Map<String,SyncBatchPolicy.Resolution> result=SyncBatchPolicy.resolve(batch,server,owner,time,valid);
        check(result.get("a")==SyncBatchPolicy.Resolution.ACKNOWLEDGED);
        check(result.get("b")==SyncBatchPolicy.Resolution.ACKNOWLEDGED);
        check(result.get("c")==SyncBatchPolicy.Resolution.RETAIN_PENDING);
        check(result.get("d")==SyncBatchPolicy.Resolution.RETAIN_CONFLICT);
        reject(()->SyncBatchPolicy.prepare(server,owner,List.of()));
        reject(()->SyncBatchPolicy.prepare(server,owner,List.of(m(server,"other","x"))));
        reject(()->SyncBatchPolicy.prepare(server,owner,List.of(m("other",owner,"x"))));
        reject(()->SyncBatchPolicy.prepare(server,owner,List.of(m(server,owner,"x"),m(server,owner,"x"))));
        reject(()->m(server,owner," "));
        reject(()->m(server,owner,"x".repeat(201)));
        List<SyncBatchPolicy.Mutation> large=new ArrayList<>();
        for(int i=0;i<50;i++)large.add(m(server,owner,"id-"+i));
        check(SyncBatchPolicy.prepare(server,owner,large).mutations.size()==50);
        large.add(m(server,owner,"extra"));reject(()->SyncBatchPolicy.prepare(server,owner,large));
        reject(()->SyncBatchPolicy.resolve(batch,"other",owner,time,valid));
        reject(()->SyncBatchPolicy.resolve(batch,server,"other",time,valid));
        reject(()->SyncBatchPolicy.resolve(batch,server,owner,"not-a-time",valid));
        reject(()->SyncBatchPolicy.resolve(batch,server,owner,time,valid.subList(0,3)));
        reject(()->SyncBatchPolicy.resolve(batch,server,owner,time,List.of(valid.get(0),valid.get(1),valid.get(2),valid.get(2))));
        reject(()->SyncBatchPolicy.resolve(batch,server,owner,time,List.of(valid.get(0),valid.get(1),valid.get(2),a("unknown","applied",null))));
        reject(()->SyncBatchPolicy.resolve(batch,server,owner,time,List.of(valid.get(0),valid.get(1),valid.get(2),a("a","pending",null))));
        reject(()->SyncBatchPolicy.resolve(batch,server,owner,time,List.of(valid.get(0),valid.get(1),valid.get(2),a("a","applied",false))));
        reject(()->SyncBatchPolicy.resolve(batch,server,owner,time,List.of(valid.get(0),valid.get(1),valid.get(2),new SyncBatchPolicy.Ack("a","duplicate","unexpected",null))));
        check(SyncBatchPolicy.resolve(batch,server,owner,time,List.of(valid.get(0),valid.get(1),valid.get(2),a("a","error",null))).get("a")==SyncBatchPolicy.Resolution.RETAIN_CONFLICT);
        check(SyncBatchPolicy.prepare(server,owner,List.of(new SyncBatchPolicy.Mutation(server,owner,"stock",SyncBatchPolicy.Kind.ADJUST_INVENTORY))).mutations.size()==1);
        System.out.println("SyncBatchPolicy: "+checks+" checks PASS");
    }
}
