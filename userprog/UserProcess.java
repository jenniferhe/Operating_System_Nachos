package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.util.HashMap;

import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {

		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		parent = null;
		status = -999;
		childProcesses = new HashMap<Integer, UserProcess>();
		
		this.PID =currentPID;
		currentPID++;
		//When any process is started, its file descriptors 0 and 1 must refer to
		//standard input and standard output
		fileTable = new OpenFile[16];
		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();									
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;
		thread = new UThread(this);
		thread.setName(name).fork();
		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0){
				return new String(bytes, 0, length);
			}
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		if(!validAddress(vaddr)) return -1;
		byte[] memory = Machine.processor().getMemory();

		//Address translation
		int vpn = vaddr/pageSize;
		int voffset = vaddr%pageSize;
		int end = (vaddr+length)/pageSize;

		int ppn = 0;
		int ppAddr = 0;
		int bytesToCopy = 0;
		int bytesCopied = 0;
		for(int i = vpn; i <= end; i++){
			ppn = pageTable[i].ppn;//Get the physical page value of a virtual page number
			pageTable[i].used = true; //pageTable[i].dirty = true;
			if(i == vpn)ppAddr += voffset;
			ppAddr += ppn*pageSize;
			if(i == end) bytesToCopy = length%pageSize;
			System.arraycopy(memory, ppAddr, data, offset, bytesToCopy);
			offset += bytesToCopy;
			bytesCopied += bytesToCopy;
		}
		return bytesCopied;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		if(!validAddress(vaddr)) return -1;
		byte[] memory = Machine.processor().getMemory();
		int vpn = vaddr/pageSize;
                int voffset = vaddr%pageSize;
                int end = (vaddr+length)/pageSize;

                int ppn = 0;
                int ppAddr = 0;
                int bytesToCopy = 0;
                int bytesCopied = 0;
                for(int i = vpn; i <= end; i++){
                        ppn = pageTable[i].ppn;//Get the physical page value of a virtual page number
                        pageTable[i].used = true; 
			pageTable[i].dirty = true;
                        if(i == 0)ppAddr += voffset;
                        ppAddr += ppn*pageSize;
                        if(i == end) bytesToCopy = length%pageSize;
                        System.arraycopy(data, offset, memory, ppAddr, bytesToCopy);
                        offset += bytesToCopy;
                        bytesCopied += bytesToCopy;
                }
                return bytesCopied;
	}


	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		try{
			pageTable = UserKernel.acquirePages(numPages);
			for(int i = 0; i < pageTable.length;i++)
				pageTable[i].vpn = i;

			for (int sectionNumber = 0; sectionNumber < coff.getNumSections(); sectionNumber++) {
				CoffSection section = coff.getSection(sectionNumber);

				Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

				int firstVPN = section.getFirstVPN();
				for (int i = 0; i < section.getLength(); i++)
					section.loadPage(i, pageTable[i+firstVPN].ppn);
			}
		}catch(Exception e){
			System.out.println("UserProcess.loadSections() exception " + e);
		}
		return true;
		/*
		if (numPages > Machine.processor().getNumPhysPages()) {
                        coff.close();
                        Lib.debug(dbgProcess, "\tinsufficient physical memory");
                        return false;
                }

                pageTable = new TranslationEntry[numPages];

		System.out.println("UserKernel.loadSections(): coff.getNumSections() =  "+ coff.getNumSections());
                for (int s = 0; s < coff.getNumSections(); s++) {
                        CoffSection section = coff.getSection(s);

                        Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                                        + " section (" + section.getLength() + " pages)");

                        for (int i = 0; i < section.getLength(); i++) {
                                int vpn = section.getFirstVPN() + i;
                                int ppn = UserKernel.acquirePage();
                                pageTable[vpn] = new TranslationEntry(vpn, ppn, true, section.isReadOnly(),false, false);
                                section.loadPage(i,ppn);
                        }
                }

               // for (int i = numPages - stackPages ; i < numPages; i++)
                //        pageTable[i] = new TranslationEntry(i, UserKernel.acquirePage(), true, false, false, false);

                return true;*/
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		//coff.close()
		UserKernel.releasePages(pageTable);	
		/*
		for(TranslationEntry t: pageTable){
			t.used = false;
			UserKernel.releasePage(t.ppn);
		}*/
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt(){
		//Only the root process should be allowed to execute this syscall
		//Any other process should ignore the syscall and return immediately				
		System.out.println("Enter halt!");
		if (this.PID != ROOT) return -1;
		Machine.halt();
		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}
	/**
 	 * A private helper method that return first unused file descriptor, or -1 if fileTable full
  	 */
	private int getFileDescriptor(){
		for (int i = 0; i < fileTable.length;i++){
			if(fileTable[i]==null)return i;
		}
		return -1;
	}

	/**
 	 * A private helper method that Check to see if the virtual address is valid.
 	 * @param address
 	 * @return true if valid, false if not valid
	 */
	protected boolean validAddress(int address){
		int vpn = Processor.pageFromAddress(address);
		return (vpn<numPages && vpn>=0);
	}

	/**
	 * Handle the create() system call.
	 * Attempt to open the named disk file, creating it if it does not exist
	 * @param namePtr pointer to null terminated file name
	 * @return file descriptor used to further reference this file; -1 if creation failed
	 */
	protected int handleCreate(int namePtr){
		//If the file descriptors are all full, return -1 directly
		int descriptor = getFileDescriptor();
		String fileName = this.readVirtualMemoryString(namePtr, MAX_SYSCALL_ARG_LENGTH);
		//System.out.println(fileName);
		if(descriptor == -1 || fileName == null) return -1;
		OpenFile file = ThreadedKernel.fileSystem.open(fileName, true);
		//If the file creation failed, return -1.
		if(file != null){
			fileTable[descriptor] = file;
			return descriptor;
		}
		return -1;
	}


	/**
	 * Handle the open() system call.
	 * @param namePtr pointer to null terminated file name
	 * @return file descriptor used to further reference this file; -1 if creation failed
	 */
	protected int handleOpen(int namePtr){
		int descriptor = getFileDescriptor();
		String fileName = this.readVirtualMemoryString(namePtr, MAX_SYSCALL_ARG_LENGTH);
		//If the file descriptors are all full, return -1 directly
		if(descriptor == -1 || fileName == null) return -1; 
		OpenFile file = ThreadedKernel.fileSystem.open(fileName, false);
		
		if(file != null){//If the file creation failed, return -1
			fileTable[descriptor] = file;
			return descriptor;
		}
		return -1;
	}

	/**
	 * Handle the read() system call.
	 * @param fileDescrptior file descriptor
	 * @param bufferPtr pointer to buffer in virtual memory
	 * @param size the number of byte to be read
	 * @return number of bytes read, or -1 on error
	 */
	protected int handleRead(int fileDescriptor, int bufferPtr, int size){
		if(fileDescriptor < 0 || fileDescriptor >= 16||size <0) return -1; //Make sure fileDescriptor is in range
		OpenFile file = fileTable[fileDescriptor];
		if(file == null) return -1;
		byte[] buff = new byte[size];
		int bytesRead = file.read(buff,0,size);
		if(bytesRead == -1) return -1;
		int bytesWritten = writeVirtualMemory(bufferPtr, buff, 0, bytesRead);
		if(bytesWritten != bytesRead) return -1;
		return bytesWritten;
	}

	/**
	 * Handle the write() system call.
	 * @param fileDescrptior file descriptor
	 * @param bufferPtr pointer to buffer in virtual memory
	 * @param size the number of byte to be write
	 * @return number of bytes written, or -1 on error
	 */
	protected int handleWrite(int fileDescriptor, int bufferPointer, int size){
		if(fileDescriptor < 0 || fileDescriptor >= 16 || size < 0) return -1;//Make sure fileDescriptor is in range
		OpenFile file = fileTable[fileDescriptor];
		if (file == null) return -1;
		byte[] buf = new byte[size];
		readVirtualMemory(bufferPointer, buf);//Do we need to check whether file descriptor is in range?
		int bytesWritten = file.write(fileDescriptor,buf,0,size);
		return bytesWritten;
	}

	/**
	 * Handle the close() system call.
	 * @param fileDescriptor file descriptor
	 * @return 0 on success, -1 on error
	 */
	protected int handleClose(int fileDescriptor){
		if(fileDescriptor >=0  &&fileDescriptor < 16){ 
			if(fileTable[fileDescriptor] != null){
				fileTable[fileDescriptor].close();
				fileTable[fileDescriptor] = null;
				return fileDescriptor;
			} 
		} 
		return -1;
	}

	/**
	 * Handle the unlink() system call.
	 * @param fileDescriptor file descriptor
	 * @return 0 on success, -1 on error
	 */
	protected int handleUnlink(int namePointer) {
		String fileName = this.readVirtualMemoryString(namePointer, MAX_SYSCALL_ARG_LENGTH);
		if(fileName == null) return -1;
		if(ThreadedKernel.fileSystem.remove(fileName))return 0;
		return -1;
	}

	/**
 	 * Handle the exec() system call.
 	 * @param fileDescrptior file descriptor
         * @param argc number of arguments to pass
         * @param argvPtr the pointer to array of null terminated strings containing arguments
         * @return PID of child process, or -1 on failure
 	 * Return the PID of the child process or -1 on failure
 	 */ 
	protected int handleExecute(int fileNamePtr, int argc, int argvPtr){
		if(fileNamePtr < 0 || argc < 0 || argv < 0) return -1;
		//Load the executeble file name
		String executableName = readVirtualMemoryString(fileNamePtr,MAX_SYSCALL_ARG_LENGTH);
		if(executableName == null||!executableName.endsWith(".coff")) return -1;
		//Load the corresponding arguments
		String[] arguments = new String[argc];
		byte argvPtrArray[] = new byte[argc * 4]; //Array of pointer to arguments
		if(readVirtualMemory(argvPtr, argvPtrArray) != argc*4) return -1;
		for(int i = 0; i < argc; i++){
			int pointer = Lib.bytesToInt(argvPtrArray, i*4);
			arguments[i] = readVirtualMemoryString(pointer, MAX_SYSCALL_ARG_LENGTH);
		}
		//Create new child process to run this executable
		UserProcess child = newUserProcess();
		child.parent = this;
		if(child.execute(executableName, arguments)){
			childProcesses.put(child.PID, child);
			return child.PID;
		}
		return -1;
	} 


	/**
 	 * Wait for child process to exit and tranfer exit value
 	 * @param pid pid of process to join on
 	 * @param statusPtr pointer that stores a process's exit status
 	 * @return 0 if child exited cleanly, -1 on all other cases
 	 */
	protected int handleJoin(int pid, int statusPtr){
		if(pid < 1 || statusPtr < 0) return -1;
		UserProcess child = childProcesses.get(pid);
		if(child == null) return -1;
		child.thread.join();
		childProcesses.remove(pid);
		if(child.status!= -999){
			byte exitStatus[] = new byte[4];
			exitStatus = Lib.bytesFromInt(child.status);
			int byteTransferred = writeVirtualMemory(statusPtr, exitStatus);
			if (byteTransferred == 4)return 0;
		}
		return -1;
	}
	

	/**
 	 * Handle exit and cleanup of a process
 	 * @param status the exit status of current process
 	 */ 
	protected void handleExit(int status){
		System.out.println("Handle exit!");
		if(childProcesses!=null){
			for(UserProcess child: childProcesses.values()){
				child.status = status;
			}
		}
		//close all opened files
		for(int i = 0; i < fileTable.length;i++){
			handleClose(i);
		}
		this.unloadSections();
		if(PID== ROOT){
			Kernel.kernel.terminate();
		}else{
			KThread.finish();
			Lib.assertNotReached();
		}
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		//System.out.println("Current system call is " + syscall);
		//System.out.println("a0 is " + a0 + " a1 is " + a1 + " a2 is " + a2 + " a3 is " + a3);
		//System.out.println("Enter handle exception " + syscall);
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0,a1,a2);
		case syscallWrite:
			return handleWrite(a0,a1,a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExec:
			return handleExecute(a0,a1,a2);
		case syscallJoin:
			return handleJoin(a0,a1);
		case syscallExit:
			handleExit(a0);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();
		//System.out.println("Enter handleException " + cause);
		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
		//	System.out.println("Finished handleException() " + cause);
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			handleExit(-1);
			Lib.assertNotReached("Unexpected exception");
		}
	}
	

	protected TranslationEntry getPageFromPageTable(int vaddr){
		if(validAddress(vaddr)) return pageTable[UserKernel.vpn(vaddr)];
		return null;
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** Parent children process hashmap*/
	protected UserProcess parent;
	private HashMap<Integer, UserProcess> childProcesses;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final int MAX_SYSCALL_ARG_LENGTH = 256;
        private static final int ROOT = 0;
        protected static int currentPID = ROOT;
        protected int PID;//Process id for the current process
	protected int status;//The status of current process
        protected OpenFile[] fileTable;
	private UThread thread;
}
