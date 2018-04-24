package com.cleo.labs.connector.sharepoint;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestPath {

    @Test
    public void test() {
        Path path = new Path("a/b/c");
        assertEquals(3, path.size());
        assertEquals("a/b", path.parent().toString());
        path = new Path("/a/b/c/");
        assertEquals(3, path.size());
        assertEquals("a/b", path.parent().toString());
        Path root = new Path("");
        System.out.println(root.toString());
        assertEquals(0, root.size());
        root = new Path("/");
        assertEquals(0, root.size());
    }

    private String slash(String s) {
        return s.replaceFirst("^(?=[^/]|$)","/");
    }

    @Test
    public void edit() {
        assertEquals("/", slash(""));
        assertEquals("/", slash("/"));
        assertEquals("/a", slash("a"));
        assertEquals("/a", slash("/a"));
    }

    private Path p(String s) {
        return new Path(s);
    }

    @Test
    public void path() {
        assertEquals("", p("").toString());
        assertEquals("", p("/").toString());
        assertEquals(0, p("").size());
        assertEquals(0, p("/").size());
        assertEquals("a", p("a").toString());
        assertEquals("a", p("/a").toString());
        assertEquals("a", p("a/").toString());
        assertEquals("a", p("/a/").toString());
        assertEquals(1, p("a").size());
        assertEquals(1, p("/a").size());
        assertEquals(1, p("a/").size());
        assertEquals(1, p("/a/").size());
    }
}
