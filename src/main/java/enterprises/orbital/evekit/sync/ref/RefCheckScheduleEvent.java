package enterprises.orbital.evekit.sync.ref;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.model.AbstractESIRefSync;
import enterprises.orbital.evekit.model.ESIRefEndpointSyncTracker;
import enterprises.orbital.evekit.model.ESIRefSyncEndpoint;
import enterprises.orbital.evekit.model.TrackerNotFoundException;
import enterprises.orbital.evekit.model.alliance.sync.ESIAllianceSync;
import enterprises.orbital.evekit.model.faction.sync.*;
import enterprises.orbital.evekit.model.server.sync.ESIServerStatusSync;
import enterprises.orbital.evekit.model.sov.sync.ESISovereigntyCampaignSync;
import enterprises.orbital.evekit.model.sov.sync.ESISovereigntyMapSync;
import enterprises.orbital.evekit.model.sov.sync.ESISovereigntyStructureSync;
import enterprises.orbital.evekit.sync.ControllerEvent;
import enterprises.orbital.evekit.sync.EventScheduler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodic event which does the following:
 * <p>
 * <ul>
 * <li>Verify an unfinished scheduled sync tracker exists for every non-excluded ESI ref endpoint</li>
 * <li>Verify an event has been queued for every unfinished scheduled sync tracker</li>
 * <li>Queue a new instance of this checker when complete.</li>
 * </ul>
 * <p>
 * Periodic event which ensures there is at least one pending event for each
 * supported reference endpoint.
 */
public class RefCheckScheduleEvent extends ControllerEvent {
  public static final Logger log = Logger.getLogger(RefCheckScheduleEvent.class.getName());

