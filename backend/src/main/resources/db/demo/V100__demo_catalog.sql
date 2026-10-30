-- Demo catalog (dev/demo profiles only). All titles, people, cinemas and events are fictional.

INSERT INTO locations (city_id, name, pincode)
SELECT c.id, l.name, l.pin FROM (VALUES
 ('hyderabad','Gachibowli','500032'), ('hyderabad','Banjara Hills','500034'), ('hyderabad','Kukatpally','500072'), ('hyderabad','Madhapur','500081'),
 ('bengaluru','Koramangala','560034'), ('bengaluru','Whitefield','560066'),
 ('chennai','T. Nagar','600017'), ('mumbai','Andheri West','400053'), ('delhi','Saket','110017'),
 ('pune','Hinjewadi','411057'), ('kolkata','Salt Lake','700091'), ('vijayawada','Benz Circle','520010'),
 ('visakhapatnam','Beach Road','530017')
) AS l(city, name, pin) JOIN cities c ON c.slug = l.city;

-- ---------------------------------------------------------------- movies
INSERT INTO movies (title, slug, tagline, synopsis, duration_min, certificate, release_date, poster_url, backdrop_url, trailer_url, formats, status, featured) VALUES
('Galactic Dawn', 'galactic-dawn', 'The last sunrise belongs to the brave.',
 'When a dying star threatens the colony ship Aruna, a disgraced navigator and a rookie engineer must steer thirty thousand sleepers through an uncharted nebula before the dawn that ends everything.',
 148, 'UA', CURRENT_DATE - 12, '/media/posters/galactic-dawn.svg', '/media/backdrops/galactic-dawn.svg', 'https://www.youtube.com/embed/aqz-KE-bpKQ', '2D,3D,IMAX,DOLBY_ATMOS', 'NOW_SHOWING', TRUE),
('Midnight Protocol', 'midnight-protocol', 'Trust no signal after 12.',
 'A cyber-security analyst in Mumbai discovers a dormant program that wakes every midnight and rewrites a city''s traffic grid. She has six nights to find its author before it rewrites her life.',
 136, 'UA13+', CURRENT_DATE - 5, '/media/posters/midnight-protocol.svg', '/media/backdrops/midnight-protocol.svg', 'https://www.youtube.com/embed/aqz-KE-bpKQ', '2D,DOLBY_ATMOS', 'NOW_SHOWING', TRUE),
('Veera', 'veera', 'One village. One promise. No retreat.',
 'A soft-spoken schoolteacher returns to her coastal village to find it swallowed by a sand-mining cartel. What begins as a petition becomes a rebellion that the whole Godavari delta will remember.',
 162, 'UA', CURRENT_DATE - 19, '/media/posters/veera.svg', '/media/backdrops/veera.svg', 'https://www.youtube.com/embed/aqz-KE-bpKQ', '2D,IMAX,4DX', 'NOW_SHOWING', TRUE),
('The Last Signal', 'the-last-signal', 'Someone is still transmitting.',
 'Snowed in at a remote Himalayan radio observatory, five researchers receive a message that seems to come from their own future — and it names which one of them will not survive the week.',
 124, 'A', CURRENT_DATE - 8, '/media/posters/the-last-signal.svg', '/media/backdrops/the-last-signal.svg', 'https://www.youtube.com/embed/aqz-KE-bpKQ', '2D,DOLBY_ATMOS', 'NOW_SHOWING', FALSE),
('Neon City', 'neon-city', 'The city never sleeps. Neither does she.',
 'In a rain-soaked Chennai of 2049, a courier who delivers memories is framed for a heist she cannot remember. Racing through neon markets and flooded metro tunnels, she must steal her past back.',
 141, 'UA13+', CURRENT_DATE - 2, '/media/posters/neon-city.svg', '/media/backdrops/neon-city.svg', 'https://www.youtube.com/embed/aqz-KE-bpKQ', '2D,3D,IMAX,4DX', 'NOW_SHOWING', TRUE),
('Monsoon Letters', 'monsoon-letters', 'Some letters take a lifetime to arrive.',
 'A retired postman in Kochi finds a bundle of undelivered letters from 1987 and sets out, one monsoon, to deliver every single one — reuniting strangers and confronting the one addressed to himself.',
 129, 'U', CURRENT_DATE - 26, '/media/posters/monsoon-letters.svg', '/media/backdrops/monsoon-letters.svg', 'https://www.youtube.com/embed/aqz-KE-bpKQ', '2D', 'NOW_SHOWING', FALSE),
