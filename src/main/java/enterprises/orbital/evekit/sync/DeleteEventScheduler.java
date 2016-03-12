package enterprises.orbital.evekit.sync;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.AccountDeleter;

public class DeleteEventScheduler extends EventScheduler {
  public static final Logger log                        = Logger.getLogger(DeleteEventScheduler.class.getName());
  // Configuration for each scheduler type
  public static final String PROP_MAX_THREADS_DELETE    = "enterprises.orbital.evekit.sync_mgr.max_threads.delete";
  public static final int    DEF_MAX_THREADS_DELETE     = 1;
  // Max delay properties for various event types
  public static final String PROP_MAX_DELAY_DELETE      = "enterprises.orbital.evekit.sync_mgr.max_delay.delete";
  public static final long   DEF_MAX_DELAY_DELETE       = TimeUnit.MILLISECONDS.convert(2, TimeUnit.HOURS);
  // Delay between checking for accounts we can delete
  public static final String PROP_DELETE_CHECK_INTERVAL = "enterprises.orbital.evekit.sync_mgr.delete_check_interval";
  public static final long   DEF_DELETE_CHECK_INTERVAL  = TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);

  public DeleteEventScheduler() {
    super();
    dispatch = Executors.newFixedThreadPool((int) OrbitalProperties.getLongGlobalProperty(PROP_MAX_THREADS_DELETE, DEF_MAX_THREADS_DELETE));
  }

  @Override
  public boolean fillPending() {
    // Scan for accounts eligible for deletion
    for (SynchronizedEveAccount next : SynchronizedEveAccount.getAllMarkedForDelete()) {
      if (AccountDeleter.deletable(next) == null) {
        DeleteEvent deleteEvent = new DeleteEvent(null, next);
        deleteEvent.tracker = dispatch.submit(deleteEvent);
        pending.add(deleteEvent);
      }
    }
    // If we didn't dispatch any events, then dispatch a sleep event to delay until the next check
    if (pending.isEmpty()) {
      long delay = OrbitalProperties.getLongGlobalProperty(PROP_DELETE_CHECK_INTERVAL, DEF_DELETE_CHECK_INTERVAL);
      log.info("No events to dispatch, sleeping for " + TimeUnit.MINUTES.convert(delay, TimeUnit.MILLISECONDS) + " minutes");
      SleepEvent sleeper = new SleepEvent(delay);
      sleeper.tracker = dispatch.submit(sleeper);
      pending.add(sleeper);
    } else {
      log.info("Dispatched " + pending.size() + " delete tasks");
    }
    return true;
  }

}
