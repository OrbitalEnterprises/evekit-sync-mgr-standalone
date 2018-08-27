package enterprises.orbital.evekit.sync.account;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.EveKitUserNotification;
import enterprises.orbital.evekit.sync.ControllerEvent;
import enterprises.orbital.evekit.sync.EventScheduler;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodic event to check for any undeleted notifications older than one month.  These notifications
 * are automatically marked as deleted.
 */
public class CheckExpiredNotificationEvent extends ControllerEvent implements Runnable {
  public static final Logger log = Logger.getLogger(CheckExpiredNotificationEvent.class.getName());

  private static final String PROP_EXPIRED_NOTE_DELAY = "enterprises.orbital.evekit.sync_mgr.expired_note_delay";
  private static final long DEF_EXPIRED_NOTE_DELAY = TimeUnit.MILLISECONDS.convert(8, TimeUnit.HOURS);

  private EventScheduler scheduler;
  private ScheduledExecutorService taskScheduler;

  CheckExpiredNotificationEvent(EventScheduler scheduler, ScheduledExecutorService taskScheduler) {
    this.scheduler = scheduler;
    this.taskScheduler = taskScheduler;
  }

  @Override
  public long maxDelayTime() {
    return TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);
  }

  @Override
  public String toString() {
    return "CheckExpiredNotificationEvent{" +
        "scheduler=" + scheduler +
        ", taskScheduler=" + taskScheduler +
        '}';
  }

  @Override
  public void run() {
    log.fine("Starting execution: " + toString());
    super.run();
    // Iterate over all user accounts, checking for undeleted notifications older than one month
    try {
      long expiry = OrbitalProperties.getCurrentTime() - TimeUnit.MILLISECONDS.convert(30, TimeUnit.DAYS);
      for (EveKitUserAccount next : EveKitUserAccount.getAllAccounts()) {
        try {
          for (EveKitUserNotification note : EveKitUserNotification.getAllNotes(next)) {
            if (note.getNoteTime() < expiry) {
              EveKitUserNotification.markNoteDeleted(next, note.getNid());
            }
          }
        } catch (IOException e) {
          // Skip this user, but log
          log.log(Level.WARNING, "Failed to retrieve notifications for user " + next + ", skipping check", e);
        }
      }
    } catch (IOException e) {
      // Give up if we can't retrieve the list of user accounts
      log.log(Level.WARNING, "Failed to retrieve list of user accounts, skipping check", e);
    }

    // Schedule next check.  Account scheduler will ensure one of these events always exists.
    long delay = OrbitalProperties.getLongGlobalProperty(PROP_EXPIRED_NOTE_DELAY, DEF_EXPIRED_NOTE_DELAY);
    log.fine("Scheduling event to occur in " + delay + " milliseconds");
    ControllerEvent ev = new CheckExpiredNotificationEvent(scheduler, taskScheduler);
    ev.setTracker(taskScheduler.schedule(ev, delay, TimeUnit.MILLISECONDS));
    scheduler.pending.add(ev);

    log.fine("Execution complete: " + toString());
  }

}