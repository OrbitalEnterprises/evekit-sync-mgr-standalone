package enterprises.orbital.evekit.sync.snapshot;

import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.account.EveKitUserNotification;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.snapshot.SnapshotScheduler;
import enterprises.orbital.evekit.sync.ControllerEvent;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

// Create a daily snapshot of the given account
public class SnapshotEvent extends ControllerEvent implements Runnable {
  public static final Logger log = Logger.getLogger(SnapshotEvent.class.getName());
  public static final String USER_SETTING_NOTIFY_SNAPSHOT = "showSnapshotNotifications";
  private SynchronizedEveAccount toSnapshot;
  private long maxDelay;

  SnapshotEvent(SynchronizedEveAccount d) {
    toSnapshot = d;
    maxDelay = PersistentProperty.getLongPropertyWithFallback(SnapshotEventScheduler.PROP_MAX_DELAY_SNAPSHOT,
                                                              SnapshotEventScheduler.DEF_MAX_DELAY_SNAPSHOT);
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
    super.run();
    try {
      SnapshotScheduler.generateAccountSnapshot(toSnapshot, dispatchTime);
      boolean notify = PersistentProperty.getBooleanPropertyWithFallback(toSnapshot, USER_SETTING_NOTIFY_SNAPSHOT,
                                                                         true);
      if (notify) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:SS");
        String asof = fmt.format(new Date(dispatchTime));
        EveKitUserNotification.makeNote(toSnapshot.getUserAccount(),
                                        "Snapshot for synchronized account \"" + toSnapshot.getName() + "\" as of "
                                            + asof + " is now ready. You can download this snapshot from the account page (cloud icon)." +
                                            "  Don't want to be notified on snapshots?  You can disable notifications in your user " +
                                            "settings (gear icon).");
      }
    } catch (IOException e) {
      log.log(Level.WARNING, "error generating snapshot for: " + toSnapshot, e);
    }
  }

}