('Kaveri Express', 'kaveri-express', 'Next stop: absolute chaos.',
 'Three strangers, one wedding sherwani and a goat named Rocket share a sleeper coupe from Mysuru to Chennai. Nobody is who they claim to be, and the ticket collector has questions.',
 132, 'U', CURRENT_DATE - 15, '/media/posters/kaveri-express.svg', '/media/backdrops/kaveri-express.svg', 'https://www.youtube.com/embed/aqz-KE-bpKQ', '2D', 'NOW_SHOWING', FALSE),
('Iron Tide', 'iron-tide', 'The sea keeps its secrets. Until now.',
 'A naval diver uncovers a sunken ship off Visakhapatnam carrying a cargo that three nations would go to war over.',
 150, 'UA', CURRENT_DATE + 18, '/media/posters/iron-tide.svg', '/media/backdrops/iron-tide.svg', 'https://www.youtube.com/embed/aqz-KE-bpKQ', '2D,IMAX', 'COMING_SOON', FALSE),
('Whispers of Hampi', 'whispers-of-hampi', 'History remembers. So do the stones.',
 'An archaeology student and a sceptical tour guide decode carvings among the Hampi ruins that point to a lost royal library.',
 138, 'U', CURRENT_DATE + 30, '/media/posters/whispers-of-hampi.svg', '/media/backdrops/whispers-of-hampi.svg', 'https://www.youtube.com/embed/aqz-KE-bpKQ', '2D', 'COMING_SOON', FALSE),
('Orbit Kids', 'orbit-kids', 'Small crew. Giant leap.',
 'Four kids from a Pune housing society build a rocket out of scrap for the national science fair — and accidentally make contact with a very lonely satellite.',
 104, 'U', CURRENT_DATE + 45, '/media/posters/orbit-kids.svg', '/media/backdrops/orbit-kids.svg', 'https://www.youtube.com/embed/aqz-KE-bpKQ', '2D,3D', 'COMING_SOON', FALSE);

INSERT INTO movie_languages (movie_id, language)
SELECT m.id, l.lang FROM (VALUES
 ('galactic-dawn','English'), ('galactic-dawn','Hindi'), ('galactic-dawn','Telugu'), ('galactic-dawn','Tamil'),
 ('midnight-protocol','Hindi'), ('midnight-protocol','English'),
 ('veera','Telugu'), ('veera','Hindi'), ('veera','Tamil'), ('veera','Kannada'),
 ('the-last-signal','English'), ('the-last-signal','Hindi'),
 ('neon-city','Tamil'), ('neon-city','Telugu'), ('neon-city','Hindi'),
 ('monsoon-letters','Malayalam'), ('monsoon-letters','Tamil'),
 ('kaveri-express','Kannada'), ('kaveri-express','Tamil'),
 ('iron-tide','Telugu'), ('iron-tide','Hindi'),
 ('whispers-of-hampi','Kannada'), ('whispers-of-hampi','Hindi'),
 ('orbit-kids','English'), ('orbit-kids','Hindi')
) AS l(slug, lang) JOIN movies m ON m.slug = l.slug;

INSERT INTO movie_genres (movie_id, genre_id)
SELECT m.id, g.id FROM (VALUES
 ('galactic-dawn','sci-fi'), ('galactic-dawn','adventure'), ('galactic-dawn','action'),
 ('midnight-protocol','thriller'), ('midnight-protocol','mystery'),
 ('veera','action'), ('veera','drama'),
 ('the-last-signal','thriller'), ('the-last-signal','horror'), ('the-last-signal','sci-fi'),
 ('neon-city','sci-fi'), ('neon-city','action'),
 ('monsoon-letters','drama'), ('monsoon-letters','romance'),
 ('kaveri-express','comedy'), ('kaveri-express','family'),
 ('iron-tide','action'), ('iron-tide','adventure'),
 ('whispers-of-hampi','historical'), ('whispers-of-hampi','mystery'),
 ('orbit-kids','animation'), ('orbit-kids','family')
) AS x(slug, genre) JOIN movies m ON m.slug = x.slug JOIN genres g ON g.slug = x.genre;

INSERT INTO movie_images (movie_id, url, caption, sort_order)
SELECT m.id, '/media/stills/' || m.slug || '-' || n || '.svg', 'Still ' || n, n
FROM movies m CROSS JOIN generate_series(1, 3) n;

INSERT INTO directors (name) VALUES
 ('Anika Raghavan'), ('Dev Malhotra'), ('Suresh Kondapalli'), ('Meera Iyer'), ('Karthik Selvan'),
 ('Thomas Varghese'), ('Pooja Hegde Rao'), ('Rahul Bhonsle');

