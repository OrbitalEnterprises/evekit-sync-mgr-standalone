package enterprises.orbital.evekit.sync.account;

import java.util.logging.Logger;

public class ShardManager {
  public static final Logger log = Logger.getLogger(ShardManager.class.getName());

  // Allowed sharding strategies
  enum Strategies {
    SINGLE_HASH
  }

  // Singleton
  private ShardManager() {}

  public static ShardFilter createShardFilter(Strategies strat, String init) {
    switch (strat) {
      case SINGLE_HASH:
        int shardBuckets = 10;
        if (init.indexOf(':') != -1) {
          String[] initSplit = init.split(":");
          shardBuckets = Integer.valueOf(initSplit[0]);
          init = initSplit[1];
        }
        final int bucketCount = shardBuckets;
        final boolean[] filter = new boolean[bucketCount];
        StringBuilder processList = new StringBuilder();
        processList.append("[ ");
        for (String i : init.split(",")) {
          int offset = Integer.valueOf(i);
          filter[offset] = true;
          processList.append(offset).append(' ');
        }
        processList.append(']');
        log.info("Instantiating SINGLE_HASH shard strategy with " + String.valueOf(bucketCount) + " buckets");
        log.info("This instance will process buckets: " + processList.toString());
        return acct -> {
          int index = (int) (acct.getAid() % bucketCount);
          return filter[index];
        };

      default:
        throw new RuntimeException("Unknown shard strategy: " + strat);
    }
  }
}
