package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;

public class MovieByIdHandler extends BaseHttpHandler {
    private final MoviesStore store;

    public MovieByIdHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String idPart = path.substring("/movies/".length());
        Integer id = parseInt(idPart);
        if (id == null) {
            sendError(ex, 400, "Некорректный ID");
            return;
        }

        switch (method) {
            case "GET": {
                store.getById(id)
                        .map(movie -> {
                            try {
                                sendJson(ex, 200, GSON.toJson(movie));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return movie;
                        })
                        .orElseGet(() -> {
                            try {
                                sendError(ex, 404, "Фильм не найден");
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return null;
                        });
                return;
            }
            case "DELETE": {
                if (!store.deleteById(id)) {
                    sendError(ex, 404, "Фильм не найден");
                    return;
                }
                sendNoContent(ex);
                return;
            }
            default:
                sendError(ex, 405, "Метод не поддерживается");
        }
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