INSERT INTO movie_directors (movie_id, director_id)
SELECT m.id, d.id FROM (VALUES
 ('galactic-dawn','Anika Raghavan'), ('midnight-protocol','Dev Malhotra'), ('veera','Suresh Kondapalli'),
 ('the-last-signal','Meera Iyer'), ('neon-city','Karthik Selvan'), ('monsoon-letters','Thomas Varghese'),
 ('kaveri-express','Pooja Hegde Rao'), ('iron-tide','Suresh Kondapalli'), ('whispers-of-hampi','Pooja Hegde Rao'),
 ('orbit-kids','Rahul Bhonsle')
) AS x(slug, dname) JOIN movies m ON m.slug = x.slug JOIN directors d ON d.name = x.dname;

INSERT INTO actors (name, bio) VALUES
 ('Arjun Varma', 'Hyderabad-born lead known for intense action roles.'),
 ('Ishita Rao', 'Theatre-trained actor with a string of acclaimed dramas.'),
 ('Kabir Sethi', 'Versatile performer across Hindi and English cinema.'),
 ('Nila Sundaram', 'Chennai-based actor and trained Bharatanatyam dancer.'),
 ('Rohan D''Souza', 'Character actor and voice artist from Goa.'),
 ('Lakshmi Menon', 'Veteran of more than sixty Malayalam films.'),
 ('Vikram Gowda', 'Kannada comedy star turned producer.'),
 ('Tara Kapoor', 'Former astrophysics student turned screen actor.'),
 ('Sameer Qureshi', 'Known for villain roles with surprising warmth.'),
 ('Aditi Kulkarni', 'Pune-based actor and playwright.'),
 ('Joseph Mathew', 'Stage legend from Kochi.'),
 ('Priya Natarajan', 'Rising star of Tamil science-fiction cinema.');

INSERT INTO movie_cast (movie_id, actor_id, character_name, crew_role, billing_order)
SELECT m.id, a.id, x.ch, x.crew, x.ord FROM (VALUES
 ('galactic-dawn','Tara Kapoor','Navigator Ira',NULL,1), ('galactic-dawn','Kabir Sethi','Engineer Neel',NULL,2), ('galactic-dawn','Sameer Qureshi','Commander Vashisht',NULL,3), ('galactic-dawn','Rohan D''Souza',NULL,'Music',9),
 ('midnight-protocol','Ishita Rao','Aanya Mehra',NULL,1), ('midnight-protocol','Kabir Sethi','Inspector Rawat',NULL,2), ('midnight-protocol','Aditi Kulkarni','Zoya',NULL,3),
 ('veera','Ishita Rao','Veera',NULL,1), ('veera','Arjun Varma','Surya',NULL,2), ('veera','Sameer Qureshi','Bhairava',NULL,3),
 ('the-last-signal','Tara Kapoor','Dr. Kavya Sen',NULL,1), ('the-last-signal','Rohan D''Souza','Leo',NULL,2), ('the-last-signal','Aditi Kulkarni','Mira',NULL,3),
 ('neon-city','Priya Natarajan','Kaya',NULL,1), ('neon-city','Arjun Varma','Ravi',NULL,2), ('neon-city','Nila Sundaram','The Broker',NULL,3),
 ('monsoon-letters','Joseph Mathew','Kunjappan',NULL,1), ('monsoon-letters','Lakshmi Menon','Annamma',NULL,2), ('monsoon-letters','Nila Sundaram','Anjali',NULL,3),
 ('kaveri-express','Vikram Gowda','Manja',NULL,1), ('kaveri-express','Nila Sundaram','Revathi',NULL,2), ('kaveri-express','Rohan D''Souza','TC Pinto',NULL,3),
 ('iron-tide','Arjun Varma','Lt. Aditya',NULL,1), ('iron-tide','Tara Kapoor','Dr. Rhea',NULL,2),
 ('whispers-of-hampi','Aditi Kulkarni','Sneha',NULL,1), ('whispers-of-hampi','Vikram Gowda','Basavaraj',NULL,2),
 ('orbit-kids','Priya Natarajan','Voice of SAT-7',NULL,1), ('orbit-kids','Kabir Sethi','Mr. Joshi',NULL,2)
) AS x(slug, aname, ch, crew, ord) JOIN movies m ON m.slug = x.slug JOIN actors a ON a.name = x.aname;

INSERT INTO ratings (movie_id) SELECT id FROM movies;

