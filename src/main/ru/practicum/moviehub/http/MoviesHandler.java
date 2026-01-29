package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.store.MoviesStore;
import ru.practicum.moviehub.model.Movie;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {
    private static final int MIN_YEAR = 1888;
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        switch (method) {
            case "GET": {
                handleGet(ex);
                return;
            }
            case "POST": {
                handlePost(ex);
                return;
            }
            default:
                sendError(ex, 405, "Метод не поддерживается");
        }
    }

    private void handleGet(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getQuery();
        if (query == null || query.isBlank()) {
            sendJson(ex, 200, GSON.toJson(store.getAll()));
            return;
        }
        String[] params = query.split("&");
        if (params.length != 1 || !params[0].startsWith("year=")) {
            sendError(ex, 400, "Некорректный параметр запроса — 'year'");
            return;
        }
        String value = params[0].substring("year=".length());
        Integer year = parseInt(value);
        if (year == null) {
            sendError(ex, 400, "Некорректный параметр запроса — 'year'");
            return;
        }
        sendJson(ex, 200, GSON.toJson(store.getByYear(year)));
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            sendError(ex, 415, "Неподдерживаемый Content-Type");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        MovieRequest request;
        try {
            request = GSON.fromJson(body, MovieRequest.class);
        } catch (Exception e) {
            sendError(ex, 400, "Некорректный JSON");
            return;
        }
        if (request == null) {
            sendError(ex, 400, "Некорректный JSON");
            return;
        }
        List<String> errors = new ArrayList<>();
        if (request.title == null || request.title.trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (request.title.length() > 100) {
            errors.add("название не должно превышать 100 символов");
        }
        int maxYear = Year.now().getValue() + 1;
        if (request.year == null || request.year < MIN_YEAR || request.year > maxYear) {
            errors.add("год должен быть между 1888 и " + maxYear);
        }
        if (!errors.isEmpty()) {
            sendValidationError(ex, errors);
            return;
        }
        Movie movie = store.add(request.title.trim(), request.year);
        sendJson(ex, 201, GSON.toJson(movie));
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
