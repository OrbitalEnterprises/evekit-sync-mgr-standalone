package enterprises.orbital.evekit.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;

public class SyncEventScheduler extends EventScheduler {
  public static final Logger log                      = Logger.getLogger(SyncEventScheduler.class.getName());
  // Configuration for each scheduler type
  public static final String PROP_MAX_THREADS_SYNC    = "enterprises.orbital.evekit.sync_mgr.max_threads.sync";
  public static final int    DEF_MAX_THREADS_SYNC     = 5;
  // Max delay properties for various event types
  public static final String PROP_MAX_DELAY_SYNC      = "enterprises.orbital.evekit.sync_mgr.max_delay.sync";
  public static final long   DEF_MAX_DELAY_SYNC       = TimeUnit.MILLISECONDS.convert(30, TimeUnit.MINUTES);
  // Delay between checking for accounts we can sync
  public static final String PROP_SYNC_CHECK_INTERVAL = "enterprises.orbital.evekit.sync_mgr.sync_check_interval";
  public static final long   DEF_SYNC_CHECK_INTERVAL  = TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);

  public SyncEventScheduler() {
    super();
    dispatch = Executors.newFixedThreadPool((int) OrbitalProperties.getLongGlobalProperty(PROP_MAX_THREADS_SYNC, DEF_MAX_THREADS_SYNC));
  }

  @Override
  public boolean fillPending() {
    // Scan for unfinished sync trackers belonging to non-active or marked for delete accounts. We need to finish these trackers directly since the scheduler
    // will skip them.
    List<SyncTracker> trackerCheck = new ArrayList<SyncTracker>();
    trackerCheck.addAll(CapsuleerSyncTracker.getAllUnfinishedTrackers());
    trackerCheck.addAll(CorporationSyncTracker.getAllUnfinishedTrackers());
    List<SynchronizedEveAccount> scheduled = new ArrayList<SynchronizedEveAccount>();
    int finishedTrackers = 0;
    int dispatchedAccounts = 0;
    for (SyncTracker next : trackerCheck) {
      SynchronizedEveAccount owner = next.getOwner();
      if (owner.getMarkedForDelete() > 0) {
        // Marked for delete. This tracker will never be finished by the synchronizer so finish it here.
        SyncTracker.finishTracker(next);
        finishedTrackers++;
        continue;
      }
      EveKitUserAccount user = owner.getUserAccount();
      if (!user.isActive()) {
        // User inactive. This tracker will never be finished by the synchronizer so finish it here.
        SyncTracker.finishTracker(next);
        finishedTrackers++;
        continue;
      }
      // Schedule this account to attempt to finish the tracker. This also handles non-auto sync accounts.
      if (!scheduled.contains(owner)) {
        SyncEvent syncEvent = new SyncEvent(null, owner);
        syncEvent.tracker = dispatch.submit(syncEvent);
        dispatchedAccounts++;
        pending.add(syncEvent);
        scheduled.add(owner);
      }
    }
    // Schedule all active and not-deleted accounts. Sync will only proceed for accounts which are eligible.
    try {
      for (SynchronizedEveAccount next : SynchronizedEveAccount.getAllAutoSyncAccounts(false)) {
        // Skip accounts we've already considered
        if (scheduled.contains(next)) continue;
        // Skip inactive accounts here
        if (!next.getUserAccount()
                 .isActive()) continue;
        // Otherwise, schedule a sync attempt
        SyncEvent syncEvent = new SyncEvent(null, next);
        syncEvent.tracker = dispatch.submit(syncEvent);
        dispatchedAccounts++;
        pending.add(syncEvent);
      }
    } catch (IOException e) {
      // DB error retrieving auto sync account list, log and continue
      log.log(Level.SEVERE, "Failed to retrieve list of auto sync accounts", e);
    }
    // If we didn't dispatch any events, then dispatch a sleep event to delay until the next check
    if (pending.isEmpty()) {
      long delay = OrbitalProperties.getLongGlobalProperty(PROP_SYNC_CHECK_INTERVAL, DEF_SYNC_CHECK_INTERVAL);
      log.info("No events to dispatch, sleeping for " + TimeUnit.MINUTES.convert(delay, TimeUnit.MILLISECONDS) + " minutes");
      SleepEvent sleeper = new SleepEvent(delay);
      sleeper.tracker = dispatch.submit(sleeper);
      pending.add(sleeper);
    } else {
      log.info("Finished " + finishedTrackers + " trackers");
      log.info("Dispatched " + dispatchedAccounts + " accounts");
    }
    return true;
  }

}
