package com.cleo.labs.connector.sharepoint;

import static org.junit.Assert.*;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class TestPaths {

    @Test
    public void test() {
        Path path = Paths.get("a/b/c");
        assertEquals(3, path.getNameCount());
        assertEquals("a/b", path.getParent().toString());
        assertFalse(path.isAbsolute());
        path = Paths.get("/a/b/c/");
        assertEquals(3, path.getNameCount());
        assertEquals("/a/b", path.getParent().toString());
        assertTrue(path.isAbsolute());
        Path root = Paths.get("");
        System.out.println(root.toString());
        assertEquals(1, root.getNameCount());
        assertTrue(root.getName(0).toString().isEmpty());
        assertFalse(root.isAbsolute());
        root = Paths.get("/");
        assertEquals(0, root.getNameCount());
        assertTrue(root.isAbsolute());
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
        return Paths.get(slash(s));
    }

    @Test
    public void path() {
        assertEquals("/", p("").toString());
        assertEquals("/", p("/").toString());
        assertEquals(0, p("").getNameCount());
        assertEquals(0, p("/").getNameCount());
        assertEquals("/a", p("a").toString());
        assertEquals("/a", p("/a").toString());
        assertEquals("/a", p("a/").toString());
        assertEquals("/a", p("/a/").toString());
        assertEquals(1, p("a").getNameCount());
        assertEquals(1, p("/a").getNameCount());
        assertEquals(1, p("a/").getNameCount());
        assertEquals(1, p("/a/").getNameCount());
    }
}
