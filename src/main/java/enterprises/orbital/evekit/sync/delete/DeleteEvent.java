package enterprises.orbital.evekit.sync.delete;

import java.util.concurrent.Future;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.AccountDeleter;
import enterprises.orbital.evekit.sync.ControllerEvent;

// Delete the given account if it is still eligible for deletion
public class DeleteEvent extends ControllerEvent implements Runnable {
  public SynchronizedEveAccount toDelete;
  public long                   maxDelay;

  public DeleteEvent(SynchronizedEveAccount d) {
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
    super.run();
    AccountDeleter del = new AccountDeleter();
    del.deleteMarked(toDelete);
  }
}