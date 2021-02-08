package com.squareup.duktape;

import java.io.Closeable;
import java.io.IOException;

@SuppressWarnings("all")
public class Duktape implements Closeable {

    public static Duktape create() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public synchronized void close() throws IOException {
        throw new RuntimeException("Stub!");
    }

    public synchronized Object evaluate(String script) {
        throw new RuntimeException("Stub!");
    }

    public synchronized <T> void set(String name, Class<T> type, T object) {
        throw new RuntimeException("Stub!");
    }

}