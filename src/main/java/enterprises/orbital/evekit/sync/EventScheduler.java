package enterprises.orbital.evekit.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

public abstract class EventScheduler {
  public List<ControllerEvent> pending = Collections.synchronizedList(new ArrayList<ControllerEvent>());
  public ExecutorService       dispatch;

  /**
   * Attempt to fill the pending queue with more events. This method will normally only be called when the queue is empty.
   * 
   * @return true if events were added, false otherwise.
   */
  public abstract boolean fillPending();
}