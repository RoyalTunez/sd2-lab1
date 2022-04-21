import actors.MasterActor;
import actors.TargetActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Main {
    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("MySystem");

        var wrapper = new Object() {
            final Map<String, List<String>> results = new HashMap<>();
        };

        Map<String, String> urlPrefixes = Map.of(
                "yandex", "https://yandex.ru/search?lr=2&text=",
                "google", "https://www.google.com/search?q=",
                "bing", "https://www.bing.com/search?q="
        );

        ActorRef master = system.actorOf(
                Props.create(
                        MasterActor.class,
                        5, 5_000_000_000L, urlPrefixes,
                        (Consumer<Map<String, List<String>>>)(wrapper.results::putAll)
                ),
                "master"
        );

        master.tell(args[0], ActorRef.noSender());

        try {
            Await.result(system.whenTerminated(), Duration.Inf());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
