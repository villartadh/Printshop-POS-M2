package ph.printshop.pos;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
public final class SyncRequestEncoderTest {
    static int checks;
    static final String SERVER="server",OWNER="owner",TIME="2026-11-28T05:00:00Z";
    static SyncBatchPolicy.Mutation id(String id){return new SyncBatchPolicy.Mutation(SERVER,OWNER,id,SyncBatchPolicy.Kind.ADJUST_INVENTORY);}
    static SyncRequestEncoder.Adjustment row(String id,long delta,String note){return new SyncRequestEncoder.Adjustment(id(id),"sku",delta,note,TIME);}
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("encoder "+checks);}
    static void reject(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("accepted invalid request "+checks);}
    static SyncRequestEncoder.Prepared prepare(SyncRequestEncoder.Adjustment... rows){return SyncRequestEncoder.prepare(SERVER,OWNER,List.of(rows));}
    public static void main(String[] args){
        var a=row("m1",-125,"a\"\\\n😀");
        var p=prepare(a);
        String expected="{\"mutations\":[{\"ownerId\":\"owner\",\"clientMutationId\":\"m1\",\"type\":\"ADJUST_INVENTORY\",\"payload\":{\"inventoryItemId\":\"sku\",\"movementType\":\"adjustment\",\"quantityDelta\":-1.25,\"note\":\"a\\\"\\\\\\u000a😀\",\"referenceType\":\"manual\",\"occurredAt\":\"2026-11-28T05:00:00Z\"}}]}";
        check(new String(p.bytes(),StandardCharsets.UTF_8).equals(expected));
        check(java.util.Arrays.equals(p.bytes(),prepare(a).bytes()));
        p.bytes()[0]=0;check(p.bytes()[0]=='{');
        check(p.batch.mutations.get(0)==a.identity);
        check(new String(prepare(row("m2",1,"")).bytes(),StandardCharsets.UTF_8).contains("\"quantityDelta\":0.01"));
        reject(()->prepare(a,a));
        reject(()->prepare());
        reject(()->SyncRequestEncoder.prepare("other",OWNER,List.of(a)));
        reject(()->SyncRequestEncoder.prepare(SERVER,"other",List.of(a)));
        reject(()->row("m",0,""));
        reject(()->row("m",Long.MIN_VALUE,""));
        reject(()->row("m",Long.MAX_VALUE,""));
        reject(()->row("m",1,"\uD800"));
        reject(()->row("m",1,"x".repeat(501)));
        reject(()->new SyncRequestEncoder.Adjustment(id("x")," ",1,"",TIME));
        reject(()->new SyncRequestEncoder.Adjustment(id("x"),"sku",1,"","not date"));
        reject(()->new SyncRequestEncoder.Adjustment(new SyncBatchPolicy.Mutation(SERVER,OWNER,"s",SyncBatchPolicy.Kind.CREATE_TRANSACTION),"sku",1,"",TIME));
        List<SyncRequestEncoder.Adjustment> rows=new ArrayList<>();
        for(int n=0;n<50;n++)rows.add(row("id"+n,1,""));
        var full=SyncRequestEncoder.prepare(SERVER,OWNER,rows);
        rows.clear();check(full.batch.mutations.size()==50);
        for(int n=0;n<51;n++)rows.add(row("id"+n,1,""));
        reject(()->SyncRequestEncoder.prepare(SERVER,OWNER,rows));
        System.out.println("SyncRequestEncoder: "+checks+" checks PASS");
    }
}
