-- Reference data required in every environment (roles, permissions, seat types, genres, cities)
INSERT INTO roles (name, description) VALUES
 ('USER', 'Customer'),
 ('ADMIN', 'Platform administrator'),
 ('PARTNER', 'Cinema / event partner'),
 ('VENUE_STAFF', 'Box office and gate staff');

INSERT INTO permissions (code, description) VALUES
 ('BOOKING_CREATE', 'Create bookings'),
 ('CATALOG_MANAGE', 'Manage movies, events and cinemas'),
 ('SHOW_MANAGE', 'Manage shows and pricing'),
 ('TICKET_VALIDATE', 'Validate tickets at entry'),
 ('COUNTER_PAYMENT', 'Confirm pay-at-venue bookings'),
 ('REFUND_MANAGE', 'View and retry refunds'),
 ('USER_MANAGE', 'Manage users and roles'),
 ('ANALYTICS_VIEW', 'View reports and analytics');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN'
   OR (r.name = 'USER' AND p.code = 'BOOKING_CREATE')
   OR (r.name = 'PARTNER' AND p.code IN ('SHOW_MANAGE', 'ANALYTICS_VIEW', 'BOOKING_CREATE'))
   OR (r.name = 'VENUE_STAFF' AND p.code IN ('TICKET_VALIDATE', 'COUNTER_PAYMENT'));

INSERT INTO seat_types (code, name, price_delta, sort_order) VALUES
 ('VIP',     'VIP Recliner', 320, 1),
 ('PREMIUM', 'Premium',      170, 2),
 ('GOLD',    'Gold',          70, 3),
 ('SILVER',  'Silver',         0, 4);

INSERT INTO genres (name, slug) VALUES
 ('Action','action'), ('Drama','drama'), ('Thriller','thriller'), ('Sci-Fi','sci-fi'),
 ('Comedy','comedy'), ('Romance','romance'), ('Family','family'), ('Animation','animation'),
 ('Adventure','adventure'), ('Mystery','mystery'), ('Horror','horror'), ('Historical','historical');

INSERT INTO cities (name, state, slug, latitude, longitude, is_popular) VALUES
 ('Hyderabad',     'Telangana',      'hyderabad',     17.385044, 78.486671, TRUE),
 ('Bengaluru',     'Karnataka',      'bengaluru',     12.971599, 77.594566, TRUE),
 ('Chennai',       'Tamil Nadu',     'chennai',       13.082680, 80.270721, TRUE),
 ('Mumbai',        'Maharashtra',    'mumbai',        19.075984, 72.877656, TRUE),
 ('Delhi',         'Delhi',          'delhi',         28.704060, 77.102493, TRUE),
 ('Pune',          'Maharashtra',    'pune',          18.520430, 73.856743, TRUE),
 ('Kolkata',       'West Bengal',    'kolkata',       22.572646, 88.363895, TRUE),
 ('Vijayawada',    'Andhra Pradesh', 'vijayawada',    16.506174, 80.648015, FALSE),
 ('Visakhapatnam', 'Andhra Pradesh', 'visakhapatnam', 17.686816, 83.218482, FALSE);

INSERT INTO event_categories (code, name, vertical, icon) VALUES
 ('CONCERT',  'Concerts',        'EVENT',    'music'),
 ('COMEDY',   'Stand-up Comedy', 'EVENT',    'laugh'),
 ('FESTIVAL', 'Festivals',       'EVENT',    'party-popper'),
 ('WORKSHOP', 'Workshops',       'EVENT',    'palette'),
 ('THEATRE',  'Theatre',         'PLAY',     'drama'),
 ('CRICKET',  'Cricket',         'SPORTS',   'trophy'),
 ('FOOTBALL', 'Football',        'SPORTS',   'goal'),
 ('KABADDI',  'Kabaddi',         'SPORTS',   'swords'),
 ('BOWLING',  'Bowling',         'ACTIVITY', 'circle-dot'),
 ('THEME_PARK','Theme Parks',    'ACTIVITY', 'ferris-wheel'),
 ('ESCAPE_ROOM','Escape Rooms',  'ACTIVITY', 'key-round'),
 ('ADVENTURE','Adventure',       'ACTIVITY', 'mountain');
