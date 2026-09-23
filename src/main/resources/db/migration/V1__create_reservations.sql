create table reservations (
    id                 varchar(8)     not null primary key,
    customer_name      varchar(200)   not null,
    room_number        varchar(10)    not null,
    start_date         date           not null,
    end_date           date           not null,
    room_segment       varchar(20)    not null,
    payment_mode       varchar(20)    not null,
    payment_reference  varchar(100),
    status             varchar(20)    not null,
    total_amount       numeric(12, 2) not null,
    amount_received    numeric(12, 2) not null default 0,
    created_at         timestamptz    not null,
    version            bigint         not null default 0
);

-- Backs the room-overlap check run on every reservation attempt.
create index idx_reservations_room_number on reservations (room_number);

-- Backs the auto-cancellation scheduler's query for pending bank transfers past their lead time.
create index idx_reservations_cancellation_candidates on reservations (status, payment_mode, start_date);

create table processed_payments (
    payment_id      varchar(100) not null primary key,
    reservation_id  varchar(8)   not null,
    processed_at    timestamptz  not null
);
