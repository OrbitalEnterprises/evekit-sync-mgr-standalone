package enterprises.orbital.evekit.sync.ref;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.sync.EventScheduler;

public class RefSyncEventScheduler extends EventScheduler {
  public static final Logger log                      = Logger.getLogger(RefSyncEventScheduler.class.getName());
  // Configuration for each scheduler type
  public static final String PROP_MAX_THREADS_SYNC    = "enterprises.orbital.evekit.ref_sync_mgr.max_threads.sync";
  public static final int    DEF_MAX_THREADS_SYNC     = 1;
  // Max delay properties for various event types
  public static final String PROP_MAX_DELAY_SYNC      = "enterprises.orbital.evekit.ref_sync_mgr.max_delay.sync";
  public static final long   DEF_MAX_DELAY_SYNC       = TimeUnit.MILLISECONDS.convert(30, TimeUnit.MINUTES);
  // Delay between checking for accounts we can sync
  public static final String PROP_SYNC_CHECK_INTERVAL = "enterprises.orbital.evekit.ref_sync_mgr.sync_check_interval";
  public static final long   DEF_SYNC_CHECK_INTERVAL  = TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);

  public RefSyncEventScheduler() {
    super();
    dispatch = Executors.newFixedThreadPool((int) OrbitalProperties.getLongGlobalProperty(PROP_MAX_THREADS_SYNC, DEF_MAX_THREADS_SYNC));
  }

  @Override
  public boolean fillPending() {
    // Schedule the next sync. This will automatically clean up any failed or stuck ref sync trackers.
    RefSyncEvent syncEvent = new RefSyncEvent();
    syncEvent.setTracker(dispatch.submit(syncEvent));
    pending.add(syncEvent);
    return true;
  }

}
