package nachos.vm;

import java.util.*;
import nachos.threads.ThreadedKernel;
import nachos.machine.*;
import nachos.threads.*;
import nachos.vm.*;

public class SwapManager {

	public SwapManager() {
		swapFile = ThreadedKernel.fileSystem.open(swapFileName, true);
		byte[] buf = new byte[Processor.pageSize];
	}

	public PageInfo getPhysPageToSwap(){
		boolean pinned;
		int ppn;
		int numPhysPages = Machine.processor().getNumPhysPages();
		for(int i = 0; i < numPhysPages;i++){
			ppn = (i+swapPointer)%Processor.pageSize;
			pinned = VMKernel.pinnedPages.contains(ppn);
			if(!pinned) {
				swapPointer = ppn+1;
				return VMKernel.globalPageTable[ppn];
			}
		}
		return null;
	}

	//Return a clean translation entry
	public TranslationEntry swapIn(PageInfo pageInfo){
		int spn = getFreeSPN();
		VMKernel.PageTableKey key = new VMKernel.PageTableKey(pageInfo.pid, pageInfo.entry.vpn);
		swapTable.put(key.toString(), pageInfo.entry);
		swapFile.write(spn*Processor.pageSize, getPageContent(pageInfo.entry.ppn), 0, Processor.pageSize);
		TranslationEntry te = new TranslationEntry(-1, pageInfo.entry.ppn, true, false, false, false);
		VMKernel.globalPageTable[pageInfo.entry.ppn] = null;
		return te;
	}

	public TranslationEntry releaseSPN(int vpn, int pid){	
		VMKernel.PageTableKey key = new VMKernel.PageTableKey(pid, vpn);
		TranslationEntry te = swapTable.get(key.toString());
		swapTable.remove(key.toString());
		return te;
	}

	public byte[] retrieveSwapPage(int vpn, int pid){
		VMKernel.PageTableKey key = new VMKernel.PageTableKey(pid, vpn);
		TranslationEntry te = swapTable.get(key.toString());
		byte[] res = new byte[Processor.pageSize];
		swapFile.read(te.vpn*Processor.pageSize, res, 0, Processor.pageSize);
		return res;
	}

	private int getFreeSPN(){
		int spn = freeSwapPages.poll();
		if(freeSwapPages.size()==0)spn = swapTable.keySet().size();
		return spn;
	}

	private static byte[] getPageContent(int ppn){
   		byte[] res = new byte[Processor.pageSize];
		System.arraycopy(Machine.processor().getMemory(),ppn*Processor.pageSize, res, 0, ppn*Processor.pageSize);
		return res;
  	}

	public void close() {
		swapFile.close();
		ThreadedKernel.fileSystem.remove(swapFileName);
	}

	public TranslationEntry find(VMKernel.PageTableKey key){
		return swapTable.get(key.toString());
	}

	private static final String swapFileName = "/SWAP";
	private OpenFile swapFile;
	private PriorityQueue<Integer> freeSwapPages = new PriorityQueue<Integer>();
	private static Hashtable<String, TranslationEntry>  swapTable = new Hashtable<String, TranslationEntry>();
	private static int swapPointer = 0;
}
