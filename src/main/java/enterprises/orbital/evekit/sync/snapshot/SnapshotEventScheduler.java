package enterprises.orbital.evekit.sync.snapshot;

import java.io.IOException;
import java.text.ParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.snapshot.SnapshotScheduler;
import enterprises.orbital.evekit.sync.EventScheduler;
import enterprises.orbital.evekit.sync.SleepEvent;

public class SnapshotEventScheduler extends EventScheduler {
  public static final Logger log                          = Logger.getLogger(SnapshotEventScheduler.class.getName());
  // Configuration for each scheduler type
  private static final String PROP_MAX_THREADS_SNAPSHOT    = "enterprises.orbital.evekit.sync_mgr.max_threads.snapshot";
  private static final int    DEF_MAX_THREADS_SNAPSHOT     = 2;
  // Max delay properties for various event types
  public static final String PROP_MAX_DELAY_SNAPSHOT      = "enterprises.orbital.evekit.sync_mgr.max_delay.snapshot";
  public static final long   DEF_MAX_DELAY_SNAPSHOT       = TimeUnit.MILLISECONDS.convert(2, TimeUnit.HOURS);
  // Delay between checking for accounts we can snapshot
  private static final String PROP_SNAPSHOT_CHECK_INTERVAL = "enterprises.orbital.evekit.sync_mgr.snapshot_check_interval";
  private static final long   DEF_SNAPSHOT_CHECK_INTERVAL  = TimeUnit.MILLISECONDS.convert(12, TimeUnit.HOURS);
  // Delay between snapshots for accounts
  private static final String PROP_SNAPSHOT_INTERVAL       = "enterprises.orbital.evekit.snapshot.interval";
  private static final long   DEF_SNAPSHOT_INTERVAL        = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);

  public SnapshotEventScheduler() {
    super();
    dispatch = Executors.newFixedThreadPool((int) OrbitalProperties.getLongGlobalProperty(PROP_MAX_THREADS_SNAPSHOT, DEF_MAX_THREADS_SNAPSHOT));
  }

  @SuppressWarnings("Duplicates")
  @Override
  public boolean fillPending() {
    // Scan for accounts eligible for snapshot
    long separation = OrbitalProperties.getLongGlobalProperty(PROP_SNAPSHOT_INTERVAL, DEF_SNAPSHOT_INTERVAL);
    long now = OrbitalProperties.getCurrentTime();
    try {
      for (EveKitUserAccount user : EveKitUserAccount.getAllAccounts()) {
        if (user.isActive()) {
          try {
            for (SynchronizedEveAccount next : SynchronizedEveAccount.getAllAccounts(user, false)) {
              try {
                long last = SnapshotScheduler.lastSnapshotTime(next);
                if (now - last > separation) {
                  SnapshotEvent snapshotEvent = new SnapshotEvent(next);
                  snapshotEvent.setTracker(dispatch.submit(snapshotEvent));
                  pending.add(snapshotEvent);
                }
              } catch (ParseException e) {
                log.log(Level.WARNING, "Failed to check snapshot eligibility, skipping: " + next, e);
              }
            }
          } catch (IOException e) {
            // DB error retrieving accounts for current user, log it and continue
            log.log(Level.SEVERE, "Failed to retrieve sync accounts for user " + user, e);
          }
        }
      }
    } catch (IOException e) {
      // DB error retrieving all accounts, log it and continue
      log.log(Level.SEVERE, "Failed to retrieve all user accounts", e);
    }
    // If we didn't dispatch any events, then dispatch a sleep event to delay until the next check
    if (pending.isEmpty()) {
      long delay = OrbitalProperties.getLongGlobalProperty(PROP_SNAPSHOT_CHECK_INTERVAL, DEF_SNAPSHOT_CHECK_INTERVAL);
      log.info("No events to dispatch, sleeping for " + TimeUnit.MINUTES.convert(delay, TimeUnit.MILLISECONDS) + " minutes");
      SleepEvent sleeper = new SleepEvent(delay);
      sleeper.setTracker(dispatch.submit(sleeper));
      pending.add(sleeper);
    } else {
      log.info("Dispatched " + pending.size() + " snapshot tasks");
    }
    return true;
  }

}
