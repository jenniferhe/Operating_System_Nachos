package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.Random;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {

		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		TranslationEntry te;
		for (int i = 0; i < Machine.processor().getTLBSize(); ++i) {
			 te = Machine.processor().readTLBEntry(i);
			 te.valid = false;
			 Machine.processor().writeTLBEntry(i, te);
		}
	//	super.restoreState();
	}


	protected void unloadSections() {
		for (int i = 0; i < coff.getNumSections(); i++) {
            TranslationEntry te = pageTable[i];
            if(te!=null)VMKernel.unloadPage(PID, te.vpn);
        }
	}


	/**
     * Initializes page tables for this process so that the executable can be
     * demand-paged.
     * 
     * @return	<tt>true</tt> if successful.
     */
    protected boolean loadSections() {
    	try{
			//pageTable = UserKernel.acquirePages(numPages);
			int pageIndex = 0;

			for (int sectionNumber = 0; sectionNumber < coff.getNumSections(); sectionNumber++) {
				CoffSection section = coff.getSection(sectionNumber);

				Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

				for (int i = 0; i < section.getLength(); i++){
					int vpn = section.getFirstVPN() + i;
					TranslationEntry te = VMKernel.loadPage(PID, i);
					if (te != null) {
						pageTable[vpn] = te;
	                    te.vpn = vpn;
	                    Lib.debug(dbgProcess, "Loading page with PID:" + PID + " and VPN: " + vpn + " = " + te);
	                    te.readOnly = section.isReadOnly();
	                    section.loadPage(i, te.ppn);
	                    pageIndex++;
					}else{
						TranslationEntry teFromSwap = VMKernel.loadPageFromSwap(PID, vpn,i);
						if (teFromSwap != null) {
							TranslationEntry newTe = new TranslationEntry(-1, -1, true, false, true, false);
							VMKernel.PageTableKey key = new VMKernel.PageTableKey(PID, vpn);
                        	TranslationEntry removed = VMKernel.clockAlgorithm(key.toString(), newTe);
                        	byte[] memory = Machine.processor().getMemory();
                       		byte[] page = new byte[pageSize];
                       		System.arraycopy(memory, removed.ppn, page, 0, pageSize);
							pageTable[removed.vpn] = newTe;     
						}else{
							coff.close();
                        	return false;
						}
					}

				}

			}
		}catch(Exception e){
			System.out.println("UserProcess.loadSections() exception " + e);
		}
		return true;
    }

	/*
	* @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();
		if(cause == Processor.exceptionTLBMiss){
			handleTLBMiss(processor.readRegister(Processor.regBadVAddr));
			int result = handleTLBMiss(processor.readRegister(Processor.regBadVAddr));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
		}else{
			super.handleException(cause);
		}
	}
	

	public int handleTLBMiss(int vaddr){
		if(!validAddress(vaddr))return -1;
		TranslationEntry te = getPageFromPageTable(vaddr);
		if(te == null||!te.valid){
			te =  VMKernel.loadPage(PID, VMKernel.vpn(vaddr));
			// int ppn = VMKernel.acquirePage(PID);
			// te = new TranslationEntry(VMKernel.vpn(vaddr), ppn, true, false, false, false);
		}
		System.out.println("VMProcess.handleTLBMiss() \nWrite TLB Entry: vpn: " + te.vpn + " ppn:" + te.ppn +"\n");
		VMKernel.tlbManager.add(te);
		return te.ppn;
	}



	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		VMKernel.PageTableKey key = new VMKernel.PageTableKey(PID,VMKernel.vpn(vaddr));
		TranslationEntry te = VMKernel.invertedPageTable.get(key);
		te.used = true;
		return super.readVirtualMemory(vaddr, data, offset, length);
	}

	public int writeVirtualMemory(int vaddr, byte[] data, int offset,int length){
		VMKernel.PageTableKey key = new VMKernel.PageTableKey(PID,VMKernel.vpn(vaddr));
		TranslationEntry te = VMKernel.invertedPageTable.get(key);
		te.used = true;
		te.dirty = true;
		return super.writeVirtualMemory(vaddr, data, offset, length);
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}