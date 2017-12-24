package enterprises.orbital.evekit.sync.ref;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.model.ESIRefSyncEndpoint;
import enterprises.orbital.evekit.model.ESIRefSynchronizationHandler;
import enterprises.orbital.evekit.sync.ControllerEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ESIStandardRefSyncEvent extends ControllerEvent implements Runnable {
  public static final Logger log = Logger.getLogger(ESIStandardRefSyncEvent.class.getName());
  protected ESIRefSyncEndpoint endpoint;
  protected ESIRefSynchronizationHandler handler;

  public ESIStandardRefSyncEvent(ESIRefSyncEndpoint endpoint, ESIRefSynchronizationHandler handler) {
    super(OrbitalProperties.getCurrentTime());
    this.endpoint = endpoint;
    this.handler = handler;
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

  @Override
  public void run() {
    log.fine("Starting execution: " + toString());
    dispatchTime = OrbitalProperties.getCurrentTime();
    handler.synch(new RefSyncClientProvider());
    log.fine("Execution complete: " + toString());
  }

}