package carlos.webcrawler;
import java.util.Arrays;

public class ThreadPoolHandler {
    Thread[] threadPool;
    volatile boolean stop = false;
    Runnable startTask;
    ThreadPoolHandler (int n) {
        threadPool = new Thread[n];
    }

    ThreadPoolHandler setTask(Runnable startTask) {
        this.startTask = startTask;
        return this;
    }

    void close() {
        stop = true;
    }

    private boolean hasATask() {
        return startTask != null;
    }

    synchronized void start() {
        if(!hasATask()) throw new IllegalStateException("Tasks are not set");
        stop = false;
        for (int i = 0; i < threadPool.length; i++) {
            threadPool[i] = new Thread(startTask);
            threadPool[i].start();
        }
    }

    synchronized boolean allTerminated() {
        return hasATask() && Arrays.stream(threadPool).noneMatch(Thread::isAlive);
    }

    int size() {
        return threadPool.length;
    }

    public boolean shouldStop() {
        return stop;
    }

    public synchronized boolean isLastThread() {
        return Arrays.stream(threadPool).filter(Thread::isAlive).count() == 1;
    }
}
