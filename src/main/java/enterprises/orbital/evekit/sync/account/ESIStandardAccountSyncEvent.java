package enterprises.orbital.evekit.sync.account;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.ESIAccountSynchronizationHandler;
import enterprises.orbital.evekit.model.ESIRefSyncEndpoint;
import enterprises.orbital.evekit.model.ESIRefSynchronizationHandler;
import enterprises.orbital.evekit.model.ESISyncEndpoint;
import enterprises.orbital.evekit.sync.ControllerEvent;
import enterprises.orbital.evekit.sync.ref.RefSyncRefClientProvider;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
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

  // Global map of locks to protect against races on the same (endpoint, account) pair
  protected static Map<Pair<ESISyncEndpoint, Long>, Object> handlerLock = new HashMap<>();

  protected Object getHandlerLock(ESISyncEndpoint ep, SynchronizedEveAccount acct) {
    synchronized (handlerLock) {
      Object lck = handlerLock.get(Pair.of(ep, acct.getAid()));
      if (lck == null) {
        lck = new Long(OrbitalProperties.getCurrentTime());
        handlerLock.put(Pair.of(ep, acct.getAid()), lck);
      }
      return lck;
    }
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
    // Prevent races between two threads synchronizing on the same account and endpoint.
    // This can lead to duplicate data since model uniqueness is not guaranteed (evolves over time).
    synchronized (getHandlerLock(handler.endpoint(), handler.account())) {
      handler.synch(new AccountSyncClientProvider(scheduler));
    }
    log.fine("Execution complete: " + toString());
  }

}