  // Max delay for reference check schedule event
  private static final String PROP_MAX_DELAY = "enterprises.orbital.evekit.ref_sync_mgr.max_delay.ref_check_schedule";
  private static final long DEF_MAX_DELAY = TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);

  // Time between check schedule events
  private static final String PROP_CYCLE_DELAY = "enterprises.orbital.evekit.ref_sync_mgr.ref_check_schedule.cycle";
  private static final long DEF_CYCLE_DELAY = TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);

  private long maxDelay;
  private EventScheduler eventScheduler;
  private ScheduledExecutorService taskScheduler;

  RefCheckScheduleEvent(EventScheduler eventScheduler, ScheduledExecutorService taskScheduler) {
    this.eventScheduler = eventScheduler;
    this.taskScheduler = taskScheduler;
    this.maxDelay = PersistentProperty.getLongPropertyWithFallback(PROP_MAX_DELAY, DEF_MAX_DELAY);
  }

  @Override
  public long maxDelayTime() {
    return maxDelay;
  }

  @Override
  public String toString() {
    return "RefCheckScheduleEvent{" +
        "maxDelay=" + maxDelay +
        ", eventScheduler=" + eventScheduler +
        ", taskScheduler=" + taskScheduler +
        ", dispatchTime=" + dispatchTime +
        ", tracker=" + tracker +
        '}';
  }

  /**
   * Check whether an event exists for the given endpoint and for which the
   * associated tracker is not done.  We assume the endpoint is handled by an instance
   * of ESIStandardREfSyncEvent.
   *
   * @param ep the endpoint to check
   * @return true if an event for the given endpoint is queued and is not done, false otherwise.
   */
  private boolean hasUnfinishedEvent(ESIRefSyncEndpoint ep) {
    synchronized (eventScheduler.pending) {
      for (ControllerEvent next : eventScheduler.pending) {
        if (next instanceof ESIStandardRefSyncEvent) {
          ESIStandardRefSyncEvent check = (ESIStandardRefSyncEvent) next;
          if (check.getEndpoint() == ep && !check.getTracker()
                                                 .isDone())
            return true;
        }
      }
    }
    return false;
  }

  /**
   * Schedule a controller event for execution.
   *
   * @param ev        the event to schedule.
   * @param eventTime time when this event should dispatch
   */
  private void scheduleEvent(ControllerEvent ev, long eventTime) {
    long delay = Math.max(0L, eventTime - OrbitalProperties.getCurrentTime());
    log.fine("Scheduling event to occur in " + delay + " milliseconds");
    ev.setTracker(taskScheduler.schedule(ev, delay, TimeUnit.MILLISECONDS));
    eventScheduler.pending.add(ev);
  }

  @Override
  public void run() {
    log.fine("Starting execution: " + toString());
    super.run();

    synchronized (RefCheckScheduleEvent.class) {

      // Ensure unfinished sync trackers exists for all non-excluded endpoints.
      Set<ESIRefSyncEndpoint> excluded = AbstractESIRefSync.getExcludedEndpoints();
      for (ESIRefSyncEndpoint check : ESIRefSyncEndpoint.values()) {
        log.fine("Starting tracker check for: " + check);

        // Skip excluded
        if (excluded.contains(check))
          continue;

        // Check for unfinished sync tracker
        try {
          ESIRefEndpointSyncTracker.getOrCreateUnfinishedTracker(check, OrbitalProperties.getCurrentTime(), null);
        } catch (IOException e) {
          log.log(Level.WARNING,
                  "Error retrieving or creating unfinished tracker for endpoint: " + check + ", continuing", e);
        }
      }

      // Ensure every unfinished sync tracker has a queued, unfinished controller event.
      // Note that the event type will depend on the tracker type.
      for (ESIRefSyncEndpoint check : ESIRefSyncEndpoint.values()) {
        log.fine("Starting sync event check for: " + check);
        // Skip excluded
        if (excluded.contains(check))
          continue;

        // Skip endpoints we don't handle yet
        if (!handlerDeploymentMap.containsKey(check))
          continue;

        // Make sure an unfinished controller event exists for this endpoint.
        // If not, queue the event at the scheduled start time for the sync tracker.
        try {

          if (!hasUnfinishedEvent(check)) {
            log.fine("Scheduling sync event for " + check);
            long startTime = ESIRefEndpointSyncTracker.getUnfinishedTracker(check)
                                                      .getScheduled();
            scheduleEvent(new ESIStandardRefSyncEvent(check, handlerDeploymentMap.get(check)
                                                                                 .generate(), taskScheduler),
                          startTime);
          }

        } catch (TrackerNotFoundException e) {
          log.log(Level.WARNING, "Unfinished tracker should exist: " + check + ", continuing", e);
        } catch (IOException e) {
          log.log(Level.WARNING, "Database error attempting to retrieve tracker: " + check + ", continuing", e);
        }
      }
    }

    // Requeue ourselves for a future invocation
    long executionDelay = PersistentProperty.getLongPropertyWithFallback(PROP_CYCLE_DELAY, DEF_CYCLE_DELAY);
    scheduleEvent(new RefCheckScheduleEvent(eventScheduler, taskScheduler),
                  OrbitalProperties.getCurrentTime() + executionDelay);
    log.fine("Execution finished: " + toString());
  }

  // Inner class describing configuration of sync handlers
  protected interface SyncHandlerGenerator<A extends AbstractESIRefSync> {
    A generate();
  }

  // Sync handler deployment map
  private static Map<ESIRefSyncEndpoint, SyncHandlerGenerator> handlerDeploymentMap = new HashMap<>();

  static {
    handlerDeploymentMap.put(ESIRefSyncEndpoint.REF_SERVER_STATUS, ESIServerStatusSync::new);
    handlerDeploymentMap.put(ESIRefSyncEndpoint.REF_ALLIANCE, ESIAllianceSync::new);
    handlerDeploymentMap.put(ESIRefSyncEndpoint.REF_SOV_MAP, ESISovereigntyMapSync::new);
    handlerDeploymentMap.put(ESIRefSyncEndpoint.REF_SOV_STRUCTURE, ESISovereigntyStructureSync::new);
    handlerDeploymentMap.put(ESIRefSyncEndpoint.REF_SOV_CAMPAIGN, ESISovereigntyCampaignSync::new);
    handlerDeploymentMap.put(ESIRefSyncEndpoint.REF_FW_CHAR_LEADERBOARD, ESIFacWarCharacterLeaderboardSync::new);
    handlerDeploymentMap.put(ESIRefSyncEndpoint.REF_FW_CORP_LEADERBOARD, ESIFacWarCorporationLeaderboardSync::new);
    handlerDeploymentMap.put(ESIRefSyncEndpoint.REF_FW_FACTION_LEADERBOARD, ESIFacWarFactionLeaderboardSync::new);
    handlerDeploymentMap.put(ESIRefSyncEndpoint.REF_FW_STATS, ESIFacWarStatsSync::new);
    handlerDeploymentMap.put(ESIRefSyncEndpoint.REF_FW_SYSTEMS, ESIFacWarSystemsSync::new);
    handlerDeploymentMap.put(ESIRefSyncEndpoint.REF_FW_WARS, ESIFacWarWarsSync::new);
  }

}