-- ---------------------------------------------------------------- cinemas
INSERT INTO cinemas (name, slug, city_id, location_id, address, latitude, longitude, facilities, allow_pay_at_venue)
SELECT x.name, x.slug, c.id, l.id, x.addr, x.lat, x.lng, x.fac, x.pav FROM (VALUES
 ('CineVerse Galleria','cineverse-galleria-hyd','hyderabad','Gachibowli','Level 4, Galleria Mall, Gachibowli, Hyderabad',17.440100,78.348900,'PARKING,FOOD_COURT,WHEELCHAIR,M_TICKET,RECLINER',TRUE),
 ('Purple Square Cinemas','purple-square-hyd','hyderabad','Banjara Hills','Road No. 1, Banjara Hills, Hyderabad',17.413000,78.439300,'PARKING,FOOD_COURT,M_TICKET',FALSE),
 ('Galaxy Mall Cinema','galaxy-mall-hyd','hyderabad','Kukatpally','Galaxy Mall, KPHB, Kukatpally, Hyderabad',17.494000,78.399800,'PARKING,WHEELCHAIR,M_TICKET',TRUE),
 ('MetroPlex','metroplex-hyd','hyderabad','Madhapur','Hitech City Main Rd, Madhapur, Hyderabad',17.448300,78.391500,'PARKING,FOOD_COURT,WHEELCHAIR,M_TICKET,RECLINER',FALSE),
 ('CineVerse Orion','cineverse-orion-blr','bengaluru','Koramangala','80 Feet Rd, Koramangala, Bengaluru',12.935200,77.624500,'PARKING,FOOD_COURT,M_TICKET,RECLINER',TRUE),
 ('MetroPlex Whitefield','metroplex-whitefield-blr','bengaluru','Whitefield','ITPL Main Rd, Whitefield, Bengaluru',12.969800,77.749900,'PARKING,WHEELCHAIR,M_TICKET',FALSE),
 ('Purple Square Marina','purple-square-chn','chennai','T. Nagar','Usman Rd, T. Nagar, Chennai',13.041800,80.233900,'FOOD_COURT,M_TICKET',TRUE),
 ('Galaxy Screens Andheri','galaxy-andheri-mum','mumbai','Andheri West','Link Rd, Andheri West, Mumbai',19.136300,72.829600,'PARKING,FOOD_COURT,WHEELCHAIR,M_TICKET',FALSE),
 ('CineVerse Select Saket','cineverse-saket-del','delhi','Saket','Press Enclave Marg, Saket, New Delhi',28.528600,77.219000,'PARKING,FOOD_COURT,WHEELCHAIR,M_TICKET,RECLINER',FALSE),
 ('MetroPlex Hinjewadi','metroplex-hinjewadi-pun','pune','Hinjewadi','Phase 1, Hinjewadi, Pune',18.591300,73.738900,'PARKING,M_TICKET',TRUE),
 ('Purple Square Salt Lake','purple-square-kol','kolkata','Salt Lake','Sector V, Salt Lake, Kolkata',22.576800,88.431800,'PARKING,FOOD_COURT,M_TICKET',FALSE),
 ('Galaxy Talkies','galaxy-talkies-vja','vijayawada','Benz Circle','MG Road, Benz Circle, Vijayawada',16.499100,80.656800,'PARKING,M_TICKET',TRUE),
 ('CineVerse Seaside','cineverse-seaside-vzg','visakhapatnam','Beach Road','Beach Road, Visakhapatnam',17.714400,83.323600,'PARKING,FOOD_COURT,WHEELCHAIR,M_TICKET',TRUE)
) AS x(name, slug, city, loc, addr, lat, lng, fac, pav)
JOIN cities c ON c.slug = x.city LEFT JOIN locations l ON l.city_id = c.id AND l.name = x.loc;

-- Screens: every cinema gets Screen 1 (standard) and Screen 2 (premium large format); flagships get Screen 3.
INSERT INTO cinema_screens (cinema_id, name, screen_type, formats)
SELECT id, 'Screen 1', 'STANDARD', '2D,3D' FROM cinemas;
INSERT INTO cinema_screens (cinema_id, name, screen_type, formats)
SELECT id, 'Screen 2', CASE WHEN slug LIKE 'cineverse-%' OR slug LIKE 'metroplex-hyd' THEN 'IMAX' ELSE 'DOLBY' END,
       CASE WHEN slug LIKE 'cineverse-%' OR slug LIKE 'metroplex-hyd' THEN '2D,3D,IMAX,DOLBY_ATMOS' ELSE '2D,DOLBY_ATMOS' END
FROM cinemas;
INSERT INTO cinema_screens (cinema_id, name, screen_type, formats)
SELECT id, 'Screen 3', '4DX', '2D,3D,4DX' FROM cinemas WHERE slug IN ('cineverse-galleria-hyd','metroplex-hyd','cineverse-orion-blr');

-- Seat layout: 10 rows (A-J) x 14 seats, aisles after seats 4 and 10.
-- A: VIP (large-format screens) or PREMIUM, B-C: PREMIUM, D-G: GOLD, H-J: SILVER. Screen is in front of row J.
DO $$
DECLARE
  scr RECORD;
  r INT; s INT; col INT; seat_type BIGINT; rl TEXT;
