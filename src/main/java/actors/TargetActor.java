package actors;

import akka.actor.UntypedActor;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TargetActor extends UntypedActor {
    private final String urlPrefix;
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    int top;

    public TargetActor(String url, int top) {
        this.urlPrefix = url;
        this.top = top;
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof String) {
            HttpGet request = new HttpGet(urlPrefix + URLEncoder.encode((String) message, StandardCharsets.UTF_8));
            request.addHeader(HttpHeaders.USER_AGENT, "Actor");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                HttpEntity entity = response.getEntity();
                JSONObject obj = (JSONObject) new JSONParser().parse(EntityUtils.toString(entity));
                JSONArray arr = (JSONArray) obj.get("results");
                String[] results = new String[Math.min(arr.size(), top)];

                for (int i = 0; i < results.length; i++) {
                    results[i] = (String) arr.get(i);
                }

                sender().tell(results, self());
            }
        }
    }
}
