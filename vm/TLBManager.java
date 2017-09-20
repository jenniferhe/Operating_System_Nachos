package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.TranslationEntry;
import nachos.vm.*;

public class TLBManager {

	public TLBManager(){}	

	public void add(TranslationEntry entry) {
		System.out.println("Add new entry to TLB: vpn = " + entry.vpn);
		int index = -1;
		for(int i = 0; i < Machine.processor().getTLBSize();i++){
			if (!Machine.processor().readTLBEntry(i).valid) {
				index = i;
				break;
			}
		}
		if(index == -1)index = Lib.random(Machine.processor().getTLBSize());
		System.out.println("Add this tlb entry to index " + index);
		Machine.processor().writeTLBEntry(index, entry);
	}

	public void invalid(int index){
		TranslationEntry entry = Machine.processor().readTLBEntry(index);
		entry.valid = false;
		Machine.processor().writeTLBEntry(index, entry);
	}

	public void updateGlobalInvertedPageTable(int pid){
		TranslationEntry entry;
		for(int i = 0; i < Machine.processor().getTLBSize();i++){
			entry = Machine.processor().readTLBEntry(i);
			if(entry.valid){
				VMKernel.PageTableKey key = new VMKernel.PageTableKey(pid, entry.vpn);
				TranslationEntry te = VMKernel.invertedPageTable.get(key.toString());
				if(te!=null){
					te.dirty = entry.dirty || te.dirty;
					te.readOnly = te.readOnly||entry.readOnly;
				}
			}
		}
	}

	public TranslationEntry find(int vpn, boolean isWrite) {
		for (int i = 0; i < Machine.processor().getTLBSize(); ++i) {
			TranslationEntry entry = Machine.processor().readTLBEntry(i);
			if (entry.valid && entry.vpn == vpn) {
				if (entry.readOnly && isWrite)//We can not write to a read-only page
					return null;
				entry.dirty = entry.dirty || isWrite;
				entry.used = true;
				Machine.processor().writeTLBEntry(i, entry);
				return entry;
			}
		}
		return null;
	}

	public void clear(){}
}
