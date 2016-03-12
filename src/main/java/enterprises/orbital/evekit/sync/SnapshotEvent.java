package enterprises.orbital.evekit.sync;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.snapshot.SnapshotScheduler;

// Create a daily snapshot of the given account
public class SnapshotEvent extends ControllerEvent implements Runnable {
  public static final Logger    log = Logger.getLogger(SnapshotEvent.class.getName());
  public SynchronizedEveAccount toSnapshot;
  public long                   maxDelay;

  public SnapshotEvent(Future<?> tracker, SynchronizedEveAccount d) {
    super(Long.MAX_VALUE, tracker);
    toSnapshot = d;
    maxDelay = PersistentProperty.getLongPropertyWithFallback(SnapshotEventScheduler.PROP_MAX_DELAY_SNAPSHOT, SnapshotEventScheduler.DEF_MAX_DELAY_SNAPSHOT);
  }

  @Override
  public long maxDelayTime() {
    return maxDelay;
  }

  @Override
  public String toString() {
    return "SnapshotEvent [toSnapshot=" + toSnapshot + ", maxDelay=" + maxDelay + ", dispatchTime=" + dispatchTime + ", tracker=" + tracker + "]";
  }

  @Override
  public void run() {
    dispatchTime = OrbitalProperties.getCurrentTime();
    try {
      SnapshotScheduler.generateAccountSnapshot(toSnapshot, dispatchTime);
    } catch (IOException e) {
      log.log(Level.WARNING, "error generating snapshot for: " + toSnapshot, e);
    }
  }

}