BEGIN
  FOR scr IN SELECT id, screen_type FROM cinema_screens LOOP
    FOR r IN 1..10 LOOP
      rl := chr(64 + r);
      IF r = 1 AND scr.screen_type IN ('IMAX', '4DX') THEN
        SELECT id INTO seat_type FROM seat_types WHERE code = 'VIP';
      ELSIF r <= 3 THEN
        SELECT id INTO seat_type FROM seat_types WHERE code = 'PREMIUM';
      ELSIF r <= 7 THEN
        SELECT id INTO seat_type FROM seat_types WHERE code = 'GOLD';
      ELSE
        SELECT id INTO seat_type FROM seat_types WHERE code = 'SILVER';
      END IF;
      FOR s IN 1..14 LOOP
        col := s + CASE WHEN s > 10 THEN 2 WHEN s > 4 THEN 1 ELSE 0 END;
        INSERT INTO screen_seats (screen_id, row_label, seat_number, label, row_index, col_index, seat_type_id, disabled)
        VALUES (scr.id, rl, s, rl || s, r, col, seat_type, (r = 10 AND s IN (1, 14)));
      END LOOP;
    END LOOP;
    UPDATE cinema_screens SET capacity = (SELECT count(*) FROM screen_seats WHERE screen_id = scr.id AND NOT disabled) WHERE id = scr.id;
  END LOOP;
END $$;

-- ---------------------------------------------------------------- food
INSERT INTO food_items (name, description, category, price, image_url, veg, sort_order) VALUES
 ('Salted Popcorn',        'Classic butter-salted, regular tub',      'POPCORN',  250, '/media/food/popcorn.svg', TRUE, 1),
 ('Large Popcorn',         'Family tub, salted',                     'POPCORN',  340, '/media/food/popcorn-large.svg', TRUE, 2),
 ('Cheese Popcorn',        'Cheddar-dusted, regular tub',             'POPCORN',  290, '/media/food/popcorn-cheese.svg', TRUE, 3),
 ('Loaded Nachos',         'Salsa, jalapeños and cheese sauce',       'SNACK',    220, '/media/food/nachos.svg', TRUE, 4),
 ('Chicken Hot Dog',       'Grilled sausage, mustard mayo',           'SNACK',    240, '/media/food/hotdog.svg', FALSE, 5),
 ('Coke',                  '500 ml, chilled',                         'BEVERAGE', 150, '/media/food/cola.svg', TRUE, 6),
 ('Pepsi',                 '500 ml, chilled',                         'BEVERAGE', 150, '/media/food/cola-blue.svg', TRUE, 7),
 ('Mineral Water',         '1 litre',                                 'BEVERAGE',  60, '/media/food/water.svg', TRUE, 8),
 ('Cold Coffee',           'Blended with vanilla ice cream',          'BEVERAGE', 180, '/media/food/coffee.svg', TRUE, 9),
 ('Couple Combo',          '1 large popcorn + 2 drinks',              'COMBO',    520, '/media/food/combo.svg', TRUE, 10),
 ('Solo Combo',            '1 regular popcorn + 1 drink',             'COMBO',    360, '/media/food/combo-solo.svg', TRUE, 11);

-- ---------------------------------------------------------------- coupons & offers
INSERT INTO coupons (code, description, discount_type, discount_value, max_discount, min_order_amount, valid_from, valid_to, first_booking_only, usage_limit, per_user_limit) VALUES
 ('WELCOME100', '10% off your first CineVerse booking, up to ₹100', 'PERCENT', 10, 100, 200, now() - interval '1 day', now() + interval '365 days', TRUE, NULL, 1),
 ('WEEKEND50',  'Flat ₹50 off on orders above ₹500',                  'FLAT', 50, NULL, 500, now() - interval '1 day', now() + interval '180 days', FALSE, 10000, 5),
 ('POPCORN20',  '20% off, up to ₹150, on orders above ₹800',          'PERCENT', 20, 150, 800, now() - interval '1 day', now() + interval '180 days', FALSE, NULL, 3),
 ('EXPIRED10',  'An expired coupon kept for testing',                  'PERCENT', 10, 50, 0, now() - interval '30 days', now() - interval '1 day', FALSE, NULL, 1);
INSERT INTO coupons (code, description, discount_type, discount_value, max_discount, min_order_amount, valid_from, valid_to, payment_method, per_user_limit) VALUES
 ('UPI75', 'Flat ₹75 off when you pay with UPI', 'FLAT', 75, NULL, 300, now() - interval '1 day', now() + interval '180 days', 'UPI', 3);
