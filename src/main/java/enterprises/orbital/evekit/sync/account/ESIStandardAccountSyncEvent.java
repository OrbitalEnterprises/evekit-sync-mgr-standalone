package enterprises.orbital.evekit.sync.account;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.ESIAccountSynchronizationHandler;
import enterprises.orbital.evekit.model.ESISyncEndpoint;
import enterprises.orbital.evekit.sync.ControllerEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ESIStandardAccountSyncEvent extends ControllerEvent implements Runnable {
  public static final Logger log = Logger.getLogger(ESIStandardAccountSyncEvent.class.getName());
  protected ESISyncEndpoint endpoint;
  protected ESIAccountSynchronizationHandler handler;
  protected ExecutorService scheduler;

  public ESIStandardAccountSyncEvent(ESISyncEndpoint endpoint,
                                     ESIAccountSynchronizationHandler handler,
                                     ExecutorService scheduler) {
    this.endpoint = endpoint;
    this.handler = handler;
    this.scheduler = scheduler;
  }

  @Override
  public long maxDelayTime() {
    return handler.maxDelay();
  }

  @Override
  public String toString() {
    return "ESIStandardAccountSyncEvent{" +
        "endpoint=" + endpoint +
        ", handler=" + handler +
        ", scheduler=" + scheduler +
        '}';
  }

  public ESISyncEndpoint getEndpoint() {
    return endpoint;
  }

  public ESIAccountSynchronizationHandler getHandler() {
    return handler;
  }

  public ExecutorService getScheduler() {
    return scheduler;
  }

  @Override
  public void run() {
    log.fine("Starting execution: " + toString());
    super.run();
    // Sync activities on a per-account basis to prevent certain data races.
    // Note that we bound the time we wait for the lock to avoid starving
    // other tasks queued in the thread pool.
    ReentrantLock lck = SynchronizedEveAccount.getSyncAccountLock(handler.account());
    int maxDelay = ThreadLocalRandom.current()
                                    .nextInt(30);
    try {
      // Wait no less than 30 and no more than 60 seconds for the lock.  If we fail
      // to obtain the lock, then yield this thread and wait for account checker to
      // reschedule us.
      if (!lck.tryLock(30 + maxDelay, TimeUnit.SECONDS)) {
        log.fine("Failed to obtain lock, yielding thread: " + toString());
        return;
      }
      handler.synch(new AccountSyncClientProvider(scheduler));
    } catch (InterruptedException e) {
      log.log(Level.WARNING, "Interrupted while waiting for lock", e);
    } finally {
      // Must verify we hold the lock first since the case where we fail to
      // obtain the lock is included in the same try block.
      if (lck.isHeldByCurrentThread()) lck.unlock();
    }
    log.fine("Execution complete: " + toString());
  }

}