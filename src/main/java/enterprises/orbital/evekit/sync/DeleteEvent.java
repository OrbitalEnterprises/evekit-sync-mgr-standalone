package enterprises.orbital.evekit.sync;

import java.util.concurrent.Future;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.AccountDeleter;

// Delete the given account if it is still eligible for deletion
public class DeleteEvent extends ControllerEvent implements Runnable {
  public SynchronizedEveAccount toDelete;
  public long                   maxDelay;

  public DeleteEvent(Future<?> tracker, SynchronizedEveAccount d) {
    super(Long.MAX_VALUE, tracker);
    toDelete = d;
    maxDelay = PersistentProperty.getLongPropertyWithFallback(DeleteEventScheduler.PROP_MAX_DELAY_DELETE, DeleteEventScheduler.DEF_MAX_DELAY_DELETE);
  }

  @Override
  public long maxDelayTime() {
    return maxDelay;
  }

  @Override
  public String toString() {
    return "DeleteEvent [toDelete=" + toDelete + ", maxDelay=" + maxDelay + ", dispatchTime=" + dispatchTime + ", tracker=" + tracker + "]";
  }

  @Override
  public void run() {
    dispatchTime = OrbitalProperties.getCurrentTime();
    AccountDeleter del = new AccountDeleter();
    del.deleteMarked(toDelete);
  }
}