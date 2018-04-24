package com.cleo.labs.connector.sharepoint;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;

public class Path {

    static public final String DELIMITER = "/";
    
    private List<String> nodes;

    private Path(List<String> nodes) {
        this.nodes = nodes;
    }

    public Path(String...parse) {
        this.nodes = new ArrayList<>();
        if (parse != null) {
            for (String node : parse) {
                if (!Strings.isNullOrEmpty(node)) {
                    for (String element : node.split(DELIMITER)) {
                        if (!element.isEmpty()) {
                            nodes.add(element);
                        }
                    }
                }
            }
        }
    }

    public int size() {
        return nodes.size();
    }

    public boolean empty() {
        return nodes.isEmpty();
    }

    public Path parent() {
        if (nodes.isEmpty()) {
            return this;
        } else {
            return new Path(nodes.subList(0, nodes.size()-1));
        }
    }

    public Path child(String node) {
        List<String> child = new ArrayList<>(nodes);
        child.add(node);
        return new Path(child);
    }

    public Path child(Path path) {
        List<String> child = new ArrayList<>(nodes);
        child.addAll(path.nodes);
        return new Path(child);
    }

    public String name() {
        if (nodes.isEmpty()) {
            return "";
        }
        return nodes.get(nodes.size()-1);
    }

    @Override
    public String toString() {
        return Joiner.on(DELIMITER).join(nodes);
    }
}
