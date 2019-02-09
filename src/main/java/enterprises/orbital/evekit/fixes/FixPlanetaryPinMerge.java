package enterprises.orbital.evekit.fixes;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.db.DBPropertyProvider;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.PlanetaryPin;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.*;

import static enterprises.orbital.evekit.model.AbstractESIAccountSync.ANY_SELECTOR;

/**
 * This code fixes redundant PlanetaryPinHead storage due to incorrect equality checking.
 *
 * The fix performs the following modifications:
 *
 * <ol>
 *   <li>Cycle through each character account.</li>
 *   <li>Cycle through all PlanetarPins for each character account grouped by planet ID and pin ID and ordered by lifeline.</li>
 *   <li>If two adjacent pins a and b are equivalent, meaning a.lifeEnd = b.lifeStart and all other fields
 *   are identical, then set a.lifeEnd = b.lifeEnd, merge any meta-data, and delete b.</li>
 * </ol>
 */
public class FixPlanetaryPinMerge {
  // Persistence unit for properties
  private static final String PROP_PROPERTIES_PU = "enterprises.orbital.evekit.sync_mgr.properties.persistence_unit";

  private static List<PlanetaryPin> retrieveAll(AbstractESIAccountSync.QueryCaller<PlanetaryPin> query) throws IOException {
    final AttributeSelector ats = AttributeSelector.any();
    long contid = 0;
    List<PlanetaryPin> results = new ArrayList<>();
    List<PlanetaryPin> nextBatch = query.query(contid, ats);
    while (!nextBatch.isEmpty()) {
      results.addAll(nextBatch);
      contid = nextBatch.get(nextBatch.size() - 1)
                        .getCid();
      nextBatch = query.query(contid, ats);
    }
    return results;
  }


  public static void main(
      String[] args)
      throws Exception {
    // Populate properties
    OrbitalProperties.addPropertyFile("FixPlanetaryPinMerge.properties");
    // Sent persistence unit for properties
    PersistentProperty.setProvider(new DBPropertyProvider(OrbitalProperties.getGlobalProperty(PROP_PROPERTIES_PU)));

    // Collect all live character sync accounts
    List<SynchronizedEveAccount> charAccounts = new ArrayList<>();
    for (SynchronizedEveAccount next : SynchronizedEveAccount.getAllSyncAccounts(false)) {
      if (next.isCharacterType()) charAccounts.add(next);
    }

    // Cycle through all character accounts, look for planetary pins
    for (SynchronizedEveAccount next : charAccounts) {
      System.out.println("Processing " + next);

      // Collect all unique (planet, pin) pairs for the current character.
      Set<Pair<Integer, Long>> planetPins = new HashSet<>();
      for (PlanetaryPin existing : retrieveAll((long contid, AttributeSelector at) -> PlanetaryPin.accessQuery(next,
                                                                                                               contid,
                                                                                                               1000,
                                                                                                               false,
                                                                                                               at,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR))) {
        planetPins.add(Pair.of(existing.getPlanetID(), existing.getPinID()));
      }

      // Now cycle through each (planet, pin) pair in timeline order and merge any equivalent adjacent members.
      System.out.println("Character has " + planetPins.size() + " pins");
      int merged = 0;
      for (Pair<Integer, Long> nextPair : planetPins) {
        System.out.print("+");
        System.out.flush();
        List<PlanetaryPin> allPins = retrieveAll((long contid, AttributeSelector at) -> PlanetaryPin.accessQuery(next,
                                                                                                                 contid,
                                                                                                                 1000,
                                                                                                                 false,
                                                                                                                 at,
                                                                                                                 AttributeSelector.values(
                                                                                                                     nextPair.getLeft()),
                                                                                                                 AttributeSelector.values(
                                                                                                                     nextPair.getRight()),
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR,
                                                                                                                 ANY_SELECTOR));
        if (allPins.size() <= 1) continue;
        PlanetaryPin current = allPins.get(0);
        for (PlanetaryPin nextPin : allPins.subList(1, allPins.size())) {
          if (current.getLifeEnd() == nextPin.getLifeStart() && current.equivalent(nextPin)) {

            // Merge
            final PlanetaryPin toUpdate = current;
            current = EveKitUserAccountProvider.getFactory().runTransaction(() -> {
              PlanetaryPin toMerge = PlanetaryPin.get(next, toUpdate.getLifeStart(), toUpdate.getPlanetID(), toUpdate.getPinID());
              PlanetaryPin toRemove = PlanetaryPin.get(next, nextPin.getLifeStart(), nextPin.getPlanetID(), nextPin.getPinID());

              toMerge.setLifeEnd(nextPin.getLifeEnd());
              for (Map.Entry<String, String> meta : nextPin.getAllMetaData()) {
                try {
                  toMerge.setMetaData(meta.getKey(), meta.getValue());
                } catch (MetaDataLimitException e) {
                  // This should never happen, fatal if it does
                  e.printStackTrace();
                  System.exit(1);
                } catch (MetaDataCountException e) {
                  // If this happens, then we arbitrarily discard the new data.
                  System.out.println("meta-data limit exceeded, dropping new data");
                }
              }

              // Delete the old data
              EveKitUserAccountProvider.getFactory().getEntityManager().remove(toRemove);

              // Persist the merged data
              return CachedData.update(toMerge);
            });
            merged++;
            if (merged % 100 == 0) {
              System.out.print(".");
              System.out.flush();
            }

            // Note that we don't alter the current pin so we can potentially merge with the next pin.
          } else {
            current = nextPin;
          }
        }
      }
      System.out.println("\nMerged " + merged + " pins");
    }
  }

}
