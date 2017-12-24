package enterprises.orbital.evekit.sync.esi;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.sync.EventScheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

/**
 * Schedule synchronization of ESI endpoints for both references and synchronized account data.
 */
public class ESIRefEventScheduler extends EventScheduler {
  public static final Logger log                      = Logger.getLogger(ESIRefEventScheduler.class.getName());
  // Configuration for each scheduler type
  public static final String PROP_MAX_THREADS_ESI = "enterprises.orbital.evekit.ref_sync_mgr.max_threads.esi";
  public static final int DEF_MAX_THREADS_ESI = 1;

  // Alias for thread pool executor which exposes scheduling classes
  private ScheduledExecutorService dispatchAlias;

  public ESIRefEventScheduler() {
    super();
    dispatch = dispatchAlias = Executors.newScheduledThreadPool((int) OrbitalProperties.getLongGlobalProperty(PROP_MAX_THREADS_ESI, DEF_MAX_THREADS_ESI));
  }

  protected void dispatchRefCheckSchedule() {
    RefCheckScheduleEvent refChecker = new RefCheckScheduleEvent(this, dispatchAlias);
    refChecker.setTracker(dispatch.submit(refChecker));
    pending.add(refChecker);
  }

  @Override
  public boolean fillPending() {
    // This should normally only be called once.  Our main task here is to launch the CheckSchedule event.
    // The CheckSchedule event should automatically queue itself when complete.  If that fails, this method
    // will be called again to queue a new event.

    // Dispatch check schedule for reference data
    dispatchRefCheckSchedule();

    return true;
  }

}
