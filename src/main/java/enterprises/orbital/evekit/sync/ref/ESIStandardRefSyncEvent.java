package enterprises.orbital.evekit.sync.ref;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.model.ESIRefSyncEndpoint;
import enterprises.orbital.evekit.model.ESIRefSynchronizationHandler;
import enterprises.orbital.evekit.sync.ControllerEvent;

import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ESIStandardRefSyncEvent extends ControllerEvent implements Runnable {
  public static final Logger log = Logger.getLogger(ESIStandardRefSyncEvent.class.getName());
  protected ESIRefSyncEndpoint endpoint;
  protected ESIRefSynchronizationHandler handler;
  protected ExecutorService scheduler;

  public ESIStandardRefSyncEvent(ESIRefSyncEndpoint endpoint, ESIRefSynchronizationHandler handler,
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
    return "ESIStandardRefSyncEvent{" +
        "handler=" + handler +
        ", dispatchTime=" + dispatchTime +
        ", tracker=" + tracker +
        '}';
  }

  public ESIRefSyncEndpoint getEndpoint() {
    return endpoint;
  }

  public ESIRefSynchronizationHandler getHandler() {
    return handler;
  }

  public ExecutorService getScheduler() {
    return scheduler;
  }

  @Override
  public void run() {
    log.fine("Starting execution: " + toString());
    super.run();
    handler.synch(new RefSyncClientProvider(scheduler));
    log.fine("Execution complete: " + toString());
  }

}