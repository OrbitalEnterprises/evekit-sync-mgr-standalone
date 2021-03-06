package enterprises.orbital.evekit.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.db.DBPropertyProvider;
import enterprises.orbital.evekit.sync.account.ESIAccountEventScheduler;
import enterprises.orbital.evekit.sync.delete.DeleteEventScheduler;
import enterprises.orbital.evekit.sync.ref.ESIRefEventScheduler;
import enterprises.orbital.evekit.sync.snapshot.SnapshotEventScheduler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main Sync process. This class is intended to be started from the command line, and restarted if it fails unexpectedly. The following tasks are performed:
 * <p>
 * <ul>
 * <li>Scan for accounts which are eligible for synchronization but which have not yet been dispatched to a scheduling thread. This includes accounts with a
 * SyncTracker in progress.
 * <li>Schedule eligible accounts for synchronization when a scheduler process is available.
 * <li>Detect and kill stuck synchronization processes.
 * <li>Delete accounts eligible for deletion.
 * <li>Schedule and dispatch daily account snapshot dumps.
 * </ul>
 */
public class SyncManager {
  private static final Logger log = Logger.getLogger(SyncManager.class.getName());
  // Persistence unit for properties
  private static final String PROP_PROPERTIES_PU = "enterprises.orbital.evekit.sync_mgr.properties.persistence_unit";
  // Sleep time when main event loop performs no action
  private static final String PROP_NOACTION_DELAY = "enterprises.orbital.evekit.sync_mgr.noaction_delay";
  private static final long DEF_NOACTION_DELAY = TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);
  // Schedule activation properties
  private static final String PROP_SYNC_SCHEDULE_ENABLED = "enterprises.orbital.evekit.sync_mgr.type.SYNC.enabled";
  private static final String PROP_REFSYNC_SCHEDULE_ENABLED = "enterprises.orbital.evekit.sync_mgr.type.REFSYNC.enabled";
  private static final String PROP_DELETE_SCHEDULE_ENABLED = "enterprises.orbital.evekit.sync_mgr.type.DELETE.enabled";
  private static final String PROP_SNAPSHOT_SCHEDULE_ENABLED = "enterprises.orbital.evekit.sync_mgr.type.SNAPSHOT.enabled";

  public static void main(
      String[] args)
      throws IOException {
    // Populate properties
    OrbitalProperties.addPropertyFile("SyncMgrStandalone.properties");
    // Sent persistence unit for properties
    PersistentProperty.setProvider(new DBPropertyProvider(OrbitalProperties.getGlobalProperty(PROP_PROPERTIES_PU)));
    // Main loop proceeds as follows:
    // 1. Dispatch events in the event queue until it is empty.
    // 1.a. Queue events only dispatch if there are threads available to dispatch the event
    // 2. When the queue is empty:
    // 2.a. Queue up sync events for accounts that are eligible for synchronization
    // 2.b. Queue up sync events for accounts that have an uncompleted sync tracker (also handles stuck trackers)
    // 2.c. Queue up delete events for accounts which are eligible for deletion
    // 2.d. Queue up snapshot events for accounts which are eligible to take a snapshot
    Map<EventType, EventScheduler> schedules = new HashMap<>();
    if (OrbitalProperties.getBooleanGlobalProperty(PROP_SYNC_SCHEDULE_ENABLED, false))
      schedules.put(EventType.ESISYNC, new ESIAccountEventScheduler());
    if (OrbitalProperties.getBooleanGlobalProperty(PROP_REFSYNC_SCHEDULE_ENABLED, false))
      schedules.put(EventType.ESIREFSYNC, new ESIRefEventScheduler());
    if (OrbitalProperties.getBooleanGlobalProperty(PROP_DELETE_SCHEDULE_ENABLED, false))
      schedules.put(EventType.DELETE, new DeleteEventScheduler());
    if (OrbitalProperties.getBooleanGlobalProperty(PROP_SNAPSHOT_SCHEDULE_ENABLED, false))
      schedules.put(EventType.SNAPSHOT, new SnapshotEventScheduler());
    final long noActionDelay = OrbitalProperties.getLongGlobalProperty(PROP_NOACTION_DELAY, DEF_NOACTION_DELAY);
    // Spin forever - ctrl-C to kill
    log.fine("Entering main event loop");
    //noinspection InfiniteLoopStatement
    while (true) {
      // Set to true on an iteration if we took some action
      boolean action = false;
      // Current loop time
      long now = OrbitalProperties.getCurrentTime();
      // Process event queue
      for (EventType next : EventType.values()) {
        if (!schedules.containsKey(next))
          // Skip disabled schedulers
          continue;
        log.info("[START] Processing Queue: " + next);
        EventScheduler nextScheduler = schedules.get(next);
        log.info("[QUEUE CHECK/CLEANUP] " + next);
        if (nextScheduler.pending.isEmpty()) {
          log.info("Filling queue");
          // Attempt to refill queue with events. We expect the scheduler will also dispatch these events internally
          action = nextScheduler.fillPending();
        } else {
          // Process scheduled events
          log.info("Checking status of queued events");
          List<ControllerEvent> deleteList = new ArrayList<>();
          for (ControllerEvent e : nextScheduler.pending) {
            // Remove completed event
            if (e.tracker.isDone()) {
              deleteList.add(e);
              action = true;
              try {
                // Call the get method to retrieve any uncaught exceptions from the event
                e.tracker.get();
              } catch (InterruptedException | ExecutionException | CancellationException f) {
                log.log(Level.WARNING, "Uncaught exception thrown by event: ", f);
              }
              continue;
            }
            // Check if a dispatched event is stuck
            if (e.dispatchTime > 0) {
              long delay = now - e.dispatchTime;
              if (delay > e.maxDelayTime()) {
                log.warning(
                    "Canceling event due to timeout.  Delay=" + delay + " Max=" + e.maxDelayTime() + " Event=" + e);
                e.tracker.cancel(true);
                action = true;
                //noinspection UnnecessaryContinue
                continue;
              }
            }
          }
          nextScheduler.pending.removeAll(deleteList);
          log.info("Cleaned up " + deleteList.size() + " events");
        }
        log.info("[STATUS CHECK] " + next);
        nextScheduler.statusCheck();
        log.info("[END] Processing Queue: " + next);
      }
      // If we took no action, then sleep and try again later.
      if (!action) try {
        log.info(
            "No actions sleeping for " + TimeUnit.MINUTES.convert(noActionDelay, TimeUnit.MILLISECONDS) + " minutes");
        Thread.sleep(noActionDelay);
      } catch (InterruptedException e) {
        // log but ignore
        log.log(Level.WARNING, "Unexpected intterupt", e);
      }
    }
  }

}
