package enterprises.orbital.evekit.sync.account;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.sync.ControllerEvent;
import enterprises.orbital.evekit.sync.EventScheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

/**
 * Schedule synchronization of ESI endpoints for synchronized account data.
 */
public class ESIAccountEventScheduler extends EventScheduler {
  public static final Logger log = Logger.getLogger(ESIAccountEventScheduler.class.getName());
  // Configuration for each scheduler type
  private static final String PROP_MAX_THREADS_ESI = "enterprises.orbital.evekit.account_sync_mgr.max_threads.esi";
  private static final int DEF_MAX_THREADS_ESI = 10;

  // Alias for thread pool executor which exposes scheduling classes
  private ScheduledExecutorService dispatchAlias;

  // Run the check schedule event on a separate dedicated dispatcher.  This ensures that we are never starved
  // out by volume on the sync thread pool scheduler.
  private ScheduledExecutorService checkService = Executors.newSingleThreadScheduledExecutor();

  public ESIAccountEventScheduler() {
    super();
    dispatch = dispatchAlias = Executors.newScheduledThreadPool(
        (int) OrbitalProperties.getLongGlobalProperty(PROP_MAX_THREADS_ESI, DEF_MAX_THREADS_ESI));
  }

  private void dispatchAccountCheckSchedule() {
    AccountCheckScheduleEvent accountChecker = new AccountCheckScheduleEvent(this, dispatchAlias, checkService);
    accountChecker.setTracker(checkService.submit(accountChecker));
    pending.add(accountChecker);
  }

  @Override
  public boolean fillPending() {
    // The check schedule event handles populating the queue.  If the queue is empty, then we likley
    // need a status check.
    statusCheck();

    return true;
  }

  @Override
  public void statusCheck() {
    // Make sure a check schedule event is still in the pending queue and ready to run.  If it died for some
    // reason, then add it back in.
    synchronized (pending) {
      for (ControllerEvent next : pending) {
        if (next instanceof AccountCheckScheduleEvent &&
            !next.getTracker()
                 .isDone())
          // Event already scheduled and not yet run
          return;
      }

      // We end up here in one of two cases:
      //
      // 1. no check schedule event is in the pending queue
      // 2. there IS a check schedule event, but it's done and no new event has been queued yet
      //
      // In either case, we need to add a new checker for liveness
      dispatchAccountCheckSchedule();
    }
  }
}
