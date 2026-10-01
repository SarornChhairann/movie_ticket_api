package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.service.MovieReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class MovieReminderApiController {

    private final MovieReminderService movieReminderService;

    /** 200 {@code {ok:true}}; 400 invalid email, unknown or not coming-soon movie; 409 {@code REMINDER_EXISTS}. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateReminderRequest req) {
        movieReminderService.createReminder(req.movieId(), req.email());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    public record CreateReminderRequest(Long movieId, String email) {
    }
}

