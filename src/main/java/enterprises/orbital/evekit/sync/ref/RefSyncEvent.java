package enterprises.orbital.evekit.sync.ref;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.model.RefDataSynchronizer;
import enterprises.orbital.evekit.sync.ControllerEvent;

import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

// Schedule synchronization for a reference data synchronizer
public class RefSyncEvent extends ControllerEvent implements Runnable {
  public static final Logger log = Logger.getLogger(RefSyncEvent.class.getName());
  public long                maxDelay;

  public RefSyncEvent() {
    super(Long.MAX_VALUE);
    maxDelay = PersistentProperty.getLongPropertyWithFallback(RefSyncEventScheduler.PROP_MAX_DELAY_SYNC, RefSyncEventScheduler.DEF_MAX_DELAY_SYNC);
  }

  @Override
  public long maxDelayTime() {
    return maxDelay;
  }

  @Override
  public String toString() {
    return "RefSyncEvent [dispatchTime=" + dispatchTime + ", tracker=" + tracker + "]";
  }

  @Override
  public void run() {
    log.fine("Starting execution: " + toString());
    dispatchTime = OrbitalProperties.getCurrentTime();
    RefDataSynchronizer sync = new RefDataSynchronizer();
    try {
      sync.synchronize();
    } catch (Exception e) {
      log.log(Level.SEVERE, "Sync failure, exiting:", e);
    }
  }

}