INSERT INTO coupons (code, description, discount_type, discount_value, max_discount, min_order_amount, valid_from, valid_to, movie_id, per_user_limit)
SELECT 'DAWN15', '15% off Galactic Dawn, up to ₹120', 'PERCENT', 15, 120, 0, now() - interval '1 day', now() + interval '90 days', id, 2 FROM movies WHERE slug = 'galactic-dawn';
INSERT INTO coupons (code, description, discount_type, discount_value, max_discount, min_order_amount, valid_from, valid_to, city_id, per_user_limit)
SELECT 'HYDFLAT60', 'Flat ₹60 off in Hyderabad', 'FLAT', 60, NULL, 400, now() - interval '1 day', now() + interval '90 days', id, 2 FROM cities WHERE slug = 'hyderabad';

INSERT INTO offers (title, description, offer_type, coupon_code, image_url, terms, valid_from, valid_to, sort_order) VALUES
 ('First booking? ₹100 off', 'New to CineVerse? Get 10% off (up to ₹100) on your first booking.', 'FIRST_BOOKING', 'WELCOME100', '/media/offers/welcome.svg', 'Valid once per account. Minimum order ₹200.', now() - interval '1 day', now() + interval '365 days', 1),
 ('Weekend Binge', 'Flat ₹50 off on orders above ₹500 every weekend.', 'WEEKEND', 'WEEKEND50', '/media/offers/weekend.svg', 'Up to 5 uses per user.', now() - interval '1 day', now() + interval '180 days', 2),
 ('Pay with UPI, save ₹75', 'Use any UPI app including Google Pay and PhonePe.', 'UPI', 'UPI75', '/media/offers/upi.svg', 'Minimum order ₹300. UPI payments only.', now() - interval '1 day', now() + interval '180 days', 3),
 ('Student Tuesdays', 'Show a valid student ID at the counter for a free regular popcorn upgrade.', 'STUDENT', NULL, '/media/offers/student.svg', 'Valid on Tuesdays at participating cinemas.', now() - interval '1 day', now() + interval '180 days', 4),
 ('Galaxy Bank Card Offer', '20% off up to ₹150 on orders above ₹800 with any credit card.', 'BANK', 'POPCORN20', '/media/offers/bank.svg', 'Illustrative demo offer.', now() - interval '1 day', now() + interval '180 days', 5);
INSERT INTO offers (title, description, offer_type, coupon_code, movie_id, image_url, terms, valid_from, valid_to, sort_order)
SELECT 'Galactic Dawn Launch', '15% off Galactic Dawn tickets, up to ₹120.', 'MOVIE', 'DAWN15', id, '/media/offers/movie.svg', 'Two uses per user.', now() - interval '1 day', now() + interval '90 days', 6 FROM movies WHERE slug = 'galactic-dawn';
INSERT INTO offers (title, description, offer_type, coupon_code, city_id, image_url, terms, valid_from, valid_to, sort_order)
SELECT 'Hyderabad Special', 'Flat ₹60 off on orders above ₹400 in Hyderabad.', 'WEEKEND', 'HYDFLAT60', id, '/media/offers/city.svg', 'Hyderabad cinemas only.', now() - interval '1 day', now() + interval '90 days', 7 FROM cities WHERE slug = 'hyderabad';

-- ---------------------------------------------------------------- venues & events
INSERT INTO venues (name, slug, city_id, address, latitude, longitude, capacity, facilities, allow_pay_at_venue)
SELECT x.name, x.slug, c.id, x.addr, x.lat, x.lng, x.cap, x.fac, x.pav FROM (VALUES
 ('Lakeside Amphitheatre','lakeside-amphitheatre-hyd','hyderabad','Necklace Road, Hyderabad',17.423300,78.473700,8000,'PARKING,FOOD_COURT,WHEELCHAIR',FALSE),
 ('The Laugh Loft','the-laugh-loft-hyd','hyderabad','Jubilee Hills Rd 36, Hyderabad',17.431500,78.407300,220,'BAR,M_TICKET',TRUE),
 ('Deccan Arena','deccan-arena-hyd','hyderabad','Uppal, Hyderabad',17.406500,78.550700,40000,'PARKING,FOOD_COURT,WHEELCHAIR',FALSE),
 ('Rangmanch Theatre','rangmanch-theatre-hyd','hyderabad','Ravindra Bharathi Complex, Hyderabad',17.399300,78.468800,600,'WHEELCHAIR',TRUE),
 ('StrikeZone Bowling','strikezone-hyd','hyderabad','Inorbit Mall, Madhapur, Hyderabad',17.434600,78.386900,120,'FOOD_COURT,PARKING',TRUE),
 ('Riddle Rooms','riddle-rooms-hyd','hyderabad','Jubilee Hills, Hyderabad',17.428900,78.411500,60,'M_TICKET',TRUE),
 ('Garden City Grounds','garden-city-grounds-blr','bengaluru','Palace Grounds, Bengaluru',13.004100,77.592400,15000,'PARKING,FOOD_COURT',FALSE),
 ('Harbour Stadium','harbour-stadium-mum','mumbai','Churchgate, Mumbai',18.938900,72.825800,33000,'PARKING,FOOD_COURT,WHEELCHAIR',FALSE),
 ('Canvas Studio','canvas-studio-blr','bengaluru','Indiranagar, Bengaluru',12.978400,77.640800,40,'M_TICKET',TRUE)
) AS x(name, slug, city, addr, lat, lng, cap, fac, pav) JOIN cities c ON c.slug = x.city;

