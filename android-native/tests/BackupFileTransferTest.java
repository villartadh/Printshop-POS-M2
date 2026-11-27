package ph.printshop.pos;
import java.io.*;
import java.util.Arrays;
public final class BackupFileTransferTest {
    interface Check { void run() throws IOException; }
    static void reject(Check c) throws IOException {
        try { c.run(); throw new AssertionError("invalid transfer accepted"); }
        catch(IOException expected) {}
    }
    public static void main(String[] args) throws IOException {
        byte[] data=new byte[20001];new java.util.Random(42).nextBytes(data);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        BackupFileTransfer.write(out,data);
        if(!Arrays.equals(data,BackupFileTransfer.read(new ByteArrayInputStream(out.toByteArray()))))throw new AssertionError("transfer changed bytes");
        reject(()->BackupFileTransfer.read(null));
        reject(()->BackupFileTransfer.read(new ByteArrayInputStream(new byte[0])));
        reject(()->BackupFileTransfer.read(new InputStream(){public int read(){return 0;}public int read(byte[] b){return 0;}}));
        reject(()->BackupFileTransfer.read(new InputStream(){long remaining=(long)BackupFileTransfer.MAX_ARCHIVE+1;public int read(){return remaining-->0?0:-1;}public int read(byte[] b){if(remaining<=0)return -1;int n=(int)Math.min(remaining,b.length);remaining-=n;return n;}}));
        reject(()->BackupFileTransfer.write(null,data));
        reject(()->BackupFileTransfer.write(out,null));
        reject(()->BackupFileTransfer.write(out,new byte[0]));
        reject(()->BackupFileTransfer.write(out,new byte[BackupFileTransfer.MAX_ARCHIVE+1]));
        reject(()->BackupFileTransfer.write(new OutputStream(){public void write(int b)throws IOException{throw new IOException("synthetic failure");}},data));
        System.out.println("BackupFileTransfer: 10 checks PASS");
    }
}
