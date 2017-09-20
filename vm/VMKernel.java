package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
    /**
     * Allocate a new VM kernel.
     */
    public VMKernel() {
        super();
    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
        super.initialize(args);
        lock = new Lock();
        swapManager = new SwapManager();
        tlbManager = new TLBManager();
    }

    /**
     * Test this kernel.
     */
    public void selfTest() {super.selfTest();}

    /**
     * Start running user programs.
     */
    public void run() {super.run();}

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {super.terminate();}

    // public static int acquirePage(int pid){
    //  System.out.println("VMKernel.acquirePage() pid = " + pid + " free page size = "+ freePages.size());
    //  int pageId = -1;
    //  freePagesLock.acquire();
    //  if(freePages.peek()!=null){
    //      pageId = freePages.poll().ppn;
    //  }
    //  freePagesLock.release();
    //  if(pageId != -1){
    //       return pageId;
    //  }
    //  pageId = swapManager.getPhysPageToSwap();
    //  acquirePage(pid, pageId);   
    //  System.out.println("acquirePage() calls swap()  returns " + pageId + "\n");
    //  return pageId;
    // }
    

    // public static void acquirePage(int pid, int ppn){
    //  //If the content that is stored in ppn is valid, we need to save that content onto swap space
    //  byte[] buff = new byte[Processor.pageSize];
    //  swapManager.swapOut(ppn,buff);
    //  if(invertedPageTable[ppn].entry.valid){
    //      swapManager.swapIn(ppn, getPageContent(ppn));
    //  }
    // }

    //load a page from free page table
    public static TranslationEntry loadPage(int pid, int vpn) {
        System.out.println("VMKernel.loadPage() pid = " + pid + " vpn = " + vpn);
        lock.acquire();              
        PageTableKey key = new VMKernel.PageTableKey(pid, vpn);
        TranslationEntry te = null;
        if(freePages.peek()!=null){
            te = freePages.poll();
            te.valid = true;
            
        }else{
            PageInfo info = swapManager.getPhysPageToSwap();
            te = swapManager.swapIn(info);
            te.vpn = vpn;
            te.valid = true;
            
        }
        invertedPageTable.put(key.toString(), te);
        globalPageTable[te.ppn] = new PageInfo(pid, te);
        //if(te != null) return te;

        // if(getAvailablePages()>0) {
        //     te = acquirePage();
        //     invertedPageTable.put(key.toString(), te);
        // }else {
        //     loadPageFromSwap(pid, vpn);
        // }
        // te = invertedPageTable.get(key.toString());
        // Lib.debug(dbgVM, "Loaded page: " + pid + ", " + vpn + " TE:" + te);
        lock.release();
        System.out.println("VMKernel.loadPage() finished");
        return te;
    }

    public static void unloadPage(int pid, int vpn) {       
        PageTableKey key = new VMKernel.PageTableKey(pid, vpn);        
        TranslationEntry te = invertedPageTable.get(key.toString());
        if (te != null) {
            releasePage(te);
            invertedPageTable.remove(key);    
        }        
    }


    public static TranslationEntry loadPageFromSwap(int pid, int vpn, int ppn) {
        lock.acquire();              
        PageTableKey key = new VMKernel.PageTableKey(pid, vpn);
        TranslationEntry te = swapManager.find(key);
        te.ppn = ppn;
        globalPageTable[ppn] = new PageInfo(pid, te);
        invertedPageTable.put(key.toString(), te);
        lock.release();
        return te;
    }

    public static TranslationEntry getEntry(int pid, int vpn) {        
        PageTableKey key = new VMKernel.PageTableKey(pid, vpn);        
        TranslationEntry te = invertedPageTable.get(key.toString());
        if (te == null) {
            //Search in swapfile
           te = swapManager.find(key);
        }
        return te;
    }

    public static TranslationEntry setEntry(int pid, int vpn, int ppn) {
        lock.acquire();
        PageTableKey key = new PageTableKey(pid, vpn);        
        TranslationEntry newTe = new TranslationEntry(vpn, ppn, true, false, true, false);
        newTe.ppn = ppn;
        invertedPageTable.put(key.toString(), newTe);
        lock.release();
        return newTe;
    }

    public static TranslationEntry clockAlgorithm(String key, TranslationEntry value) {
        lock.acquire();
        if (invertedPageTable.isEmpty()) {
            lock.release();
            return null;
        }
        int count=0;
        int dirty=0;
        TranslationEntry te;
        Enumeration e = invertedPageTable.keys();
        while(e.hasMoreElements()) {
            String keyValue = (String) e.nextElement();
            te = (TranslationEntry) invertedPageTable.get(keyValue);
            if (!te.used) {
                value.ppn = te.ppn;
                invertedPageTable.remove(keyValue);
                invertedPageTable.put(key, value);
                lock.release();
                return te;
            } else {
                if (!te.dirty) {
                    te.used = false;
                    invertedPageTable.put(keyValue,te);
                    count++;        
                }else dirty++;
            }
        }
        if (count>0 || dirty>0) {
            Enumeration en = invertedPageTable.keys();
            if (en.hasMoreElements()) {
                String kv = (String) en.nextElement();
                te = (TranslationEntry) invertedPageTable.get(kv);
                value.ppn = te.ppn;                
                invertedPageTable.remove(kv);
                invertedPageTable.put(key,value);
                lock.release();
                return te;
            }
        }
        lock.release();
        return null;
    }

    public static class PageTableKey {
        private int PID;
        private int vpn;
        public PageTableKey(int pid, int vpn) {
            this.PID = pid;
            this.vpn = vpn;
        }
        public int getVPN() {return this.vpn;}
        public int getPID() {return this.PID;}
        public String toString() {return this.PID + "-" + this.vpn;}
    }

    // private static void releaseSwapTable(int element) {
    //     lock.acquire();
    //     swapPPN.add(element,-1);
    //     lock.release();
    // }
    
    // private static TranslationEntry getSwapTable(String key) {
    //     TranslationEntry te = (TranslationEntry) swapTable.get(key);
    //     return te;
    // }

    // public static void writeSwapFile(TranslationEntry te, byte[] page) {        
    //     //UserProcess.readVirtualMemory(bufferToWritePointer, bytesToWrite, 0, numberOfBytes);
    //     swapFile.write(page,0,page.length);
    // }

    // dummy variables to make javac smarter
    private static VMProcess dummy1 = null;
    private static final char dbgVM = 'v';
    public static TLBManager tlbManager;
    public static SwapManager swapManager;
    public static HashSet<Integer> pinnedPages;
    public static Hashtable<String, TranslationEntry> invertedPageTable = new Hashtable<String, TranslationEntry> ();
    public static PageInfo[] globalPageTable = new PageInfo[Machine.processor().getNumPhysPages()];
}
