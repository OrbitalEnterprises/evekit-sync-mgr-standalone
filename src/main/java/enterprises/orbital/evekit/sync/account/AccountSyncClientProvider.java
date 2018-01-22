package enterprises.orbital.evekit.sync.account;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.*;
import enterprises.orbital.eve.esi.client.invoker.ApiClient;
import enterprises.orbital.evekit.model.ESIAccountClientProvider;
import enterprises.orbital.evekit.model.ESIRefClientProvider;

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

}
