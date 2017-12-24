package enterprises.orbital.evekit.sync.ref;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.StatusApi;
import enterprises.orbital.eve.esi.client.invoker.ApiClient;
import enterprises.orbital.evekit.model.ESIClientProvider;

public class RefSyncClientProvider implements ESIClientProvider {
  // User agent property
  public static final String PROP_USER_AGENT = "enterprises.orbital.evekit.esi.user_agent";
  public static final String DEF_USER_AGENT = "unknown-agent";

  // Client connection timeout in milliseconds
  public static final String PROP_CONNECT_TIMEOUT = "enterprises.orbital.evekit.esi.timeout.connect";
  public static final long DEF_CONNECT_TIMEOUT = 60000L;

  protected ApiClient client;

  public RefSyncClientProvider() {
    client = new ApiClient();
    client.setUserAgent(OrbitalProperties.getGlobalProperty(PROP_USER_AGENT, DEF_USER_AGENT));
    client.setConnectTimeout((int) OrbitalProperties.getLongGlobalProperty(PROP_CONNECT_TIMEOUT, DEF_CONNECT_TIMEOUT));
  }

  @Override
  public StatusApi getStatusApi() {
    StatusApi api = new StatusApi();
    api.setApiClient(client);
    return api;
  }
}
