package nachos.threads;

import nachos.machine.*;
import java.util.Random;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		communicateLock = new Lock();
		speak = new Condition2(communicateLock);
		listen = new Condition2(communicateLock);
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word the integer to transfer.
	 */
	public void speak(int word) {
		communicateLock.acquire();
		//When there is someone speaking or no available waiting listener, 
		//put the speaker to sleep and relinquish the lock
		while(full||waitingListeners == 0)
			speak.sleep();
		//At this point, the current speaker fullfill the precondition and can speak
		communicateWord = word;
		full = true;
		//System.out.format("One thread %s has spoke %s at time %s \n", this, communicateWord, Machine.timer().getTime());
		//Wake up a listener to listen to this word
		listen.wake();
		communicateLock.release();
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		communicateLock.acquire();
		//Change the variable in shared state: waitingListeners and then signal speak to wake up a speaker
		waitingListeners++;
		speak.wake();
		//Allow the speaker to have the lock so that it can speak the word
		//Once the speaker done speaking, it would signal lister to wake up to read the word
		listen.sleep();
		//At this point, the listener is woken up, it must satisfy the condition that there is a speaker already spoke
		//I.e. full is set to true, communicateWord stores the word passed by the speaker
		int word = communicateWord;
		//After the communication, change the shared state variable
		//set the full to false to indicate this speaker-listener communication has finished
		full = false;
		waitingListeners--;
		//Signal the speaker to wake up since the communicateWord is empty
		speak.wake();
		communicateLock.release();
		System.out.println("One communication succeed with the word " + word);
		return word;
	}
	
	private boolean full = false;
	private int communicateWord = 0;
	private Lock communicateLock;
	private Condition2 speak;
	private Condition2 listen;
	private int waitingListeners = 0;
	
	private static class CommunicatorTest implements Runnable {
		CommunicatorTest(int which, Communicator com, boolean speaker, int word, int randomDelay){
			this.which = which;
			this.com = com;
			this.speaker = speaker;
			this.word = word;
			this.randomDelay = randomDelay;
		}
	
		public void run(){
			System.out.format("Thread %s has random delay of %s at time %s \n", this.which, this.randomDelay, Machine.timer().getTime());
			ThreadedKernel.alarm.waitUntil(this.randomDelay);
			if(this.speaker){
				com.speak(this.word);
				System.out.format("Thread %s has spoke %s at time %s \n", this.which, this.word, Machine.timer().getTime());
			}else{
				int word = com.listen();
				System.out.format("Thread %s has listened %s at time %s \n", this.which, word, Machine.timer().getTime());
			}
			currentRunningCommunicator--;
		}
	
		private boolean speaker;
		private int word;
		private Communicator com;
		private int which;
		private int randomDelay;
	}
	
	public static void selfTest(){
		Lib.debug(dbgThread, "Enter Communicator.selfTest");
		Communicator com = new Communicator();
		Random rng = new Random();
			
		//Test 0 with all random delays
		for(int i = 0; i < 5; i++){
			threads[i] = new KThread(new CommunicatorTest(i, com, true, rng.nextInt(50), rng.nextInt(1000)));
			threads[i+5] = new KThread(new CommunicatorTest(i+5, com, false, 0,rng.nextInt(1000)));
		}
		System.out.println("*******Communicator Test 0******");
		for(KThread t: threads){
			t.fork();
		}
		for(KThread t: threads){
			t.join();
		}

		//test 1 with 0 delay
		for(int i = 0; i < 5; i++){
                        threads[i] = new KThread(new CommunicatorTest(i, com, true, rng.nextInt(1000),0));
                        threads[i+5] = new KThread(new CommunicatorTest(i+5, com, false,0,0));
                }
                System.out.println("*******Communicator Test 1****** \n");
                for(KThread t: threads){
                        t.fork();
                }
                for(KThread t: threads){
                        t.join();
                }
		
		//Test 2: 5 listeners run first
		for(int i = 0; i < 5; i++){
                        threads[i] = new KThread(new CommunicatorTest(i, com, false, 0,0));
                        threads[i+5] = new KThread(new CommunicatorTest(i+5, com, true,rng.nextInt(1000),0));
                }
                System.out.println("*******Communicator Test 2 ******\n");
                for(KThread t: threads){
                        t.fork();
                }
                for(KThread t: threads){
                        t.join();
                }
		
		//Test 3: Determinestic test case
		//Threads are created with 500 milisecond ascending delays so that we should have
		// 0 listens to 5, 1 listens to 6, 2 listens to 7, 3 listens to 8, 4 listens to 9
		for(int i = 0; i < 5; i++){
                        threads[i] = new KThread(new CommunicatorTest(i, com, false, 0,500*i));
                        threads[i+5] = new KThread(new CommunicatorTest(i+5, com, true,rng.nextInt(1000),500*(i+5)));
                }
                System.out.println("*******Communicator Test 2 ******\n");
                for(KThread t: threads){
                        t.fork();
                }
                for(KThread t: threads){
                        t.join();
                }

	}
	
	private static final char dbgThread = 't';
	public static KThread[] threads = new KThread[10];
	private static int currentRunningCommunicator = 10;
}
