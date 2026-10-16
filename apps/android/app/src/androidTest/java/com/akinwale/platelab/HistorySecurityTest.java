package com.akinwale.platelab;

import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HistorySecurityTest {
    private File directory,file;
    private Context isolated;
    @Before public void isolatedStore() throws Exception {
        Context real=InstrumentationRegistry.getInstrumentation().getTargetContext();
        directory=new File(real.getCacheDir(),"history-security-"+UUID.randomUUID());assertTrue(directory.mkdir());
        isolated=new ContextWrapper(real){@Override public File getFilesDir(){return directory;}};
        file=new File(directory,"readings.json");
    }
    @After public void cleanup(){for(File child:Objects.requireNonNull(directory.listFiles()))child.delete();directory.delete();}
    private List<JSONObject> fixture() throws Exception {return Collections.singletonList(new JSONObject().put("id","synthetic").put("text","ABC123DE"));}
    private byte[] bytes() throws Exception {return read(file);}
    private byte[] read(File f) throws Exception {try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    private void write(File f,byte[] b) throws Exception {try(FileOutputStream out=new FileOutputStream(f)){out.write(b);}}
    @Test public void encryptionRoundtripUsesFreshNonceAndHidesPlateText() throws Exception {
        ReadingStore store=new ReadingStore(isolated);assertTrue(store.load().isEmpty());store.save(fixture());byte[] first=bytes();
        assertFalse(new String(first,StandardCharsets.ISO_8859_1).contains("ABC123DE"));assertEquals("ABC123DE",new ReadingStore(isolated).load().get(0).getString("text"));
        store.save(fixture());assertFalse(Arrays.equals(first,bytes()));
    }
    @Test public void existingPlaintextIsMigratedWithoutLosingRecords() throws Exception {
        write(file,new JSONArray(fixture()).toString().getBytes(StandardCharsets.UTF_8));
        assertEquals("ABC123DE",new ReadingStore(isolated).load().get(0).getString("text"));assertEquals('P',bytes()[0]);
        assertFalse(new String(bytes(),StandardCharsets.ISO_8859_1).contains("ABC123DE"));
    }
    @Test public void tamperingIsRejectedAndCannotBeOverwritten() throws Exception {
        ReadingStore store=new ReadingStore(isolated);store.load();store.save(fixture());byte[] corrupt=bytes();corrupt[corrupt.length-1]^=1;write(file,corrupt);
        ReadingStore broken=new ReadingStore(isolated);try{broken.load();fail("Tamper accepted");}catch(IOException expected){}
        try{broken.save(Collections.emptyList());fail("Unreadable data overwritten");}catch(IOException expected){}
        assertArrayEquals(corrupt,bytes());
    }
    @Test public void interruptedLegacyWriteRecoversAtomicBackup() throws Exception {
        byte[] old=new JSONArray(fixture()).toString().getBytes(StandardCharsets.UTF_8);write(new File(file+".bak"),old);
        write(file,"partial".getBytes(StandardCharsets.UTF_8));assertEquals(1,new ReadingStore(isolated).load().size());assertEquals('P',bytes()[0]);
    }
}
