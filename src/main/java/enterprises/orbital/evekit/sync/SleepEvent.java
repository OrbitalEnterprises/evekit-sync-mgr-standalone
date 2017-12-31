package enterprises.orbital.evekit.sync;

/**
 * An artificial event which can be used to occupy a scheduler queue to force a delay until the queue is filled again.
 */
public class SleepEvent extends ControllerEvent implements Runnable {
  public long sleepTime;

  public SleepEvent(long sleepTime) {
    this.sleepTime = sleepTime;
  }

  @Override
  public long maxDelayTime() {
    return Long.MAX_VALUE;
  }

  @Override
  public void run() {
    super.run();
    try {
      Thread.sleep(sleepTime);
    } catch (InterruptedException e) {
      // ignore and exit
    }
  }

}
