package co.tkm.ticketmanagementsystem.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlashSystemTemplateTest {

    @Test
    void baseLayout_includesFlashRootConfirmModalAndScript() throws IOException {
        String html = read("src/main/resources/templates/layout/base.html");
        assertTrue(html.contains("id=\"mtms-flash-root\""));
        assertTrue(html.contains("id=\"mtmsConfirmModal\""));
        assertTrue(html.contains("/mtms/flash.css"));
        assertTrue(html.contains("/mtms/flash.js"));
    }

    @Test
    void adminLayout_includesFlashRootConfirmModalAndScript() throws IOException {
        String html = read("src/main/resources/templates/layout/admin-base.html");
        assertTrue(html.contains("id=\"mtms-flash-root\""));
        assertTrue(html.contains("id=\"mtmsConfirmModal\""));
        assertTrue(html.contains("/mtms/flash.css"));
        assertTrue(html.contains("/mtms/flash.js"));
    }

    @Test
    void templates_doNotUseNativeConfirm() throws IOException {
        String templates = readAllTemplates();
        assertFalse(templates.contains("confirm("));
    }

    @Test
    void templates_useDataConfirmAttributes() throws IOException {
        String templates = readAllTemplates();
        assertTrue(templates.contains("data-confirm="));
    }

    @Test
    void adminShowtimeTemplates_includeCsrfInputs() throws IOException {
        String list = read("src/main/resources/templates/admin/showtime-list.html");
        String form = read("src/main/resources/templates/admin/showtime-form.html");
        assertTrue(list.contains("_csrf.parameterName"));
        assertTrue(list.contains("_csrf.token"));
        assertTrue(form.contains("_csrf.parameterName"));
        assertTrue(form.contains("_csrf.token"));
    }

    @Test
    void adminHallAndCinemaTemplates_includeCsrfInputs() throws IOException {
        String hallForm = read("src/main/resources/templates/admin/hall-form.html");
        String cinemaForm = read("src/main/resources/templates/admin/cinema-form.html");
        assertTrue(hallForm.contains("_csrf.parameterName"));
        assertTrue(hallForm.contains("_csrf.token"));
        assertTrue(cinemaForm.contains("_csrf.parameterName"));
        assertTrue(cinemaForm.contains("_csrf.token"));
    }

    @Test
    void adminLayout_hasCinemasNavLink() throws IOException {
        String html = read("src/main/resources/templates/layout/admin-base.html");
        assertTrue(html.contains("@{/admin/cinemas}") || html.contains("/admin/cinemas"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }

    private static String readAllTemplates() throws IOException {
        StringBuilder out = new StringBuilder();
        Files.walk(Path.of("src/main/resources/templates"))
                .filter(p -> p.toString().endsWith(".html"))
                .sorted()
                .forEach(p -> {
                    try {
                        out.append(Files.readString(p, StandardCharsets.UTF_8)).append('\n');
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        return out.toString();
    }
}
