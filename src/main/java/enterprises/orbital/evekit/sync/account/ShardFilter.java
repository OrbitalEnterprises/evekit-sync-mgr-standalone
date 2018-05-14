package enterprises.orbital.evekit.sync.account;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;

public interface ShardFilter {

  /**
   * Check whether the given synchronized account should be processed
   * by this shard.
   *
   * @param acct SynchronizedEveAccount to check
   * @return true if this shard should process this account.  false otherwise.
   */
  boolean process(SynchronizedEveAccount acct);
}
