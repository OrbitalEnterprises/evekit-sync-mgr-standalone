package enterprises.orbital.evekit.tools;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.db.DBPropertyProvider;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.account.SynchronizedAccountAccessKey;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.PlanetaryPin;
import org.apache.commons.lang3.tuple.Pair;

import javax.persistence.Query;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static enterprises.orbital.evekit.model.AbstractESIAccountSync.ANY_SELECTOR;

/**
 * This code simply lists all access key credentials in the system.
 */
public class ListAccessKeys {
  // Persistence unit for properties
  private static final String PROP_PROPERTIES_PU = "enterprises.orbital.evekit.sync_mgr.properties.persistence_unit";


  public static void main(
      String[] args)
      throws Exception {
    // Populate properties
    OrbitalProperties.addPropertyFile("Tools.properties");
    // Set persistence unit for properties
    PersistentProperty.setProvider(new DBPropertyProvider(OrbitalProperties.getGlobalProperty(PROP_PROPERTIES_PU)));

    // Display all access keys
    for (SynchronizedAccountAccessKey next : SynchronizedAccountAccessKey.getAllKeysOnServer()) {
      next.generateCredential();
      System.out.println(next.getAccessKey() + " " + next.getCredential());
    }
  }

}
