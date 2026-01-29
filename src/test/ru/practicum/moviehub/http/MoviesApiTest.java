package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;
import ru.practicum.moviehub.api.ErrorResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static final Gson GSON = new Gson();
    private static MoviesServer server;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer(new MoviesStore(), 8080);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    void beforeEach() {
        server.clearStore();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies"))
                .build();

        HttpResponse<String> resp = send(req);

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        assertContentType(resp);
        List<Movie> movies = GSON.fromJson(resp.body(), ListOfMoviesTypeToken.getListType());
        assertEquals(0, movies.size(), "GET /movies должен вернуть пустой список");
    }

    @Test
    void postMovies_addsMovie() throws Exception {
        String json = "{\"title\":\"Inception\",\"year\":2010}";
        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> resp = send(req);

        assertEquals(201, resp.statusCode());
        assertContentType(resp);
        Movie created = GSON.fromJson(resp.body(), Movie.class);
        assertNotNull(created);
        assertEquals("Inception", created.getTitle());
        assertEquals(2010, created.getYear());

        HttpResponse<String> listResp = send(HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies"))
                .build());
        List<Movie> movies = GSON.fromJson(listResp.body(), ListOfMoviesTypeToken.getListType());
        assertEquals(1, movies.size());
        assertEquals("Inception", movies.get(0).getTitle());
    }

    @Test
    void postMovies_emptyTitle_returnsValidationError() throws Exception {
        String json = "{\"title\":\"\",\"year\":2010}";
        HttpResponse<String> resp = send(postMovies(json));

        assertEquals(422, resp.statusCode());
        assertContentType(resp);
        ErrorResponse error = parseError(resp);
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().contains("название не должно быть пустым"));
    }

    @Test
    void postMovies_longTitle_returnsValidationError() throws Exception {
        String longTitle = "a".repeat(101);
        String json = "{\"title\":\"" + longTitle + "\",\"year\":2010}";
        HttpResponse<String> resp = send(postMovies(json));

        assertEquals(422, resp.statusCode());
        assertContentType(resp);
        ErrorResponse error = parseError(resp);
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().contains("название не должно превышать 100 символов"));
    }

    @Test
    void postMovies_invalidYear_returnsValidationError() throws Exception {
        int maxYear = Year.now().getValue() + 1;
        String json = "{\"title\":\"Movie\",\"year\":" + (maxYear + 2) + "}";
        HttpResponse<String> resp = send(postMovies(json));

        assertEquals(422, resp.statusCode());
        assertContentType(resp);
        ErrorResponse error = parseError(resp);
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().contains("год должен быть между 1888 и " + maxYear));
    }

    @Test
    void postMovies_wrongContentType_returns415() throws Exception {
        String json = "{\"title\":\"Movie\",\"year\":2010}";
        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .build();

        HttpResponse<String> resp = send(req);

        assertEquals(415, resp.statusCode());
        assertContentType(resp);
        assertErrorMessage(resp, "Неподдерживаемый Content-Type");
    }

    @Test
    void postMovies_invalidJson_returnsValidationError() throws Exception {
        HttpResponse<String> resp = send(postMovies("{invalid"));

        assertEquals(400, resp.statusCode());
        assertContentType(resp);
        assertErrorMessage(resp, "Некорректный JSON");
    }

    @Test
    void getMovieById_returnsMovie() throws Exception {
        Movie created = createMovie("Interstellar", 2014);

        HttpResponse<String> resp = send(HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies/" + created.getId()))
                .build());

        assertEquals(200, resp.statusCode());
        assertContentType(resp);
        assertTrue(resp.body().contains("Interstellar"));
    }

    @Test
    void getMovieById_notFound_returns404() throws Exception {
        HttpResponse<String> resp = send(HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies/999"))
                .build());

        assertEquals(404, resp.statusCode());
        assertContentType(resp);
        assertErrorMessage(resp, "Фильм не найден");
    }

    @Test
    void getMovieById_invalidId_returns400() throws Exception {
        HttpResponse<String> resp = send(HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies/abc"))
                .build());

        assertEquals(400, resp.statusCode());
        assertContentType(resp);
        assertErrorMessage(resp, "Некорректный ID");
    }

    @Test
    void deleteMovieById_removesMovie() throws Exception {
        Movie created = createMovie("Alien", 1979);

        HttpResponse<String> resp = send(HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create(BASE + "/movies/" + created.getId()))
                .build());

        assertEquals(204, resp.statusCode());
    }

    @Test
    void deleteMovieById_notFound_returns404() throws Exception {
        HttpResponse<String> resp = send(HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create(BASE + "/movies/123"))
                .build());

        assertEquals(404, resp.statusCode());
        assertContentType(resp);
        assertErrorMessage(resp, "Фильм не найден");
    }

    @Test
    void deleteMovieById_invalidId_returns400() throws Exception {
        HttpResponse<String> resp = send(HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create(BASE + "/movies/abc"))
                .build());

        assertEquals(400, resp.statusCode());
        assertContentType(resp);
        assertErrorMessage(resp, "Некорректный ID");
    }

    @Test
    void getMoviesByYear_returnsMatches() throws Exception {
        createMovie("Movie 1", 2000);
        createMovie("Movie 2", 2001);
        createMovie("Movie 3", 2000);

        HttpResponse<String> resp = send(HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies?year=2000"))
                .build());

        assertEquals(200, resp.statusCode());
        assertContentType(resp);
        List<Movie> movies = GSON.fromJson(resp.body(), ListOfMoviesTypeToken.getListType());
        assertEquals(2, movies.size());
        assertTrue(movies.stream().anyMatch(movie -> "Movie 1".equals(movie.getTitle())));
        assertTrue(movies.stream().anyMatch(movie -> "Movie 3".equals(movie.getTitle())));
        assertTrue(movies.stream().noneMatch(movie -> "Movie 2".equals(movie.getTitle())));
    }

    @Test
    void getMoviesByYear_noMatches_returnsEmpty() throws Exception {
        createMovie("Movie 1", 2000);

        HttpResponse<String> resp = send(HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies?year=1999"))
                .build());

        assertEquals(200, resp.statusCode());
        assertContentType(resp);
        List<Movie> movies = GSON.fromJson(resp.body(), ListOfMoviesTypeToken.getListType());
        assertEquals(0, movies.size());
    }

    @Test
    void getMoviesByYear_invalidParam_returns400() throws Exception {
        HttpResponse<String> resp = send(HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .build());

        assertEquals(400, resp.statusCode());
        assertContentType(resp);
        assertErrorMessage(resp, "Некорректный параметр запроса — 'year'");
    }

    @Test
    void methodNotAllowed_returns405() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(BASE + "/movies"))
                .build();

        HttpResponse<String> resp = send(req);

        assertEquals(405, resp.statusCode());
        assertContentType(resp);
        assertErrorMessage(resp, "Метод не поддерживается");
    }

    @Test
    void postMovies_multipleValidationErrors_returnsDetails() throws Exception {
        int maxYear = Year.now().getValue() + 1;
        String json = "{\"title\":\"\",\"year\":" + (maxYear + 3) + "}";
        HttpResponse<String> resp = send(postMovies(json));

        assertEquals(422, resp.statusCode());
        assertContentType(resp);
        ErrorResponse error = parseError(resp);
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().contains("название не должно быть пустым"));
        assertTrue(error.getDetails().contains("год должен быть между 1888 и " + maxYear));
    }

    @Test
    void methodNotAllowed_forMovieById_returns405() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(BASE + "/movies/1"))
                .build();

        HttpResponse<String> resp = send(req);

        assertEquals(405, resp.statusCode());
        assertContentType(resp);
        assertErrorMessage(resp, "Метод не поддерживается");
    }

    private HttpRequest postMovies(String json) {
        return HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .build();
    }

    private Movie createMovie(String title, int year) throws Exception {
        String json = String.format("{\"title\":\"%s\",\"year\":%d}", title, year);
        HttpResponse<String> resp = send(postMovies(json));
        return GSON.fromJson(resp.body(), Movie.class);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void assertContentType(HttpResponse<?> resp) {
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType);
    }

    private void assertErrorMessage(HttpResponse<String> resp, String message) {
        ErrorResponse error = parseError(resp);
        assertEquals(message, error.getError());
    }

    private ErrorResponse parseError(HttpResponse<String> resp) {
        ErrorResponse error = GSON.fromJson(resp.body(), ErrorResponse.class);
        assertNotNull(error);
        return error;
    }
}
