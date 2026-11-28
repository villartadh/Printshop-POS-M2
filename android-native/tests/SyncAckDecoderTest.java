package ph.printshop.pos;
import java.nio.charset.StandardCharsets;
import java.util.List;
public final class SyncAckDecoderTest {
    private static int checks;
    static final String SERVER="synthetic-server",OWNER="synthetic-owner";
    static final SyncBatchPolicy.Batch BATCH=SyncBatchPolicy.prepare(SERVER,OWNER,List.of(new SyncBatchPolicy.Mutation(SERVER,OWNER,"abc",SyncBatchPolicy.Kind.CREATE_TRANSACTION)));
    static String envelope(String row){return "{\"results\":["+row+"],\"syncedAt\":\"2026-11-28T05:00:00.000Z\"}";}
    static String row(String extra){return "{\"clientMutationId\":\"abc\",\"status\":\"applied\""+extra+"}";}
    static java.util.Map<String,SyncBatchPolicy.Resolution> decode(String json){return SyncAckDecoder.decode(json.getBytes(StandardCharsets.UTF_8),BATCH,SERVER,OWNER);}
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError("decoder check "+checks);}
    static void reject(Runnable action){checks++;try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("invalid acknowledgment accepted "+checks);}
    public static void main(String[] args){
        String valid=envelope(row(""));
        check(decode(valid).get("abc")==SyncBatchPolicy.Resolution.ACKNOWLEDGED);
        check(decode(" \n"+valid+"\t").get("abc")==SyncBatchPolicy.Resolution.ACKNOWLEDGED);
        check(decode(envelope("{\"clientMutationId\":\"a\\u0062c\",\"status\":\"duplicate\"}")).get("abc")==SyncBatchPolicy.Resolution.ACKNOWLEDGED);
        check(decode(envelope("{\"clientMutationId\":\"abc\",\"status\":\"error\",\"retryable\":true,\"error\":\"busy\"}")).get("abc")==SyncBatchPolicy.Resolution.RETAIN_PENDING);
        check(decode(envelope("{\"clientMutationId\":\"abc\",\"status\":\"error\",\"retryable\":false,\"error\":\"\\uD83D\\uDE00\"}")).get("abc")==SyncBatchPolicy.Resolution.RETAIN_CONFLICT);
        for(String bad:new String[]{"",valid+"{}",valid.replace("\"results\"","\"unknown\""),valid.replace("\"syncedAt\"","\"results\""),
            envelope(row(",\"status\":\"duplicate\"")),envelope(row(",\"retryable\":false")),envelope(row(",\"new\":1")),
            envelope(row("").replace("\"abc\"","null")),envelope(row("").replace("\"abc\"","\"other\"")),
            envelope(row("").replace("\"applied\"","true")),envelope(row("").replace("\"applied\"","\"apl\\uD800\"")),
            envelope(row("").replace("\"applied\"","\"apl\\u+111\"")),envelope(row("")+","),envelope(row("")+","+row("")),
            envelope(row("").replace("abc","a\nbc")),envelope("{}"),envelope(row("").replace("abc","a".repeat(2049))),
            valid.replace("2026-11-28T05:00:00.000Z","invalid"),valid.replace("results","Results")})
            reject(()->decode(bad));
        reject(()->SyncAckDecoder.decode(new byte[]{(byte)0xc0,(byte)0xaf},BATCH,SERVER,OWNER));
        reject(()->SyncAckDecoder.decode(new byte[SyncAckDecoder.MAX_BYTES+1],BATCH,SERVER,OWNER));
        reject(()->SyncAckDecoder.decode(null,BATCH,SERVER,OWNER));
        reject(()->SyncAckDecoder.decode(valid.getBytes(StandardCharsets.UTF_8),BATCH,SERVER,"another-owner"));
        System.out.println("SyncAckDecoder: "+checks+" checks PASS");
    }
}
