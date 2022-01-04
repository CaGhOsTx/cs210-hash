package carlos.webscraper;

import java.util.LinkedList;
import java.util.List;

final class ThreadHandler {
    private final List<Thread> threadPool;
    private boolean stop = false;
    private Runnable task;
    final int initialSize;
    ThreadHandler(int n) {
        threadPool = new LinkedList<>();
        initialSize = n;
    }

    ThreadHandler setTask(Runnable startTask) {
        this.task = startTask;
        return this;
    }

    void stop() {
        stop = true;
    }

    private boolean hasATask() {
        return task != null;
    }

    void addMoreThreads(int n) {
        allocateThreads(n);
    }

    void decreaseThreadsTo(int n) {
        deallocateThreads(n);
    }

    synchronized void start() {
        if(!hasATask()) throw new IllegalStateException("Task is not set");
        stop = false;
        allocateThreads(initialSize);
    }

    private synchronized void deallocateThreads(int n) {
        while(threadPool.size() > n) {
            stop();
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        stop = false;
    }

    private void allocateThreads(int n) {
        for (int i = threadPool.size(); i < n; i++) {
            threadPool.add(new Thread(task));
            threadPool.get(i).start();
        }
    }

    synchronized boolean allTerminated() {
        return hasATask() && threadPool.stream().noneMatch(Thread::isAlive);
    }

    int size() {
        return threadPool.size();
    }

    boolean shouldStop() {
        return stop;
    }

    synchronized boolean isLastThread() {
        return threadPool.stream().filter(Thread::isAlive).count() == 1;
    }

    synchronized void notifyScraper(WebScraper scraper) {
        scraper.notify();
    }
}
