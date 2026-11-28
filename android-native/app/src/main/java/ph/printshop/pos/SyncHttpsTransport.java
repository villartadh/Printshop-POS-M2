package ph.printshop.pos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;

/** Internal wire primitive only. Callers must bind the encoded payload and bearer to
 * the same authenticated pairing before use. No application caller or network permission yet. */
final class SyncHttpsTransport {
    static final int MAX_REQUEST_BYTES=512*1024;
    private static final int TIMEOUT_MS=10000;
    private SyncHttpsTransport(){}
    static byte[] post(SyncOriginPolicy origin,String bearer,byte[] encoded) throws IOException {
        if(origin==null||bearer==null||bearer.length()>4096||!bearer.matches("[A-Za-z0-9._~+/-]+={0,2}")||
            encoded==null||encoded.length==0||encoded.length>MAX_REQUEST_BYTES)
            throw new IllegalArgumentException("Invalid synchronization request");
        byte[] request=encoded.clone();
        HttpsURLConnection connection=null;
        try {
            connection=(HttpsURLConnection)origin.batchEndpoint().toURL().openConnection(java.net.Proxy.NO_PROXY);
            // Default platform TLS and hostname verification; never install a custom trust manager.
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(request.length);
            connection.setRequestProperty("Authorization","Bearer "+bearer);
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("Accept-Encoding","identity");
            try(OutputStream out=connection.getOutputStream()){out.write(request);}
            if(connection.getResponseCode()!=200)throw failure();
            String type=connection.getContentType();
            if(type==null||!type.toLowerCase(Locale.ROOT).matches("application/json(?:\\s*;\\s*charset\\s*=\\s*(?:utf-8|\"utf-8\"))?"))throw failure();
            String encoding=connection.getContentEncoding();
            if(encoding!=null&&!"identity".equalsIgnoreCase(encoding))throw failure();
            long length=connection.getContentLengthLong();
            if(length==0||length>SyncAckDecoder.MAX_BYTES)throw failure();
            try(InputStream in=connection.getInputStream()){return readBounded(in,length);}
        }catch(IOException|RuntimeException e){
            // Never expose URL, bearer, server error body or platform exception text.
            throw failure();
        }finally{
            java.util.Arrays.fill(request,(byte)0);
            if(connection!=null)connection.disconnect();
        }
    }
    static byte[] readBounded(InputStream in,long declared) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[8192];
        long start=System.nanoTime();
        while(true){
            if(System.nanoTime()-start>TIMEOUT_MS*1000000L)throw failure();
            int n=in.read(buffer);
            if(n<0)break;
            if(n==0||n>SyncAckDecoder.MAX_BYTES-out.size())throw failure();
            out.write(buffer,0,n);
        }
        if(out.size()==0||(declared>=0&&declared!=out.size()))throw failure();
        return out.toByteArray();
    }
    private static IOException failure(){return new IOException("Synchronization transport failed");}
}
