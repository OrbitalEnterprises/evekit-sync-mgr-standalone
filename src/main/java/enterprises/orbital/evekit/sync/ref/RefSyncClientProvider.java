package enterprises.orbital.evekit.sync.ref;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.AllianceApi;
import enterprises.orbital.eve.esi.client.api.StatusApi;
import enterprises.orbital.eve.esi.client.invoker.ApiClient;
import enterprises.orbital.evekit.model.ESIClientProvider;

import java.util.concurrent.ExecutorService;

/**
 * API client provider for sync requests.  This provider is threading friendly and provides
 * a separate ApiClient for each API getter.  Threaded requests should request a separate API
 * instance for each thread.
 */
public class RefSyncClientProvider implements ESIClientProvider {
  // User agent property
  public static final String PROP_USER_AGENT = "enterprises.orbital.evekit.esi.user_agent";
  public static final String DEF_USER_AGENT = "unknown-agent";

  // Client connection timeout in milliseconds
  public static final String PROP_CONNECT_TIMEOUT = "enterprises.orbital.evekit.esi.timeout.connect";
  public static final long DEF_CONNECT_TIMEOUT = 60000L;

  private final ExecutorService scheduler;

  public RefSyncClientProvider(ExecutorService scheduler) {
    this.scheduler = scheduler;
  }

  protected ApiClient generateClient() {
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
  public StatusApi getStatusApi() {
    StatusApi api = new StatusApi();
    api.setApiClient(generateClient());
    return api;
  }

  @Override
  public AllianceApi getAllianceApi() {
    AllianceApi api = new AllianceApi();
    api.setApiClient(generateClient());
    return api;
  }
}
