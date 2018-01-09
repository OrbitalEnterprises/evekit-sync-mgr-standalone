package enterprises.orbital.evekit.sync.account;

import enterprises.orbital.evekit.model.ESIAccountSynchronizationHandler;
import enterprises.orbital.evekit.model.ESIRefSyncEndpoint;
import enterprises.orbital.evekit.model.ESIRefSynchronizationHandler;
import enterprises.orbital.evekit.model.ESISyncEndpoint;
import enterprises.orbital.evekit.sync.ControllerEvent;
import enterprises.orbital.evekit.sync.ref.RefSyncRefClientProvider;

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
    handler.synch(new AccountSyncClientProvider(scheduler));
    log.fine("Execution complete: " + toString());
  }

}