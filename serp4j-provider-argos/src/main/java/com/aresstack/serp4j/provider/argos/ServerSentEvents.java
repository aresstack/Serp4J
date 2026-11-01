package com.aresstack.serp4j.provider.argos;

import java.util.ArrayList;
import java.util.List;

/** Minimal reader for {@code text/event-stream} bodies: returns the joined {@code data} of every event. */
final class ServerSentEvents {

    private ServerSentEvents() {
    }

    static List<String> dataOf(String body) {
        List<String> events = new ArrayList<String>();
        StringBuilder data = null;
        for (String line : body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (line.isEmpty()) {
                if (data != null) {
                    events.add(data.toString());
                    data = null;
                }
            } else if (line.startsWith("data:")) {
                String value = line.substring(5);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                if (data == null) {
                    data = new StringBuilder(value);
                } else {
                    data.append('\n').append(value);
                }
            }
        }
        if (data != null) {
            events.add(data.toString());
        }
        return events;
    }
}
