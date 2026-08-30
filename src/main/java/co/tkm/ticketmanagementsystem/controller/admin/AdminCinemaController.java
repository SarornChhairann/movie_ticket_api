package co.tkm.ticketmanagementsystem.controller.admin;

import co.tkm.ticketmanagementsystem.model.Cinema;
import co.tkm.ticketmanagementsystem.service.CinemaService;
import co.tkm.ticketmanagementsystem.service.HallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/cinemas")
@RequiredArgsConstructor
@Tag(name = "Admin: Cinemas", description = "Back-office cinema management")
public class AdminCinemaController {

    private final CinemaService cinemaService;
    private final HallService hallService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List all cinemas")
    public ResponseEntity<List<Cinema>> cinemas() {
        return ResponseEntity.ok(cinemaService.findAll());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a cinema by id")
    public ResponseEntity<Cinema> cinema(@PathVariable UUID id) {
        return ResponseEntity.ok(cinemaService.findById(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create or update a cinema (set id to update)")
    public ResponseEntity<Cinema> saveCinema(@RequestBody Cinema cinema) {
        return ResponseEntity.ok(cinemaService.save(cinema));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a cinema (fails if it still has halls)")
    public ResponseEntity<Map<String, Object>> deleteCinema(@PathVariable UUID id) {
        long halls = hallService.countByCinemaId(id);
        if (halls > 0) {
            throw new IllegalStateException("Cannot delete cinema: it has " + halls + " hall(s). Delete or move halls first.");
        }
        cinemaService.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Cinema deleted."));
    }
}
