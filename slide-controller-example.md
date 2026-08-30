# 🎬 Controller Example — `AdminMovieController`

A **`@RestController`** that exposes movie management as a JSON REST API.
Each method = one endpoint. The controller stays thin and delegates to `MovieService`.

**Base path:** `/admin/movies`  ·  **Access:** `ROLE_ADMIN` only

---

### Endpoints

| Method | Endpoint | What it does |
|:------:|:--|:--|
| `GET`    | `/admin/movies`      | List all movies |
| `GET`    | `/admin/movies/{id}` | Get one movie by id |
| `POST`   | `/admin/movies`      | Create or update a movie (+ optional poster/banner upload) |
| `DELETE` | `/admin/movies/{id}` | Delete a movie |

---

### The code

```java
@RestController
@RequestMapping("/admin/movies")     // base path for every endpoint
@RequiredArgsConstructor
public class AdminMovieController {

    private final MovieService movieService;   // business logic lives here

    @GetMapping                               // GET /admin/movies
    public ResponseEntity<List<Movie>> movies() {
        return ResponseEntity.ok(movieService.findAll());
    }

    @GetMapping("/{id}")                      // GET /admin/movies/{id}
    public ResponseEntity<Movie> movie(@PathVariable Long id) {
        return ResponseEntity.ok(movieService.findById(id));
    }

    @PostMapping                              // POST /admin/movies
    public ResponseEntity<Movie> saveMovie(@ModelAttribute Movie movie,
                                           @RequestPart(required = false) MultipartFile imageFile) {
        return ResponseEntity.ok(movieService.saveWithImages(movie, imageFile, null));
    }

    @DeleteMapping("/{id}")                    // DELETE /admin/movies/{id}
    public ResponseEntity<Void> deleteMovie(@PathVariable Long id) {
        movieService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

> **Takeaway:** one controller → one resource. `@GetMapping / @PostMapping / @DeleteMapping`
> map HTTP verbs to methods, and all endpoints are auto-documented in **Swagger UI**.
