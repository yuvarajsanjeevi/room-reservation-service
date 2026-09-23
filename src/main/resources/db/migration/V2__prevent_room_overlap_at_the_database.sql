-- The application checks for an overlapping reservation before writing (see
-- ReservationRepository.existsOverlapping), but that check and the write are two separate
-- statements: two requests for the same room at the same instant could both pass the check before
-- either commits. This constraint is the backstop that makes double-booking impossible regardless of
-- timing, not just unlikely.
create extension if not exists btree_gist;

alter table reservations
    add constraint reservations_no_room_overlap
    exclude using gist (
        room_number with =,
        daterange(start_date, end_date, '[)') with &&
    )
    where (status <> 'CANCELLED');
