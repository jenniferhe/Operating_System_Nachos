package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.util.LinkedList;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		System.out.println("UserKernel initialized()");
		super.initialize(args);

		console = new SynchConsole(Machine.console());
		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});
		//Initialize freePage LinkedList
		for(int i = 0; i < Machine.processor().getNumPhysPages();i++){
			freePages.add(new TranslationEntry(0,i,false,false,false,false));
		}
		lock = new Lock();
	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
		super.selfTest();

		System.out.println("Testing the console device. Typed characters");
		System.out.println("will be echoed until q is typed.");

		char c;

		do {
			c = (char) console.readByte(true);
			console.writeByte(c);
		} while (c != 'q');

		System.out.println("");
	}

	/**
	 * Returns the current process.
	 * 
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 * 
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
	//	System.out.println("Enter exception handler " + cause);
		process.handleException(cause);
	//	System.out.println("Exit exception handler");
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 * 
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		Lib.assertTrue(process.execute(shellProgram, new String[] {}));

		KThread.currentThread().finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	/**
 	 * Acquire one free page from the linkedlist. 
 	 * @return pageId or -1 when there is no available pages
 	 */
	public static TranslationEntry acquirePage(){
		lock.acquire();
		TranslationEntry res = freePages.poll();
		res.valid = true;
		lock.release();
		return res;
	}

	public static TranslationEntry[] acquirePages(int numPages){
		TranslationEntry[] returnPages = null;
		lock.acquire();
		if (!freePages.isEmpty() && freePages.size() >= numPages) {
			returnPages = new TranslationEntry[numPages];
			for (int i = 0; i < numPages; ++i) {
				returnPages[i] = freePages.remove();
				returnPages[i].valid = true;
			}
		}		
		lock.release();
		return returnPages;
	}

 	/**
	 * Release the currentPage and put it back into freepage linkedlist
	 * @param pageId the id of a page
  	 */
	public static void releasePage(TranslationEntry te){
		lock.acquire();
		te.valid = false;
		freePages.addLast(te);
		lock.release();
	}

	public static void releasePages(TranslationEntry[] pageTable){
		lock.acquire();
		for (TranslationEntry te : pageTable) {
			freePages.add(te);
			if(te !=null)te.valid = false;
		}
		lock.release();
	}

	public static int getAvailablePages(){
		int size = 0;
		lock.acquire();
		size = freePages.size();
		lock.release();
		return size;
	}

	//helper method:
	public static int vpn(int vaddr){
		return vaddr/Processor.pageSize;
	}
	public static int offset(int vaddr){
		return vaddr%Processor.pageSize;
	}
	public static int addr(int pn, int offset){
		return pn*Processor.pageSize + offset;
	}

	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;
	
	//Global linkedList of free physical address of free pages in the kernel
	protected static LinkedList<TranslationEntry> freePages = new LinkedList<TranslationEntry>();

	protected static Lock lock;
}
