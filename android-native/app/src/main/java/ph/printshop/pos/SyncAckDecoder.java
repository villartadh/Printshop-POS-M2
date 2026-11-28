package ph.printshop.pos;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Narrow decoder for the desktop batch acknowledgment, not a general JSON parser.
 * Reject malformed, repeated, unknown or incomplete fields before resolving any row. */
final class SyncAckDecoder {
    static final int MAX_BYTES=256*1024;
    private SyncAckDecoder(){}
    static Map<String,SyncBatchPolicy.Resolution> decode(byte[] bytes,SyncBatchPolicy.Batch batch,String server,String owner){
        if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw invalid();
        final String input;
        try{
            input=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        }catch(CharacterCodingException e){throw invalid();}
        Parser p=new Parser(input);p.take('{');Set<String> seen=new HashSet<>();
        String time=null;List<SyncBatchPolicy.Ack> rows=null;
        if(!p.peek('}'))do{
            String key=p.string();if(!seen.add(key))throw invalid();p.take(':');
            if("syncedAt".equals(key))time=p.string();
            else if("results".equals(key))rows=p.rows();
            else throw invalid();
        }while(p.optional(','));
        p.take('}');p.space();if(p.pos!=input.length()||time==null||rows==null)throw invalid();
        return SyncBatchPolicy.resolve(batch,server,owner,time,rows);
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid synchronization acknowledgment");}
    private static final class Parser{
        final String input;int pos;
        Parser(String input){this.input=input;}
        void space(){while(pos<input.length()&&" \r\n\t".indexOf(input.charAt(pos))>=0)pos++;}
        boolean peek(char ch){space();return pos<input.length()&&input.charAt(pos)==ch;}
        boolean optional(char ch){if(!peek(ch))return false;pos++;return true;}
        void take(char ch){if(!optional(ch))throw invalid();}
        String string(){
            take('"');StringBuilder out=new StringBuilder();
            while(pos<input.length()){
                char ch=input.charAt(pos++);
                if(ch=='"'){
                    for(int i=0;i<out.length();i++){
                        char c=out.charAt(i);
                        if(Character.isHighSurrogate(c)){if(++i>=out.length()||!Character.isLowSurrogate(out.charAt(i)))throw invalid();}
                        else if(Character.isLowSurrogate(c))throw invalid();
                    }
                    return out.toString();
                }
                if(ch<32)throw invalid();
                if(ch=='\\'){
                    if(pos>=input.length())throw invalid();
                    char esc=input.charAt(pos++);
                    switch(esc){
                        case '"':case '\\':case '/':ch=esc;break;
                        case 'b':ch='\b';break;case 'f':ch='\f';break;case 'n':ch='\n';break;
                        case 'r':ch='\r';break;case 't':ch='\t';break;
                        case 'u':
                            if(pos+4>input.length())throw invalid();int n=0;
                            for(int j=0;j<4;j++){char h=input.charAt(pos++);int digit=h>='0'&&h<='9'?h-'0':h>='a'&&h<='f'?h-'a'+10:h>='A'&&h<='F'?h-'A'+10:-1;if(digit<0)throw invalid();n=n*16+digit;}
                            ch=(char)n;break;
                        default:throw invalid();
                    }
                }
                out.append(ch);if(out.length()>2048)throw invalid();
            }
            throw invalid();
        }
        Boolean bool(){
            space();if(input.startsWith("true",pos)){pos+=4;return true;}
            if(input.startsWith("false",pos)){pos+=5;return false;}throw invalid();
        }
        List<SyncBatchPolicy.Ack> rows(){
            take('[');List<SyncBatchPolicy.Ack> rows=new ArrayList<>();
            if(!peek(']'))do{
                if(rows.size()>=SyncBatchPolicy.MAX_BATCH)throw invalid();
                take('{');Set<String> seen=new HashSet<>();String id=null,status=null,error=null;Boolean retry=null;
                if(!peek('}'))do{
                    String key=string();if(!seen.add(key))throw invalid();take(':');
                    switch(key){
                        case "clientMutationId":id=string();break;
                        case "status":status=string();break;
                        case "error":error=string();break;
                        case "retryable":retry=bool();break;
                        default:throw invalid();
                    }
                }while(optional(','));
                take('}');if(id==null||status==null)throw invalid();
                rows.add(new SyncBatchPolicy.Ack(id,status,error,retry));
            }while(optional(','));
            take(']');return rows;
        }
    }
}
