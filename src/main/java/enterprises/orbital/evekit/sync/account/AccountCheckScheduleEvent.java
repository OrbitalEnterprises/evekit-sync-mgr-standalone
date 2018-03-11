package enterprises.orbital.evekit.sync.account;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.AbstractESIAccountSync;
import enterprises.orbital.evekit.model.ESIEndpointSyncTracker;
import enterprises.orbital.evekit.model.ESISyncEndpoint;
import enterprises.orbital.evekit.model.character.sync.*;
import enterprises.orbital.evekit.model.corporation.sync.*;
import enterprises.orbital.evekit.sync.ControllerEvent;
import enterprises.orbital.evekit.sync.EventScheduler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodic event which does the following:
 * <p>
 * <ul>
 * <li>Verify an unfinished scheduled sync tracker exists for every non-excluded ESI endpoint for every account</li>
 * <li>Verify an event has been queued for every unfinished scheduled sync tracker</li>
 * <li>Queue a new instance of this checker when complete.</li>
 * </ul>
 * <p>
 * Note that events are not scheduled if the corresponding account lacks the scopes required for a particular
 * endpoint.  The event handler is expected to handle the case where a sync account is removed or suspended
 * before the event has a chance to be scheduled.
 */
public class AccountCheckScheduleEvent extends ControllerEvent {
  public static final Logger log = Logger.getLogger(AccountCheckScheduleEvent.class.getName());

