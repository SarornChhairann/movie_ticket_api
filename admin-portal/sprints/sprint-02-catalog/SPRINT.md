# Sprint 2 — Catalog

## Goal

An admin can list, create, edit, and delete movies and cinemas.

Depends on: sprint 1. Analytics movies and cinemas rows.

## Acceptance

- [ ] Movie list shows title, genre, status, and duration from `GET /admin/movies`.
- [ ] Save sends `multipart/form-data`. Update includes `id`. Poster is the part `imageFile`. Banner is the part `bannerFile`. Both parts are optional.
- [ ] Status choices are only `NOW_SHOWING`, `COMING_SOON`, and `ARCHIVED`.
- [ ] Cinema form edits name, address, opening hours, and coordinates. Cinema `id` is a UUID string.
- [ ] Delete asks for confirmation, then calls `DELETE` and expects 204.
- [ ] Poster and banner URLs prefix the API origin when the path starts with `/uploads/`.

## Steps

1. Routes: `/movies`, `/movies/new`, `/movies/:id`, `/cinemas`, `/cinemas/:id`.
2. Do not send the movie as JSON. The controller binds `@ModelAttribute Movie`.
3. Leave hall layout to sprint 3.

## Testing

Create a movie with a poster, reload, and see the image. Edit the title without a new file and confirm the old image remains.

## Out of scope

Showtimes, seat maps.
