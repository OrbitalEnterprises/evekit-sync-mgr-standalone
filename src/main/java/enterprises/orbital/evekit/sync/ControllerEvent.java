package enterprises.orbital.evekit.sync;

import enterprises.orbital.base.OrbitalProperties;

import java.util.concurrent.Future;

// Marker interface for controller events
public abstract class ControllerEvent implements Runnable {
  // Time when this event was dispatched
  protected long dispatchTime;
  // Future tracking execution of this event.
  protected Future<?> tracker;

  public long getDispatchTime() {
    return dispatchTime;
  }

  public Future<?> getTracker() {
    return tracker;
  }

  public void setTracker(Future<?> tracker) {
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

  @Override
  public void run() {
    dispatchTime = OrbitalProperties.getCurrentTime();
  }
}