  // Max delay for reference check schedule event
  private static final String PROP_MAX_DELAY = "enterprises.orbital.evekit.sync_mgr.max_delay.sync_check_schedule";
  private static final long DEF_MAX_DELAY = TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);

  // Time between check schedule events
  private static final String PROP_CYCLE_DELAY = "enterprises.orbital.evekit.sync_mgr.sync_check_schedule.cycle";
  private static final long DEF_CYCLE_DELAY = TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);

  private long maxDelay;
  private EventScheduler eventScheduler;
  private ScheduledExecutorService taskScheduler;

  AccountCheckScheduleEvent(EventScheduler eventScheduler, ScheduledExecutorService taskScheduler) {
    this.eventScheduler = eventScheduler;
    this.taskScheduler = taskScheduler;
    this.maxDelay = PersistentProperty.getLongPropertyWithFallback(PROP_MAX_DELAY, DEF_MAX_DELAY);
  }

  @Override
  public long maxDelayTime() {
    return maxDelay;
  }

  @Override
  public String toString() {
    return "AccountCheckScheduleEvent{" +
        "maxDelay=" + maxDelay +
        ", eventScheduler=" + eventScheduler +
        ", taskScheduler=" + taskScheduler +
        '}';
  }

  /**
   * Check whether an unfinished event exists for the given tracker.  We assume the tracker is handled by an instance
   * of ESIStandardAccountSyncEvent.
   *
   * @param tracker the tracker to check
   * @return true if an event for the given tracker is queued and is not done, false otherwise.
   */
  private boolean hasUnfinishedEvent(ESIEndpointSyncTracker tracker) {
    ESISyncEndpoint ep = tracker.getEndpoint();
    SynchronizedEveAccount account = tracker.getAccount();
    synchronized (eventScheduler.pending) {
      for (ControllerEvent next : eventScheduler.pending) {
        if (next instanceof ESIStandardAccountSyncEvent) {
          ESIStandardAccountSyncEvent check = (ESIStandardAccountSyncEvent) next;
          if (check.getEndpoint() == ep &&
              check.getHandler()
                   .account()
                   .equals(account) &&
              !check.getTracker()
                    .isDone())
            return true;
        }
      }
    }
    return false;
  }

  /**
   * Schedule a controller event for execution.
   *
   * @param ev        the event to schedule.
   * @param eventTime time when this event should dispatch
   */
  private void scheduleEvent(ControllerEvent ev, long eventTime) {
    long delay = Math.max(0L, eventTime - OrbitalProperties.getCurrentTime());
    log.fine("Scheduling event to occur in " + delay + " milliseconds");
    ev.setTracker(taskScheduler.schedule(ev, delay, TimeUnit.MILLISECONDS));
    eventScheduler.pending.add(ev);
  }

  @Override
  public void run() {
    log.fine("Starting execution: " + toString());
    super.run();

    // Construct set of excluded endpoints, further refined by endpoints for which we don't yet have handlers.
    Set<ESISyncEndpoint> excluded = AbstractESIAccountSync.getExcludedEndpoints();
    for (ESISyncEndpoint check : ESISyncEndpoint.values()) {
      if (!handlerDeploymentMap.containsKey(check)) excluded.add(check);
    }

    // Ensure unfinished sync trackers exists for:
    //
    // - all non-excluded endpoints
    // - for non-disabled synch accounts which have not been marked for deletion
    // - for sync accounts which have the required scopes for the endpoint (note: some accounts may not have ESI creds)
    //
    for (ESISyncEndpoint check : ESISyncEndpoint.values()) {
      log.fine("Starting tracker check for: " + check);

      // Skip excluded by property
      if (excluded.contains(check)) {
        log.fine("Skipping excluded endpoint: " + check);
        continue;
      }

      // Now iterate through all users and sync accounts
      try {
        for (EveKitUserAccount nextUser : EveKitUserAccount.getAllAccounts()) {
          // Skip disabled users
          if (!nextUser.isActive())
            continue;

          // Iterate over non-deleted accounts for this user
          try {
            for (SynchronizedEveAccount nextAccount : SynchronizedEveAccount.getAllAccounts(nextUser, false)) {
              // Skip disabled sync accounts
              if (PersistentProperty.getBooleanPropertyWithFallback(nextAccount, "disabled", false)) {
                log.fine("Sync disabled for account, skipping: " + nextAccount);
                continue;
              }

              // Skip accounts which have no assigned credentials
              if (nextAccount.getEveCharacterID() == -1) {
                log.fine("Account has no credentials, skipping: " + nextAccount);
                continue;
              }

              try {
                // Verify scope then check for unfinished sync tracker
                // Note that scope may be null for endpoints which don't require a scope
                // for access.
                if (check.getScope() != null &&  !nextAccount.hasScope(check.getScope()
                                                                            .getName()))
                  continue;
                ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(nextAccount, check,
                                                                    OrbitalProperties.getCurrentTime());
              } catch (IOException e) {
                log.log(Level.WARNING,
                        "Error retrieving or creating unfinished tracker for endpoint: " + check + ", continuing", e);
              }
            }
          } catch (IOException e) {
            log.log(Level.WARNING,
                    "Error retrieving sync accounts for user: " + nextUser + ", skipping user for this cycle", e);
          }
        }

      } catch (IOException e) {
        log.log(Level.WARNING, "Error retrieving user list, skipping sync tracker check for this cycle", e);
      }
    }

    // Ensure every unfinished sync tracker has a queued, unfinished controller event.
    try {
      for (ESIEndpointSyncTracker nextTracker : ESIEndpointSyncTracker.getAllUnfinishedTrackers()) {
        log.fine("Verifying event exists for: " + nextTracker);

        // Make sure an unfinished controller event exists for this tracker.
        // If not, queue the event at the scheduled start time for the sync tracker.
        if (!hasUnfinishedEvent(nextTracker)) {
          log.fine("Scheduling sync event for " + nextTracker);
          long startTime = nextTracker.getScheduled();
          ESISyncEndpoint ep = nextTracker.getEndpoint();
          scheduleEvent(new ESIStandardAccountSyncEvent(ep, handlerDeploymentMap.get(ep)
                                                                                .generate(nextTracker.getAccount()),
                                                        taskScheduler), startTime);
        }
      }
    } catch (IOException e) {
      log.log(Level.WARNING, "Error retrieving unfinished tracker list, skipping check for this cycle", e);
    }

    // Requeue ourselves for a future invocation
    long executionDelay = PersistentProperty.getLongPropertyWithFallback(PROP_CYCLE_DELAY, DEF_CYCLE_DELAY);
    scheduleEvent(new AccountCheckScheduleEvent(eventScheduler, taskScheduler),
                  OrbitalProperties.getCurrentTime() + executionDelay);
    log.fine("Execution finished: " + toString());
  }

  // Inner class describing configuration of sync handlers
  protected interface SyncHandlerGenerator<A extends AbstractESIAccountSync> {
    A generate(SynchronizedEveAccount account);
  }

  // Sync handler deployment map
  private static Map<ESISyncEndpoint, SyncHandlerGenerator> handlerDeploymentMap = new HashMap<>();

  static {
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_WALLET_BALANCE, ESICharacterWalletBalanceSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CORP_WALLET_BALANCE, ESICorporationWalletBalanceSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_AGENTS, ESICharacterResearchAgentSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_WALLET_JOURNAL, ESICharacterWalletJournalSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CORP_WALLET_JOURNAL, ESICorporationWalletJournalSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_WALLET_TRANSACTIONS, ESICharacterWalletTransactionSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CORP_WALLET_TRANSACTIONS, ESICorporationWalletTransactionSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_ASSETS, ESICharacterAssetsSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CORP_ASSETS, ESICorporationAssetsSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_BLUEPRINTS, ESICharacterBlueprintsSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CORP_BLUEPRINTS, ESICorporationBlueprintsSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_MARKET, ESICharacterMarketOrderSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CORP_MARKET, ESICorporationMarketOrderSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_STANDINGS, ESICharacterStandingSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CORP_STANDINGS, ESICorporationStandingSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_INDUSTRY, ESICharacterIndustryJobSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CORP_INDUSTRY, ESICorporationIndustryJobSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_CONTRACTS, ESICharacterContractsSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CORP_CONTRACTS, ESICorporationContractsSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CORP_CONTAINER_LOGS, ESICorporationContainerLogSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_LOCATION, ESICharacterLocationSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_SHIP_TYPE, ESICharacterShipSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_ONLINE, ESICharacterOnlineSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_BOOKMARKS, ESICharacterBookmarksSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CORP_BOOKMARKS, ESICorporationBookmarksSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_KILL_MAIL, ESICharacterKillMailSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CORP_KILL_MAIL, ESICorporationKillMailSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_CLONES, ESICharacterSheetCloneSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_IMPLANTS, ESICharacterSheetImplantsSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_FATIGUE, ESICharacterSheetJumpSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_SHEET, ESICharacterSheetSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_SKILL_QUEUE, ESICharacterSkillInQueueSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_SKILLS, ESICharacterSkillsSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_MAIL, ESICharacterMailSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_CONTACTS, ESICharacterContactsSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CORP_CONTACTS, ESICorporationContactsSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_FACTION_WAR, ESICharacterFacWarStatsSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CORP_FACTION_WAR, ESICorporationFacWarStatsSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_PLANETS, ESICharacterPlanetsSync::new);
    handlerDeploymentMap.put(ESISyncEndpoint.CHAR_CALENDAR, ESICharacterCalendarSync::new);
  }

}