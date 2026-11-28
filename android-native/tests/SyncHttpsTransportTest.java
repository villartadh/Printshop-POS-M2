package ph.printshop.pos;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.*;
public final class SyncHttpsTransportTest {
    static int checks;
    static void check(boolean v){checks++;if(!v)throw new AssertionError("transport check "+checks);}
    interface Attempt {void run() throws Exception;}
    static void reject(Attempt a)throws Exception{checks++;try{a.run();}catch(IOException|IllegalArgumentException expected){return;}throw new AssertionError("accepted invalid transport "+checks);}
    public static void main(String[] args)throws Exception{
        byte[] request="{}".getBytes(StandardCharsets.UTF_8),response="{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        KeyStore ks=KeyStore.getInstance("PKCS12");
        try(InputStream in=new FileInputStream(args[0])){ks.load(in,"synthetic-test".toCharArray());}
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        km.init(ks,"synthetic-test".toCharArray());
        SSLContext serverContext=SSLContext.getInstance("TLS");serverContext.init(km.getKeyManagers(),null,null);
        HttpsServer server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        AtomicInteger mode=new AtomicInteger(),calls=new AtomicInteger(),redirects=new AtomicInteger();
        server.createContext("/redirect",e->{redirects.incrementAndGet();e.close();});
        server.createContext("/api/sync/batch",e->{
            try{
                calls.incrementAndGet();
                if(!"POST".equals(e.getRequestMethod())||!"Bearer synthetic-token".equals(e.getRequestHeaders().getFirst("Authorization"))||
                    !java.util.Arrays.equals(request,e.getRequestBody().readAllBytes())){e.sendResponseHeaders(400,-1);return;}
                int m=mode.get();
                if(m==1){e.getResponseHeaders().set("Location","/redirect");e.sendResponseHeaders(307,-1);return;}
                if(m==2){e.sendResponseHeaders(401,-1);return;}
                e.getResponseHeaders().set("Content-Type",m==3?"text/html":"application/json; charset=utf-8");
                if(m==4)e.getResponseHeaders().set("Content-Encoding","gzip");
                byte[] bytes=m==5?new byte[SyncAckDecoder.MAX_BYTES+1]:response;
                e.sendResponseHeaders(200,m==5?0:bytes.length);e.getResponseBody().write(bytes);
            }finally{e.close();}
        });
        server.start();
        try{
            String port=":"+server.getAddress().getPort();
            SyncOriginPolicy origin=SyncOriginPolicy.parse("https://localhost"+port);
            check(java.util.Arrays.equals(response,SyncHttpsTransport.post(origin,"synthetic-token",request)));
            for(int i=1;i<=5;i++){mode.set(i);reject(()->SyncHttpsTransport.post(origin,"synthetic-token",request));}
            check(calls.get()==6&&redirects.get()==0);
            mode.set(0);reject(()->SyncHttpsTransport.post(SyncOriginPolicy.parse("https://127.0.0.1"+port),"synthetic-token",request));
            reject(()->SyncHttpsTransport.post(origin,"bad\r\nheader",request));
            reject(()->SyncHttpsTransport.post(origin,"synthetic-token",new byte[SyncHttpsTransport.MAX_REQUEST_BYTES+1]));
            check(calls.get()==6);
            check(SyncHttpsTransport.readBounded(new ByteArrayInputStream(response),response.length).length==response.length);
            reject(()->SyncHttpsTransport.readBounded(new ByteArrayInputStream(response),response.length+1));
            reject(()->SyncHttpsTransport.readBounded(new ByteArrayInputStream(new byte[0]),0));
            reject(()->SyncHttpsTransport.readBounded(new InputStream(){public int read(){return 0;}public int read(byte[] b){return 0;}},-1));
        }finally{server.stop(0);}
        System.out.println("SyncHttpsTransport: "+checks+" checks PASS");
    }
}