INSERT INTO events (title, slug, description, category_id, vertical, venue_id, city_id, artist, team_home, team_away, languages, duration_min, age_restriction, poster_url, banner_url, featured)
SELECT x.title, x.slug, x.descr, ec.id, ec.vertical, v.id, v.city_id, x.artist, x.th, x.ta, x.langs, x.dur, x.age,
       '/media/events/' || x.slug || '.svg', '/media/events/' || x.slug || '-banner.svg', x.feat
FROM (VALUES
 ('Hyderabad Comedy Night','hyderabad-comedy-night','COMEDY','the-laugh-loft-hyd','An evening of fresh stand-up from five of the city''s sharpest new comics, hosted by the unflappable Ravi Chandra.','Ravi Chandra & Friends',NULL,NULL,'English, Hindi, Telugu',120,'18+',TRUE),
 ('Neon Music Festival','neon-music-festival','FESTIVAL','lakeside-amphitheatre-hyd','Two stages, twelve acts and one very long night of electronic, indie and fusion music by the lake.','Various artists',NULL,NULL,'Multilingual',480,'16+',TRUE),
 ('Champions Arena: Hyderabad Hawks vs Mumbai Mariners','champions-arena-hawks-mariners','CRICKET','deccan-arena-hyd','Floodlit T20 league clash between two unbeaten sides. Gates open two hours before first ball.',NULL,'Hyderabad Hawks','Mumbai Mariners','English, Hindi, Telugu',210,'All ages',TRUE),
 ('Deccan Derby: Hyderabad Hawks FC vs Bengaluru Blaze','deccan-derby-football','FOOTBALL','deccan-arena-hyd','The southern derby returns. Ninety minutes, two rival fan-zones and one bragging-rights trophy.',NULL,'Hyderabad Hawks FC','Bengaluru Blaze','English',120,'All ages',FALSE),
 ('Pro Raiders Kabaddi Night','pro-raiders-kabaddi','KABADDI','deccan-arena-hyd','Double-header of fast-paced professional kabaddi under the lights.',NULL,'Telangana Titans XI','Chennai Chargers','Telugu, Tamil',150,'All ages',FALSE),
 ('The Tin Crown','the-tin-crown','THEATRE','rangmanch-theatre-hyd','A darkly funny two-act play about a travelling drama troupe that inherits a bankrupt kingdom.','Rangmanch Repertory',NULL,NULL,'English',110,'12+',TRUE),
 ('Stories from the Charminar','stories-from-the-charminar','THEATRE','rangmanch-theatre-hyd','Five short plays woven from four centuries of Old City folklore.','Qissa Collective',NULL,NULL,'Hindi, Urdu',95,'All ages',FALSE),
 ('Strike Night Bowling','strike-night-bowling','BOWLING','strikezone-hyd','One-hour lane booking for up to six players, shoes included.',NULL,NULL,NULL,NULL,60,'All ages',FALSE),
 ('The Vault Escape Room','the-vault-escape-room','ESCAPE_ROOM','riddle-rooms-hyd','Crack a 1970s bank vault in sixty minutes. Teams of 2-6.',NULL,NULL,NULL,'English, Hindi',60,'12+',TRUE),
 ('Indie Garden Sessions','indie-garden-sessions','CONCERT','garden-city-grounds-blr','Open-air evening with Bengaluru''s favourite indie bands.','The Monsoon Radio',NULL,NULL,'English, Kannada',240,'All ages',FALSE),
 ('Watercolour Weekend Workshop','watercolour-weekend','WORKSHOP','canvas-studio-blr','A beginner-friendly three-hour watercolour workshop. All materials provided.','Studio Nila',NULL,NULL,'English',180,'10+',FALSE),
 ('Harbour Cup Final','harbour-cup-final','CRICKET','harbour-stadium-mum','The season finale at the seaside stadium.',NULL,'Mumbai Mariners','Delhi Dynamos','English, Hindi',210,'All ages',FALSE)
) AS x(title, slug, cat, venue, descr, artist, th, ta, langs, dur, age, feat)
JOIN event_categories ec ON ec.code = x.cat JOIN venues v ON v.slug = x.venue;

