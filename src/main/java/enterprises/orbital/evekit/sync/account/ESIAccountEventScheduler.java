package enterprises.orbital.evekit.sync.account;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.model.ESIEndpointSyncTracker;
import enterprises.orbital.evekit.model.ESISyncEndpoint;
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

  public ESIAccountEventScheduler() {
    super();
    dispatch = dispatchAlias = Executors.newScheduledThreadPool((int) OrbitalProperties.getLongGlobalProperty(PROP_MAX_THREADS_ESI, DEF_MAX_THREADS_ESI));
  }

  private void dispatchRefCheckSchedule() {
    AccountCheckScheduleEvent accountChecker = new AccountCheckScheduleEvent(this, dispatchAlias);
    accountChecker.setTracker(dispatch.submit(accountChecker));
    pending.add(accountChecker);
  }

  @Override
  public boolean fillPending() {
    // This should normally only be called once.  Our main task here is to launch the CheckSchedule event.
    // The CheckSchedule event should automatically queue itself when complete.  If that fails, this method
    // will be called again to queue a new event.

    // Dispatch check schedule for reference data
    synchronized (pending) {
      dispatchRefCheckSchedule();
    }

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
      dispatchRefCheckSchedule();
    }
  }
}
