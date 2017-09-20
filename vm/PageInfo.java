package nachos.vm;

import nachos.machine.TranslationEntry;



public class PageInfo {
	public PageInfo(int pid, TranslationEntry entry) {
		this.pid = pid;
		this.entry = entry;
	}
	int pid;
	TranslationEntry entry;
}