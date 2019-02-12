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
import java.util.concurrent.ExecutionException;

import static enterprises.orbital.evekit.model.AbstractESIAccountSync.ANY_SELECTOR;

/**
 * This code fixes redundant PlanetaryPinHead storage due to incorrect equality checking.
 * <p>
 * The fix performs the following modifications:
 *
 * <ol>
 * <li>Cycle through each character account.</li>
 * <li>Cycle through all PlanetarPins for each character account grouped by planet ID and pin ID and ordered by lifeline.</li>
 * <li>If two adjacent pins a and b are equivalent, meaning a.lifeEnd = b.lifeStart and all other fields
 * are identical, then set a.lifeEnd = b.lifeEnd, merge any meta-data, and delete b.</li>
 * </ol>
 */
public class FixPlanetaryPinMerge {
  // Persistence unit for properties
  private static final String PROP_PROPERTIES_PU = "enterprises.orbital.evekit.sync_mgr.properties.persistence_unit";

  private static List<PlanetaryPin> retrieveAll(
      AbstractESIAccountSync.QueryCaller<PlanetaryPin> query) throws IOException {
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


  private static void mergePins(final SynchronizedEveAccount next, final PlanetaryPin toUpdate, final long newLifeEnd,
                                List<PlanetaryPin> toDelete) throws IOException, ExecutionException {
    System.out.print("m[" + toDelete.size() + "]");
    System.out.flush();
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               List<PlanetaryPin> deleteList = new ArrayList<>();
                               PlanetaryPin toMerge = PlanetaryPin.get(next, toUpdate.getLifeStart(),
                                                                       toUpdate.getPlanetID(), toUpdate.getPinID());
                               Set<Long> deleteSet = new HashSet<>();
                               for (PlanetaryPin td : toDelete) {
                                 deleteSet.add(td.getCid());
                               }
                               List<PlanetaryPin> allPins = getPins(next, AttributeSelector.values(toUpdate.getPlanetID()), AttributeSelector.values(toUpdate.getPinID()));
                               for (PlanetaryPin td : allPins) {
                                 if (deleteSet.contains(td.getCid())) {
                                   deleteList.add(td);

                                   for (Map.Entry<String, String> meta : td.getAllMetaData()) {
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
                                 }
                               }


                               // Delete the old data
                               for (PlanetaryPin td : deleteList) {
                                 EveKitUserAccountProvider.getFactory()
                                                          .getEntityManager()
                                                          .remove(td);
                               }

                               // Merge new data
                               toMerge.setLifeEnd(newLifeEnd);
                               CachedData.update(toMerge);

                             });

  }


  private static List<PlanetaryPin> getPins(SynchronizedEveAccount next, AttributeSelector planetID,
                                            AttributeSelector pinID) throws IOException {
    return retrieveAll((long contid, AttributeSelector at) -> PlanetaryPin.accessQuery(next,
                                                                                       contid,
                                                                                       1000,
                                                                                       false,
                                                                                       at,
                                                                                       planetID,
                                                                                       pinID,
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
      for (PlanetaryPin existing : getPins(next, ANY_SELECTOR, ANY_SELECTOR)) {
        planetPins.add(Pair.of(existing.getPlanetID(), existing.getPinID()));
      }

      // Now cycle through each (planet, pin) pair in timeline order and merge any equivalent adjacent members.
      System.out.println("Character has " + planetPins.size() + " pins");
      int merged = 0;
      for (Pair<Integer, Long> nextPair : planetPins) {
        System.out.print("+");
        System.out.flush();
        List<PlanetaryPin> allPins = getPins(next, AttributeSelector.values(
            nextPair.getLeft()),
                                             AttributeSelector.values(
                                                 nextPair.getRight()));
        if (allPins.size() <= 1) continue;
        PlanetaryPin current = allPins.get(0);
        long currentLifeEnd = current.getLifeEnd();
        List<PlanetaryPin> toDelete = new ArrayList<>();
        for (PlanetaryPin nextPin : allPins.subList(1, allPins.size())) {
          if (currentLifeEnd == nextPin.getLifeStart() && current.equivalent(nextPin)) {
            toDelete.add(nextPin);
            currentLifeEnd = nextPin.getLifeEnd();
          } else {
            // Handle any pending merges
            //noinspection Duplicates
            if (!toDelete.isEmpty()) {
              mergePins(next, current, currentLifeEnd, toDelete);
              merged += toDelete.size();
              System.out.print(".");
              System.out.flush();
            }

            // Reset and advance current pin
            toDelete.clear();
            current = nextPin;
            currentLifeEnd = current.getLifeEnd();
          }
        }

        // It's possible that all pins were merged, check that here.
        //noinspection Duplicates
        if (!toDelete.isEmpty()) {
          mergePins(next, current, currentLifeEnd, toDelete);
          merged += toDelete.size();
          System.out.print(".");
          System.out.flush();
        }
      }
      System.out.println("\nMerged " + merged + " pins");
    }
  }

}
