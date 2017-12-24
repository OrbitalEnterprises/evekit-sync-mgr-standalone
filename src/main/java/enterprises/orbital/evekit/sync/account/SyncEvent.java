package enterprises.orbital.evekit.sync.account;

import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.AbstractSynchronizer;
import enterprises.orbital.evekit.model.CapsuleerSynchronizer;
import enterprises.orbital.evekit.model.CorporationSynchronizer;
import enterprises.orbital.evekit.sync.ControllerEvent;

// Schedule synchronization for the given account
public class SyncEvent extends ControllerEvent implements Runnable {
  public static final Logger    log = Logger.getLogger(SyncEvent.class.getName());
  public SynchronizedEveAccount toSync;
  public long                   maxDelay;

  public SyncEvent(SynchronizedEveAccount d) {
    super(Long.MAX_VALUE);
    toSync = d;
    maxDelay = PersistentProperty.getLongPropertyWithFallback(SyncEventScheduler.PROP_MAX_DELAY_SYNC, SyncEventScheduler.DEF_MAX_DELAY_SYNC);
  }

  @Override
  public long maxDelayTime() {
    return maxDelay;
  }

  @Override
  public String toString() {
    return "SyncEvent [toSync=" + toSync + ", dispatchTime=" + dispatchTime + ", tracker=" + tracker + "]";
  }

  @Override
  public void run() {
    log.fine("Starting execution: " + toString());
    dispatchTime = OrbitalProperties.getCurrentTime();
    AbstractSynchronizer sync = toSync.isCharacterType() ? new CapsuleerSynchronizer() : new CorporationSynchronizer();
    try {
      sync.synchronize(toSync);
    } catch (Exception e) {
      log.log(Level.SEVERE, "Sync failure, exiting:", e);
    }
  }

}