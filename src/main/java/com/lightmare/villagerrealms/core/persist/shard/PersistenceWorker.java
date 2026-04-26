package com.lightmare.villagerrealms.core.persist.shard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Single background thread that drains a queue of (path, bytes) snapshots and
 * writes them atomically. Snapshots are produced on the main server thread; the
 * worker only owns disk I/O.
 *
 * If multiple writes target the same path before the worker drains them, only
 * the latest is kept (older queued snapshots for that path are superseded).
 */
public final class PersistenceWorker {

    private static final Logger LOG = LoggerFactory.getLogger(PersistenceWorker.class);

    private record Job(Path target, byte[] bytes) {}

    private final LinkedBlockingQueue<Job> queue = new LinkedBlockingQueue<>();
    private final java.util.concurrent.ConcurrentHashMap<Path, byte[]> latest =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final Thread thread;
    private volatile boolean running = true;

    public PersistenceWorker(String name) {
        this.thread = new Thread(this::loop, name);
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    public void submit(Path target, byte[] bytes) {
        latest.put(target, bytes);
        queue.offer(new Job(target, bytes));
    }

    public void shutdown(long drainTimeoutMillis) {
        running = false;
        thread.interrupt();
        try {
            thread.join(drainTimeoutMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void loop() {
        while (running || !queue.isEmpty()) {
            Job job;
            try {
                job = queue.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                if (!running) break;
                continue;
            }
            if (job == null) continue;

            byte[] current = latest.get(job.target);
            if (current != job.bytes) continue;

            try {
                AtomicFile.write(job.target, job.bytes);
                latest.remove(job.target, job.bytes);
            } catch (IOException e) {
                LOG.error("Failed to write shard {}: {}", job.target, e.toString(), e);
            }
        }
    }

    public boolean isIdle() {
        return queue.isEmpty();
    }

    public void awaitIdle(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!queue.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }
}