-- Sessions: one-off events get a single evening; plays get three dates; activities get hourly slots on the next 7 days.
INSERT INTO event_sessions (event_id, start_time, end_time)
SELECT e.id,
       (CURRENT_DATE + x.day_offset + x.at)::timestamp AT TIME ZONE 'Asia/Kolkata',
       (CURRENT_DATE + x.day_offset + x.at)::timestamp AT TIME ZONE 'Asia/Kolkata' + make_interval(mins => e.duration_min)
FROM (VALUES
 ('hyderabad-comedy-night', 5, time '20:00'), ('hyderabad-comedy-night', 12, time '20:00'),
 ('neon-music-festival', 20, time '16:00'),
 ('champions-arena-hawks-mariners', 9, time '19:30'),
 ('deccan-derby-football', 14, time '19:00'),
 ('pro-raiders-kabaddi', 7, time '19:00'),
 ('the-tin-crown', 3, time '19:30'), ('the-tin-crown', 4, time '19:30'), ('the-tin-crown', 10, time '16:00'),
 ('stories-from-the-charminar', 6, time '18:30'),
 ('indie-garden-sessions', 11, time '17:00'),
 ('watercolour-weekend', 6, time '10:00'),
 ('harbour-cup-final', 25, time '19:30')
) AS x(slug, day_offset, at) JOIN events e ON e.slug = x.slug;

INSERT INTO event_sessions (event_id, start_time, end_time)
SELECT e.id,
       (CURRENT_DATE + d + make_time(h, 0, 0))::timestamp AT TIME ZONE 'Asia/Kolkata',
       (CURRENT_DATE + d + make_time(h, 0, 0))::timestamp AT TIME ZONE 'Asia/Kolkata' + interval '60 minutes'
FROM events e CROSS JOIN generate_series(1, 7) d CROSS JOIN generate_series(11, 21, 2) h
WHERE e.slug IN ('strike-night-bowling', 'the-vault-escape-room');

INSERT INTO event_ticket_categories (session_id, name, description, price, capacity, sold, max_per_booking, sort_order)
SELECT s.id, t.name, t.descr, t.price, t.cap, t.sold, t.maxb, t.ord
FROM event_sessions s JOIN events e ON e.id = s.event_id
JOIN (VALUES
 ('hyderabad-comedy-night','Silver','Rows 6 onwards',499,120,67,6,3), ('hyderabad-comedy-night','Gold','Rows 2-5',799,80,41,6,2), ('hyderabad-comedy-night','VIP','Front row + meet & greet',1499,20,9,4,1),
 ('neon-music-festival','General','Standing, both stages',1299,5000,2104,8,3), ('neon-music-festival','Gold','Fan pit',2499,2000,860,6,2), ('neon-music-festival','VIP','Lounge with dedicated bar',5999,300,212,4,1),
 ('champions-arena-hawks-mariners','East Stand','Upper tier',800,15000,9120,8,4), ('champions-arena-hawks-mariners','West Stand','Lower tier',1500,12000,8800,8,3), ('champions-arena-hawks-mariners','Pavilion','Covered seating',3500,6000,4100,6,2), ('champions-arena-hawks-mariners','Corporate Box','Hospitality box',12000,400,256,4,1),
 ('deccan-derby-football','General','Open stands',399,20000,6400,8,2), ('deccan-derby-football','Premium','Centre-line seating',999,5000,1900,6,1),
 ('pro-raiders-kabaddi','General','Open stands',299,10000,2300,8,2), ('pro-raiders-kabaddi','Courtside','First three rows',1299,300,140,4,1),
 ('the-tin-crown','Balcony','Upper level',350,200,64,8,2), ('the-tin-crown','Stalls','Ground level',650,400,190,8,1),
 ('stories-from-the-charminar','Standard','Free seating',400,600,122,8,1),
 ('indie-garden-sessions','General','Lawn access',899,6000,1450,8,2), ('indie-garden-sessions','VIP','Seated deck',2499,400,120,4,1),
 ('watercolour-weekend','Participant','Includes materials kit',1800,24,17,2,1),
 ('harbour-cup-final','North Stand','Upper tier',1000,15000,7000,8,2), ('harbour-cup-final','Pavilion','Covered seating',4000,5000,3100,6,1),
 ('strike-night-bowling','Lane (up to 6 players)','One lane for 60 minutes',1200,8,0,2,1),
 ('the-vault-escape-room','Team pass (2-6 players)','One room for 60 minutes',2400,2,0,1,1)
) AS t(slug, name, descr, price, cap, sold, maxb, ord) ON t.slug = e.slug;
