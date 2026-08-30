package co.tkm.ticketmanagementsystem.controller.admin;

import co.tkm.ticketmanagementsystem.model.Cinema;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.service.HallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/halls")
@RequiredArgsConstructor
@Tag(name = "Admin: Halls", description = "Back-office hall management")
public class AdminHallController {

    private final HallService hallService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List all halls")
    @Transactional(readOnly = true)
    public ResponseEntity<List<HallResponse>> halls() {
        return ResponseEntity.ok(hallService.findAll().stream().map(this::toHallResponse).toList());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a hall by id")
    @Transactional(readOnly = true)
    public ResponseEntity<HallResponse> hall(@PathVariable Long id) {
        return ResponseEntity.ok(toHallResponse(hallService.findById(id)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create or update a hall (set id to update)")
    public ResponseEntity<HallResponse> saveHall(@RequestBody HallRequest req) {
        Hall hall = new Hall();
        Cinema cinema = new Cinema();
        cinema.setId(req.cinemaId());
        hall.setCinema(cinema);
        hall.setName(req.name());
        hall.setCity(req.city());
        hall.setAddress(req.address());
        hall.setType(req.type());
        hall.setTotalSeats(req.totalSeats());
        hall.setCapacity(req.capacity());
        hall.setHasDolbyAtmos(req.hasDolbyAtmos());
        hall.setHasScreenX(req.hasScreenX());
        hall.setAmenities(req.amenities());

        Hall saved = (req.id() == null) ? hallService.save(hall) : hallService.update(req.id(), hall);
        return ResponseEntity.ok(toHallResponse(saved));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a hall")
    public ResponseEntity<Void> deleteHall(@PathVariable Long id) {
        hallService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private HallResponse toHallResponse(Hall h) {
        Cinema cinema = h.getCinema();
        return new HallResponse(h.getId(), h.getName(), h.getTotalSeats(), h.getCity(), h.getAddress(), h.getType(),
                h.getCapacity(), h.isHasDolbyAtmos(), h.isHasScreenX(), h.getAmenities(),
                cinema != null ? cinema.getId() : null, cinema != null ? cinema.getName() : null);
    }

    public record HallResponse(Long id, String name, Integer totalSeats, String city, String address, String type,
                                Integer capacity, boolean hasDolbyAtmos, boolean hasScreenX, String amenities,
                                UUID cinemaId, String cinemaName) {
    }

    public record HallRequest(Long id, String name, UUID cinemaId, String city, String address,
                               String type, Integer totalSeats, Integer capacity, boolean hasDolbyAtmos,
                               boolean hasScreenX, String amenities) {
    }
}
