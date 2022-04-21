package actors;

import akka.actor.*;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MasterActor extends UntypedActor {
    Map<String, List<String>> results = new HashMap<>();
    Map<String, String> urlPrefixes;
    List<ActorRef> targetActors = new ArrayList<>();
    Consumer<Map<String, List<String>>> processResults;
    int top;
    Duration timeout;

    public MasterActor(int top,
                       long timeout,
                       Map<String, String> urlPrefixes,
                       Consumer<Map<String, List<String>>> processResults) {
        this.top = top;
        this.timeout = Duration.fromNanos(timeout);
        this.urlPrefixes = urlPrefixes;
        this.processResults = processResults;

        for (Map.Entry<String, String> entry : urlPrefixes.entrySet()) {
            targetActors.add(getContext().actorOf(Props.create(TargetActor.class, entry.getValue(), top), entry.getKey()));
        }
    }

    @Override
    public void onReceive(Object message) {
        if (message instanceof String) {
            getContext().setReceiveTimeout(timeout);

            for (ActorRef actor : targetActors) {
                actor.tell(message, self());
            }

            return;
        }

        if (message instanceof ReceiveTimeout) {
            results.put(sender().path().name(), List.of("Unable to access site within time limit"));
        } else if (message instanceof String[]) {
            results.put(sender().path().name(), List.of((String[]) message));
        }

        if (results.size() == urlPrefixes.size()) {
            sender().tell(results, self());

            for (ActorRef actor : targetActors) {
                getContext().stop(actor);
            }

            processResults.accept(results);
            getContext().stop(self());
            getContext().system().terminate();
        }
    }
}
