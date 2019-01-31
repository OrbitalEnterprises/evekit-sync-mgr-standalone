package enterprises.orbital.evekit.sync.account;

import enterprises.orbital.base.NoPersistentPropertyException;
import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.sync.ControllerEvent;
import enterprises.orbital.evekit.sync.EventScheduler;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Periodic event to check for any expired refresh tokens.  If such a token exists, and the user
 * which owns the token has configured an alert address, then send a note indicating that the
 * token has expired.
 */
public class ESICheckExpiredTokenEvent extends ControllerEvent implements Runnable {
  public static final Logger log = Logger.getLogger(ESICheckExpiredTokenEvent.class.getName());

  private static final String PROP_ALERT_SOURCE_ADDRESS = "enterprises.orbital.evekit.sync_mgr.alert_source_address";
  private static final String DEF_ALERT_SOURCE_ADDRESS = "deadlybulb@orbital.enterprises";

  private static final String PROP_ALERT_CHECK_DELAY = "enterprises.orbital.evekit.sync_mgr.alert_check_delay";
  private static final long DEF_ALERT_CHECK_DELAY = TimeUnit.MILLISECONDS.convert(4, TimeUnit.HOURS);

  private static final String PROP_GMAIL_ACCOUNT_ADDRESS = "enterprises.orbital.evekit.sync_mgr.alert_gmail_address";
  private static final String PROP_GMAIL_ACCOUNT_PASSWORD = "enterprises.orbital.evekit.sync_mgr.alert_gmail_password";

  private EventScheduler scheduler;
  private ScheduledExecutorService taskScheduler;

  ESICheckExpiredTokenEvent(EventScheduler scheduler, ScheduledExecutorService taskScheduler) {
    this.scheduler = scheduler;
    this.taskScheduler = taskScheduler;
  }

  @Override
  public long maxDelayTime() {
    return TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);
  }

  @Override
  public String toString() {
    return "ESICheckExpiredTokenEvent{" +
        "scheduler=" + scheduler +
        ", taskScheduler=" + taskScheduler +
        '}';
  }

  @Override
  public void run() {
    log.fine("Starting execution: " + toString());
    super.run();
    // Iterate over all user accounts, checking for expired ESI tokens
    try {
      for (EveKitUserAccount next : EveKitUserAccount.getAllAccounts()) {
        try {
          String contactAddress = PersistentProperty.getProperty(next,
                                                                 EveKitUserAccount.PERPROP_ESI_EXPIRE_CONTACT_ADDRESS);
          contactAddress = contactAddress.trim();
          if (contactAddress.length() == 0 || contactAddress.indexOf('@') == -1)
            // Invalid address
            continue;

          // Iterate through ESI tokens looking for tokens needing re-authorization.
          try {
            List<SynchronizedEveAccount> needsReauth = SynchronizedEveAccount.getAllAccounts(next, false)
                                                                             .stream()
                                                                             .filter(x -> {
                                                                               x.updateValid();
                                                                               return x.getEveCharacterID() > 0 && !x.isValid();
                                                                             })
                                                                             .collect(Collectors.toList());

            // If any accounts need to be re-authorized, send e-mail to the appropriate contact address
            if (!needsReauth.isEmpty()) {
              log.info("Sending expired token warning for to " + contactAddress);
              Properties props = System.getProperties();
              String gmailHost = "smtp.gmail.com";
              String gmailSource = OrbitalProperties.getGlobalProperty(PROP_GMAIL_ACCOUNT_ADDRESS);
              String gmailPassword = OrbitalProperties.getGlobalProperty(PROP_GMAIL_ACCOUNT_PASSWORD);
              props.put("mail.smtps.host", gmailHost);
              props.put("mail.smtps.auth", "true");
              Session session = Session.getInstance(props, null);
              Message msg = new MimeMessage(session);
              try {
                InternetAddress srcAddr = new InternetAddress(
                    OrbitalProperties.getGlobalProperty(PROP_ALERT_SOURCE_ADDRESS, DEF_ALERT_SOURCE_ADDRESS));
                msg.setFrom(srcAddr);
                msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(contactAddress, false));
                msg.setRecipients(Message.RecipientType.BCC, new InternetAddress[] { srcAddr });
                msg.setSubject("EveKit: you have ESI access tokens which need to be re-authorized");
                StringBuilder msgText = new StringBuilder();
                msgText.append("Dear EveKit user:\n\n");
                msgText.append("The following synchronized accounts have ESI access tokens which need to be ")
                       .append("re-authorized.  Most account data will not synchronize until these tokens ")
                       .append("have been re-authorized.  Please visit https://evekit.orbital.enterprises ")
                       .append("and re-authorize the ESI token for the appropriate account.  ")
                       .append("If you wish to stop receiving these alerts, select Settings ")
                       .append("and clear the 'Expired ESI Token Contact Address' field.\n\n")
                       .append("Accounts to Re-Authorize:\n\n");
                for (SynchronizedEveAccount acct : needsReauth)
                  msgText.append("\t")
                         .append(acct.getName())
                         .append("\n");
                msg.setText(msgText.toString());
                Transport transport = session.getTransport("smtps");
                transport.connect(gmailHost, gmailSource, gmailPassword);
                transport.sendMessage(msg, msg.getAllRecipients());
                transport.close();
              } catch (Exception e) {
                // Anything here is fatal, we'll skip this user
                log.log(Level.WARNING, "Failed to send alerts for user " + next + ", skipping check", e);
              }
            }

          } catch (IOException e) {
            // Skip this user, but log
            log.log(Level.WARNING, "Failed to retrieve accounts for user " + next + ", skipping check", e);
          }
        } catch(NoPersistentPropertyException e) {
          // Skip if property not set
          //noinspection UnnecessaryContinue
          continue;
        }
      }
    } catch (IOException e) {
      // Give up if we can't retrieve the list of user accounts
      log.log(Level.WARNING, "Failed to retrieve list of user accounts, skipping check", e);
    }

    // Schedule next check.  Account scheduler will ensure one of these events always exists.
    long delay = OrbitalProperties.getLongGlobalProperty(PROP_ALERT_CHECK_DELAY, DEF_ALERT_CHECK_DELAY);
    log.fine("Scheduling event to occur in " + delay + " milliseconds");
    ControllerEvent ev = new ESICheckExpiredTokenEvent(scheduler, taskScheduler);
    ev.setTracker(taskScheduler.schedule(ev, delay, TimeUnit.MILLISECONDS));
    scheduler.pending.add(ev);

    log.fine("Execution complete: " + toString());
  }

}