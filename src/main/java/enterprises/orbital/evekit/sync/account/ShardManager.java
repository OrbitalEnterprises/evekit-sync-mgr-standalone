package enterprises.orbital.evekit.sync.account;

public class ShardManager {
  // Allowed sharding strategies
  enum Strategies {
    SINGLE_HASH
  }

  // Singleton
  private ShardManager() {}

  public static ShardFilter createShardFilter(Strategies strat, String init) {
    switch (strat) {
      case SINGLE_HASH:
        final boolean[] filter = new boolean[10];
        for (String i : init.split(",")) {
          int offset = Integer.valueOf(i);
          filter[offset] = true;
        }
        return acct -> {
          int index = (int) (acct.getAid() % 10);
          return filter[index];
        };

      default:
        throw new RuntimeException("Unknown shard strategy: " + strat);
    }
  }
}
