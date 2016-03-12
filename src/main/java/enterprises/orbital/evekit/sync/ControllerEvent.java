package enterprises.orbital.evekit.sync;

import java.util.concurrent.Future;

// Marker interface for controller events
public abstract class ControllerEvent {
  // Time when this event was dispatched
  public long      dispatchTime;
  // Future tracking execution of this event.
  public Future<?> tracker;

  public ControllerEvent(long dispatchTime, Future<?> tracker) {
    super();
    this.dispatchTime = dispatchTime;
    this.tracker = tracker;
  }

  /**
   * Return max delay time for this event. If this now - dispatch exceeds this value, then we'll cancel the event.
   * 
   * @return max delay time in milliseconds.
   */
  public abstract long maxDelayTime();

  @Override
  public String toString() {
    return "ControllerEvent [dispatchTime=" + dispatchTime + ", tracker=" + tracker + "]";
  }

}