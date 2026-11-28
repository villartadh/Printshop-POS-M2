package ph.printshop.pos;
public final class SyncOriginPolicyTest {
    private static int checks;
    private static void check(boolean condition){checks++;if(!condition)throw new AssertionError("origin check "+checks);}
    private static void reject(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("unsafe origin accepted");}
    public static void main(String[] args){
        SyncOriginPolicy p=SyncOriginPolicy.parse("HTTPS://POS.Example:443/");
        check(p.identity().equals("https://pos.example"));
        check(p.batchEndpoint().toString().equals("https://pos.example/api/sync/batch"));
        check(SyncOriginPolicy.parse("https://192.168.1.20:8443").identity().equals("https://192.168.1.20:8443"));
        check(SyncOriginPolicy.parse("https://localhost:9443").identity().equals("https://localhost:9443"));
        for(String raw:new String[]{null,"","http://pos.example","https://user:secret@pos.example","https://pos.example/path",
            "https://pos.example?credential=x","https://pos.example#fragment","https://pos.example:","https://pos.example:0443",
            "https://pos.example:0","https://pos.example:65536","https://pos.example.","https://127.1","https://0177.0.0.1",
            "https://0x7f000001","https://0x7f.0.0.1","https://256.0.0.1","https://[::1]","https://pos%2eexample","https://pos.example\\evil",
            "https://pos.example\n"," https://pos.example","https://pos.example/../","https://pos.example:443//","https://-pos.example"})
            reject(()->SyncOriginPolicy.parse(raw));
        for(int status:new int[]{300,301,302,303,304,307,308,399})reject(()->SyncOriginPolicy.requireNoRedirect(status));
        SyncOriginPolicy.requireNoRedirect(200);check(true);
        SyncOriginPolicy.requireNoRedirect(401);check(true);
        System.out.println("SyncOriginPolicy: "+checks+" checks PASS");
    }
}
