package co.tkm.ticketmanagementsystem.controller.admin;

import co.tkm.ticketmanagementsystem.model.SystemSetting;
import co.tkm.ticketmanagementsystem.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
@Tag(name = "Admin: Settings", description = "Back-office system settings")
public class AdminSettingController {

    private final SystemSettingService systemSettingService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List all system settings")
    public ResponseEntity<List<SystemSetting>> settings() {
        return ResponseEntity.ok(systemSettingService.findAll());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Save one or more system settings (key -> value)")
    public ResponseEntity<List<SystemSetting>> saveSettings(@RequestBody Map<String, String> params) {
        params.forEach(systemSettingService::set);
        return ResponseEntity.ok(systemSettingService.findAll());
    }
}
