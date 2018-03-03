package enterprises.orbital.evekit.sync.account;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.*;
import enterprises.orbital.eve.esi.client.invoker.ApiClient;
import enterprises.orbital.evekit.model.ESIAccountClientProvider;

import java.util.concurrent.ExecutorService;

/**
 * API client provider for sync requests.  This provider is threading friendly and provides
 * a separate ApiClient for each API getter.  Threaded requests should request a separate API
 * instance for each thread.
 */
public class AccountSyncClientProvider implements ESIAccountClientProvider {
  // User agent property
  private static final String PROP_USER_AGENT = "enterprises.orbital.evekit.esi.user_agent";
  private static final String DEF_USER_AGENT = "unknown-agent";

  // Client connection timeout in milliseconds
  private static final String PROP_CONNECT_TIMEOUT = "enterprises.orbital.evekit.esi.timeout.connect";
  private static final long DEF_CONNECT_TIMEOUT = 60000L;

  private final ExecutorService scheduler;

  AccountSyncClientProvider(ExecutorService scheduler) {
    this.scheduler = scheduler;
  }

  private ApiClient generateClient() {
    ApiClient client = new ApiClient();
    client.setUserAgent(OrbitalProperties.getGlobalProperty(PROP_USER_AGENT, DEF_USER_AGENT));
    client.setConnectTimeout((int) OrbitalProperties.getLongGlobalProperty(PROP_CONNECT_TIMEOUT, DEF_CONNECT_TIMEOUT));
    return client;
  }

  @Override
  public ExecutorService getScheduler() {
    return scheduler;
  }

  @Override
  public WalletApi getWalletApi() {
    WalletApi api = new WalletApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public CharacterApi getCharacterApi() {
    CharacterApi api = new CharacterApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public CorporationApi getCorporationApi() {
    CorporationApi api = new CorporationApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public AssetsApi getAssetsApi() {
    AssetsApi api = new AssetsApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public MarketApi getMarketApi() {
    MarketApi api = new MarketApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public IndustryApi getIndustryApi() {
    IndustryApi api = new IndustryApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public ContractsApi getContractsApi() {
    ContractsApi api = new ContractsApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public LocationApi getLocationApi() {
    LocationApi api = new LocationApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public BookmarksApi getBookmarksApi() {
    BookmarksApi api = new BookmarksApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public KillmailsApi getKillmailsApi() {
    KillmailsApi api = new KillmailsApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public ClonesApi getClonesApi() {
    ClonesApi api = new ClonesApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public SkillsApi getSkillsApi() {
    SkillsApi api = new SkillsApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public MailApi getMailApi() {
    MailApi api = new MailApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public ContactsApi getContactsApi() {
    ContactsApi api = new ContactsApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public FactionWarfareApi getFactionWarfareApi() {
    FactionWarfareApi api = new FactionWarfareApi();
    api.setApiClient(generateClient());
    return api;
  }

}
