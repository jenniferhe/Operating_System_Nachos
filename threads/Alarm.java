package nachos.threads;

import nachos.machine.*;
import java.util.PriorityQueue;
import java.util.LinkedList;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean intStatus = Machine.interrupt().disable();
		while(!waitQueue.isEmpty()){
			//If the waitQueue is not empty, we want to check the first item in the queue
			//If current time did not past the thread's wake time, 
			//then we know all the threads in the queue hasn't reach their wake time(because of the priority queue)
			//So we breake the while loop
			//Else, we keep waking up the first item in the priority queue
			if(waitQueue.peek().getWakeTime() > Machine.timer().getTime()){
				//System.out.println("No threads to be wake up at this moment");
                                break;
			}else{
				waitQueue.poll().getThread().ready();
                                //System.out.println("Wake up a thread");
			}
		}
		Machine.interrupt().restore(intStatus);
		KThread.currentThread().yield();	
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		boolean intStatus = Machine.interrupt().disable();
		if(x != 0){//Not necessary to put the thread to sleep if x = 0
			//Add the waiter to the waitQueue and then put the thread to sleep	
			waitQueue.add(new Waiter(KThread.currentThread(), Machine.timer().getTime() + x)); 
			//System.out.println("Put " +  KThread.currentThread() + " to sleep for " + x + " milliseconds");
			KThread.currentThread().sleep();
		}
		Machine.interrupt().restore(intStatus);
	}
	
	//Create a priorityQueue of Waiter storing all the threads together with its wakeTime
	//Those waiters will be sorted based on wake time
	private PriorityQueue<Waiter> waitQueue = new PriorityQueue<Waiter>();

	private class Waiter implements Comparable<Waiter>{
		private KThread _thread;
		private long _wakeTime;
		private Waiter(KThread thread, long wakeTime){
			_thread = thread;
			_wakeTime = wakeTime;
		}
		public KThread getThread(){
			return _thread;
		}
		public long getWakeTime(){
			return _wakeTime;
		}
		public void setWakeTime(long time){
			_wakeTime = time;
		}
		@Override
		public int compareTo(Waiter other){
			if(_wakeTime < other.getWakeTime()){
				return -1;
			}else if(_wakeTime > other.getWakeTime()){
				return 1;
			}else{
				return 0;
			}
		}
	}

}
