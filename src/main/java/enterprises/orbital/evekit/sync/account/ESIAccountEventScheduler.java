package enterprises.orbital.evekit.sync.account;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.sync.ControllerEvent;
import enterprises.orbital.evekit.sync.EventScheduler;

import java.util.HashMap;
import java.util.Map;
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
  // Choose among different scheduling regimes
  private static final String REGIME_SHARED = "shared";
  private static final String REGIME_DEDICATED = "dedicated";
  private static final String PROP_SCHEDULING_REGIME = "enterprises.orbital.evekit.account_sync_mgr.sched_regime";
  private static final String DEF_SCHEDULING_REGIME = REGIME_SHARED;

  /**
   * Scheduler interface to be used to schedule sync actions.
   */
  public interface SyncActionScheduler {
    /**
     * Return the appropriate ScheduledExecutorService for the given sync account.
     *
     * @param account the account for which a scheduler is requested.  If null, then
     *                return a default scheduler.
     * @return the appropriate ScheduledExecutorService
     */
    ScheduledExecutorService getScheduler(SynchronizedEveAccount account);
  }

  // Run the check schedule event on a separate dedicated dispatcher.  This ensures that we are never starved
  // out by volume on the sync thread pool scheduler.
  private ScheduledExecutorService checkService = Executors.newSingleThreadScheduledExecutor();

  // Chosen scheduling regime instance
  private SyncActionScheduler schedulingRegime;

  public ESIAccountEventScheduler() {
    super();
    String regime = OrbitalProperties.getGlobalProperty(PROP_SCHEDULING_REGIME, DEF_SCHEDULING_REGIME);
    log.info("Scheduling regime: " + regime);
    switch (regime) {
      case REGIME_DEDICATED:
        schedulingRegime = new SyncActionScheduler() {
          final Map<Long, ScheduledExecutorService> schedulerMap = new HashMap<>();
          final ScheduledExecutorService defaultScheduler = Executors.newSingleThreadScheduledExecutor();

          @Override
          public ScheduledExecutorService getScheduler(SynchronizedEveAccount account) {
            if (account == null) return defaultScheduler;
            synchronized (schedulerMap) {
              ScheduledExecutorService service = schedulerMap.get(account.getAid());
              if (service == null) {
                service = Executors.newSingleThreadScheduledExecutor();
                schedulerMap.put(account.getAid(), service);
              }
              return service;
            }
          }
        };
        break;

      case REGIME_SHARED:
        // fall through
      default:
        schedulingRegime = new SyncActionScheduler() {
          final ScheduledExecutorService service = Executors.newScheduledThreadPool(
              (int) OrbitalProperties.getLongGlobalProperty(PROP_MAX_THREADS_ESI, DEF_MAX_THREADS_ESI));

          @Override
          public ScheduledExecutorService getScheduler(SynchronizedEveAccount account) {
            return service;
          }
        };
        break;
    }

    dispatch = checkService;
  }

  private void dispatchAccountCheckSchedule() {
    AccountCheckScheduleEvent accountChecker = new AccountCheckScheduleEvent(this, schedulingRegime, checkService);
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
