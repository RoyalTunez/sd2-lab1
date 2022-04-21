import actors.MasterActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.xebialabs.restito.semantics.Action;
import com.xebialabs.restito.server.StubServer;
import org.glassfish.grizzly.http.Method;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.xebialabs.restito.semantics.Action.delay;
import static org.junit.Assert.*;

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.semantics.Condition.method;
import static com.xebialabs.restito.semantics.Condition.startsWithUri;

public class ActorsTest {
    private static final int PORT = 32453;

    @Test
    public void manyResultsTest() {
        withStubServer(PORT, s -> {
            whenHttp(s)
                    .match(method(Method.GET), startsWithUri("/yandex"))
                    .then(Action.stringContent(readResponseContent("manyResultsTestYandex.json")));

            whenHttp(s)
                    .match(method(Method.GET), startsWithUri("/google"))
                    .then(Action.stringContent(readResponseContent("manyResultsTestGoogle.json")));

            whenHttp(s)
                    .match(method(Method.GET), startsWithUri("/bing"))
                    .then(Action.stringContent(readResponseContent("manyResultsTestBing.json")));

            Map<String, String> urlPrefixes = Map.of(
                    "yandex", "http://localhost:" + PORT + "/yandex/search?text=",
                    "google", "http://localhost:" + PORT + "/google/search?q=",
                    "bing", "http://localhost:" + PORT + "/bing/search?q="
            );

            ActorSystem system = ActorSystem.create("MySystem");

            var wrapper = new Object() {
                final Map<String, List<String>> results = new HashMap<>();
            };

            ActorRef master = system.actorOf(
                    Props.create(
                            MasterActor.class,
                            3, 5_000_000_000L, urlPrefixes,
                            (Consumer<Map<String, List<String>>>) (wrapper.results::putAll)
                    ),
                    "master"
            );

            master.tell("Hello world", ActorRef.noSender());

            try {
                Await.result(system.whenTerminated(), Duration.Inf());
            } catch (Exception e) {
                e.printStackTrace();
            }

            assertTrue("Should contain yandex results", wrapper.results.containsKey("yandex"));
            assertTrue("Should contain google results", wrapper.results.containsKey("google"));
            assertTrue("Should contain bing results", wrapper.results.containsKey("bing"));
            assertEquals("Should not contain any other results", 3, wrapper.results.size());
            assertEquals(
                    "Yandex results",
                    List.of(
                            "Y. Hello world is a first program",
                            "Y. Hello world i am a robot",
                            "Y. There is so much thing in this world i would like to investigate"
                    ),
                    wrapper.results.get("yandex")
            );

            assertEquals(
                    "Google results",
                    List.of(
                            "G. Hello world is a first program",
                            "G. Hello world i am a robot",
                            "G. There is so much thing in this world i would like to investigate"
                    ),
                    wrapper.results.get("google")
            );

            assertEquals(
                    "Bing results",
                    List.of(
                            "B. Hello world is a first program",
                            "B. Hello world i am a robot",
                            "B. There is so much thing in this world i would like to investigate"
                    ),
                    wrapper.results.get("bing")
            );
        });
    }

    @Test
    public void timeoutTest() {
        withStubServer(PORT, s -> {
            whenHttp(s)
                    .match(method(Method.GET), startsWithUri("/yandex"))
                    .then(Action.stringContent(readResponseContent("manyResultsTestYandex.json")));

            whenHttp(s)
                    .match(method(Method.GET), startsWithUri("/google"))
                    .then(Action.stringContent(readResponseContent("manyResultsTestGoogle.json")));

            whenHttp(s)
                    .match(method(Method.GET), startsWithUri("/bing"))
                    .then(delay(12000), Action.stringContent(readResponseContent("manyResultsTestBing.json")));

            Map<String, String> urlPrefixes = Map.of(
                    "yandex", "http://localhost:" + PORT + "/yandex/search?text=",
                    "google", "http://localhost:" + PORT + "/google/search?q=",
                    "bing", "http://localhost:" + PORT + "/bing/search?q="
            );

            ActorSystem system = ActorSystem.create("MySystem");

            var wrapper = new Object() {
                final Map<String, List<String>> results = new HashMap<>();
            };

            ActorRef master = system.actorOf(
                    Props.create(
                            MasterActor.class,
                            3, 5_000_000_000L, urlPrefixes,
                            (Consumer<Map<String, List<String>>>) (wrapper.results::putAll)
                    ),
                    "master"
            );

            master.tell("Hello world", ActorRef.noSender());

            try {
                Await.result(system.whenTerminated(), Duration.Inf());
            } catch (Exception e) {
                e.printStackTrace();
            }

            assertTrue("Should contain yandex results", wrapper.results.containsKey("yandex"));
            assertTrue("Should contain google results", wrapper.results.containsKey("google"));
            assertTrue("Should contain deadLetters", wrapper.results.containsKey("deadLetters"));
            assertEquals("Should not contain any other results", 3, wrapper.results.size());
            assertEquals(
                    "Yandex results",
                    List.of(
                            "Y. Hello world is a first program",
                            "Y. Hello world i am a robot",
                            "Y. There is so much thing in this world i would like to investigate"
                    ),
                    wrapper.results.get("yandex")
            );

            assertEquals(
                    "Google results",
                    List.of(
                            "G. Hello world is a first program",
                            "G. Hello world i am a robot",
                            "G. There is so much thing in this world i would like to investigate"
                    ),
                    wrapper.results.get("google")
            );

            assertEquals(
                    "Dead Letters",
                    List.of(
                            "Unable to access site within time limit"
                    ),
                    wrapper.results.get("deadLetters")
            );
        });
    }

    String readResponseContent(String name) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(this.getClass().getResourceAsStream(name))));

        return reader.lines().collect(Collectors.joining("\n"));
    }

    private void withStubServer(int port, Consumer<StubServer> callback) {
        StubServer stubServer = null;

        try {
            stubServer = new StubServer(port).run();
            
            callback.accept(stubServer);
        } finally {
            if (stubServer != null) {
                stubServer.stop();
            }
        }
    }
}
