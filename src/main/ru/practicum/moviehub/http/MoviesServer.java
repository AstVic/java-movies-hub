package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private final HttpServer server;
    private final MoviesStore store;

    public MoviesServer(MoviesStore moviesServer, int port) {
        this.store = new MoviesStore();
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать HTTP-сервер", e);
        }
        server.createContext("/movies", new MoviesHandler(store));
        server.createContext("/movies/", new MovieByIdHandler(store));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public void clearStore() {
        store.clear();
    }
}
