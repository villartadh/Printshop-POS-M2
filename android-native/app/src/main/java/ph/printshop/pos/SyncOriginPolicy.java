package ph.printshop.pos;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** An explicitly provisioned HTTPS origin. No discovery, network I/O or trust override. */
final class SyncOriginPolicy {
    private final URI origin;
    private SyncOriginPolicy(URI origin){this.origin=origin;}
    static SyncOriginPolicy parse(String raw){
        if(raw==null||raw.length()>300||raw.isEmpty()||raw.indexOf('%')>=0||raw.indexOf('\\')>=0)
            throw new IllegalArgumentException("Invalid synchronization origin");
        for(int i=0;i<raw.length();i++)if(raw.charAt(i)<=32||raw.charAt(i)>126)
            throw new IllegalArgumentException("Invalid synchronization origin");
        try{
            URI u=new URI(raw);
            String host=u.getHost();
            if(!"https".equalsIgnoreCase(u.getScheme())||host==null||u.getRawUserInfo()!=null||
                u.getRawQuery()!=null||u.getRawFragment()!=null||
                !(u.getRawPath().isEmpty()||"/".equals(u.getRawPath()))||
                u.getPort()==0||u.getPort()>65535||u.getPort()<-1)
                throw new IllegalArgumentException("HTTPS origin required");
            // Reject alternative authority encodings (empty port, leading-zero port, etc.).
            String authority=host+(u.getPort()<0?"":":"+u.getPort());
            if(!authority.equalsIgnoreCase(u.getRawAuthority()))throw new IllegalArgumentException("Ambiguous synchronization authority");
            host=host.toLowerCase(Locale.ROOT);
            // DNS names and canonical IPv4 only for this first stage. IPv6 pairing needs
            // dedicated identity/certificate tests before enabling it.
            if(host.length()>253||host.endsWith(".")||host.indexOf(':')>=0)throw new IllegalArgumentException("Unsupported synchronization host");
            if(host.matches("[0-9.]+")){
                String[] parts=host.split("\\.",-1);
                if(parts.length!=4)throw new IllegalArgumentException("Invalid IPv4 origin");
                for(String part:parts){
                    if(!part.matches("0|[1-9][0-9]{0,2}")||Integer.parseInt(part)>255)
                        throw new IllegalArgumentException("Invalid IPv4 origin");
                }
            }else{
                for(String part:host.split("\\.",-1))
                    if(!part.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))
                        throw new IllegalArgumentException("Invalid DNS origin");
                // Numeric final labels may be interpreted as noncanonical IP addresses.
                if(host.substring(host.lastIndexOf('.')+1).matches("(?:[0-9]+|0x[0-9a-f]+)"))
                    throw new IllegalArgumentException("Ambiguous synchronization host");
            }
            int port=u.getPort()==443?-1:u.getPort();
            return new SyncOriginPolicy(new URI("https",null,host,port,null,null,null));
        }catch(URISyntaxException e){throw new IllegalArgumentException("Invalid synchronization origin",e);}
    }
    String identity(){return origin.toASCIIString();}
    URI batchEndpoint(){return origin.resolve("/api/sync/batch");}
    /** HTTP redirects are never followed, including redirects to the same origin. */
    static void requireNoRedirect(int status){
        if(status>=300&&status<400)throw new IllegalArgumentException("Synchronization redirect refused");
    }
}
