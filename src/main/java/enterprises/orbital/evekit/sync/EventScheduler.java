package enterprises.orbital.evekit.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

public abstract class EventScheduler {
  public final List<ControllerEvent> pending = Collections.synchronizedList(new ArrayList<>());
  public ExecutorService       dispatch;

  /**
   * Attempt to fill the pending queue with more events. This method will normally only be called when the queue is empty.
   * 
   * @return true if events were added, false otherwise.
   */
  public abstract boolean fillPending();

  /**
   * Give this scheduler a chance to verify it is still live and functioning properly.
   */
  public void statusCheck() {}
}