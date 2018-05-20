package enterprises.orbital.evekit.sync.account;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.ESIAccountSynchronizationHandler;
import enterprises.orbital.evekit.model.ESISyncEndpoint;
import enterprises.orbital.evekit.sync.ControllerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
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
    // For fairness, and to prevent certain data races, serialize sync activities on a
    // per-sync account basis.  This doesn't really guarantee fairness if a single account
    // has many active threads.  Fortunately, this still works pretty well since sync
    // times for endpoints are reasonably diverse.
    ReentrantLock lck = SynchronizedEveAccount.getSyncAccountLock(handler.account());
    lck.lock();
    try {
      handler.synch(new AccountSyncClientProvider(scheduler));
    } finally {
      lck.unlock();
    }
    log.fine("Execution complete: " + toString());
  }

}