package enterprises.orbital.evekit.sync;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.db.DBPropertyProvider;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.snapshot.SnapshotScheduler;

/**
 * A simple command line tool for working with the sync engine.
 */
public class AdminTool {

  public static final void printUsage() {
    System.out.println("help                          - this message.");
    System.out.println("exit                          - quit.");
    System.out.println("snapshot <userid> <accountid> - generate account snapshot file");
  }

  public static void main(
                          String[] argv)
    throws IOException {
    // Populate properties
    OrbitalProperties.addPropertyFile("SyncMgrStandalone.properties");
    // Sent persistence unit for properties
    PersistentProperty.setProvider(new DBPropertyProvider(OrbitalProperties.getGlobalProperty(SyncManager.PROP_PROPERTIES_PU)));
    // Start command parser
    do {
      System.out.print("> ");
      LineNumberReader commands = new LineNumberReader(new InputStreamReader(System.in));
      String next = commands.readLine();
      StringTokenizer tokens = new StringTokenizer(next);
      try {
        switch (tokens.nextToken()) {

        case "exit":
          System.exit(0);
          break;

        case "snapshot":
          generateSnapshot(Long.valueOf(tokens.nextToken()), Long.valueOf(tokens.nextToken()));
          break;

        default:
          printUsage();
        }
      } catch (NoSuchElementException e) {
        printUsage();
      }
    } while (true);

  }

  public static void generateSnapshot(
                                      long ownerID,
                                      long accountID)
    throws IOException {
    EveKitUserAccount user = EveKitUserAccount.getAccount(ownerID);
    SynchronizedEveAccount acct = SynchronizedEveAccount.getSynchronizedAccount(user, accountID, true);
    SnapshotScheduler.generateAccountSnapshot(acct, OrbitalProperties.getCurrentTime());
    System.out.println("Snapshot generated in directory: "
        + OrbitalProperties.getGlobalProperty(SnapshotScheduler.PROP_SNAPSHOT_DIR, SnapshotScheduler.DEF_SNAPSHOT_DIR));
  }
}
