INSERT INTO airports (id, city, airport_code, country, is_deleted, latitude, longitude, airport_name, timezone)
VALUES ('00000000-0000-0000-0000-000000000001', 'Hanoi', 'HAN', 'Vietnam', false, 21.221, 105.804,
        'Noi Bai International Airport', 'Asia/Bangkok'),
       ('00000000-0000-0000-0000-000000000002', 'Ho Chi Minh City', 'SGN', 'Vietnam', false, 10.818, 106.652,
        'Tan Son Nhat International Airport', 'Asia/Bangkok'),
       ('00000000-0000-0000-0000-000000000003', 'Bangkok', 'BKK', 'Thailand', false, 13.690, 100.750,
        'Suvarnabhumi Airport', 'Asia/Bangkok'),
       ('00000000-0000-0000-0000-000000000004', 'Singapore', 'SIN', 'Singapore', false, 1.364, 103.991,
        'Changi Airport', 'Asia/Singapore'),
       ('00000000-0000-0000-0000-000000000005', 'Tokyo', 'HND', 'Japan', false, 35.549, 139.779, 'Haneda Airport',
        'Asia/Tokyo'),
       ('00000000-0000-0000-0000-000000000006', 'Seoul', 'ICN', 'South Korea', false, 37.460, 126.440,
        'Incheon International Airport', 'Asia/Seoul'),
       ('00000000-0000-0000-0000-000000000007', 'Beijing', 'PEK', 'China', false, 40.080, 116.584,
        'Beijing Capital International Airport', 'Asia/Shanghai'),
       ('00000000-0000-0000-0000-000000000008', 'New York', 'JFK', 'USA', false, 40.641, -73.778,
        'John F. Kennedy International Airport', 'America/New_York'),
       ('00000000-0000-0000-0000-000000000009', 'Los Angeles', 'LAX', 'USA', false, 33.941, -118.408,
        'Los Angeles International Airport', 'America/Los_Angeles'),
       ('00000000-0000-0000-0000-000000000010', 'London', 'LHR', 'United Kingdom', false, 51.470, -0.454,
        'Heathrow Airport', 'Europe/London'),
       ('00000000-0000-0000-0000-000000000011', 'Paris', 'CDG', 'France', false, 49.009, 2.547,
        'Charles de Gaulle Airport', 'Europe/Paris'),
       ('00000000-0000-0000-0000-000000000012', 'Frankfurt', 'FRA', 'Germany', false, 50.037, 8.562,
        'Frankfurt Airport', 'Europe/Berlin'),
       ('00000000-0000-0000-0000-000000000013', 'Amsterdam', 'AMS', 'Netherlands', false, 52.310, 4.768,
        'Schiphol Airport', 'Europe/Amsterdam'),
       ('00000000-0000-0000-0000-000000000014', 'Dubai', 'DXB', 'UAE', false, 25.253, 55.365,
        'Dubai International Airport', 'Asia/Dubai'),
       ('00000000-0000-0000-0000-000000000015', 'Doha', 'DOH', 'Qatar', false, 25.273, 51.608,
        'Hamad International Airport', 'Asia/Qatar'),
       ('00000000-0000-0000-0000-000000000016', 'Sydney', 'SYD', 'Australia', false, -33.939, 151.175,
        'Sydney Kingsford Smith Airport', 'Australia/Sydney'),
       ('00000000-0000-0000-0000-000000000017', 'Melbourne', 'MEL', 'Australia', false, -37.673, 144.843,
        'Melbourne Airport', 'Australia/Melbourne'),
       ('00000000-0000-0000-0000-000000000018', 'Auckland', 'AKL', 'New Zealand', false, -37.009, 174.785,
        'Auckland Airport', 'Pacific/Auckland'),
       ('00000000-0000-0000-0000-000000000019', 'Toronto', 'YYZ', 'Canada', false, 43.677, -79.624,
        'Toronto Pearson International Airport', 'America/Toronto'),
       ('00000000-0000-0000-0000-000000000020', 'Vancouver', 'YVR', 'Canada', false, 49.194, -123.184,
        'Vancouver International Airport', 'America/Vancouver'),
       ('00000000-0000-0000-0000-000000000021', 'São Paulo', 'GRU', 'Brazil', false, -23.435, -46.473,
        'Guarulhos International Airport', 'America/Sao_Paulo'),
       ('00000000-0000-0000-0000-000000000022', 'Buenos Aires', 'EZE', 'Argentina', false, -34.822, -58.535,
        'Ministro Pistarini International Airport', 'America/Argentina/Buenos_Aires'),
       ('00000000-0000-0000-0000-000000000023', 'Cape Town', 'CPT', 'South Africa', false, -33.970, 18.601,
        'Cape Town International Airport', 'Africa/Johannesburg'),
       ('00000000-0000-0000-0000-000000000024', 'Istanbul', 'IST', 'Turkey', false, 41.260, 28.742, 'Istanbul Airport',
        'Europe/Istanbul'),
       ('00000000-0000-0000-0000-000000000025', 'Delhi', 'DEL', 'India', false, 28.556, 77.100,
        'Indira Gandhi International Airport', 'Asia/Kolkata');

-- Seat-specific Benefits for different fare classes
INSERT INTO flight_benefits (id, is_deleted, benefit_name, benefit_description, benefit_icon_url)
VALUES
    -- Economy Class Benefits
    ('10000000-0000-0000-0000-000000000001', false, 'Standard Baggage', 'One carry-on bag and one checked bag up to 20kg', 'baggage.svg'),
    ('10000000-0000-0000-0000-000000000002', false, 'Meal Service', 'Standard meal and beverage service', 'meal.svg'),
    ('10000000-0000-0000-0000-000000000003', false, 'Entertainment', 'Access to in-flight entertainment system', 'entertainment.svg'),
    ('10000000-0000-0000-0000-000000000004', false, 'USB Charging', 'USB charging port at your seat', 'charging.svg'),
    
    -- Business Class Benefits
    ('10000000-0000-0000-0000-000000000005', false, 'Premium Baggage', 'Two checked bags up to 32kg each', 'premium-baggage.svg'),
    ('10000000-0000-0000-0000-000000000006', false, 'Priority Boarding', 'Early boarding and dedicated check-in', 'priority.svg'),
    ('10000000-0000-0000-0000-000000000007', false, 'Lounge Access', 'Access to business class airport lounges', 'lounge.svg'),
    ('10000000-0000-0000-0000-000000000008', false, 'Premium Meals', 'Chef-curated meals with premium beverages', 'premium-meal.svg'),
    ('10000000-0000-0000-0000-000000000009', false, 'Wider Seats', 'Wider seats with additional legroom', 'seat-comfort.svg'),
    ('10000000-0000-0000-0000-000000000010', false, 'Dedicated Cabin Crew', 'Personalized service from dedicated staff', 'service.svg'),
    ('10000000-0000-0000-0000-000000000011', false, 'Power Outlets', 'AC power outlets at your seat', 'power.svg'),
    ('10000000-0000-0000-0000-000000000012', false, 'Premium Bedding', 'Premium pillows and blankets for comfort', 'bedding.svg'),
    
    -- First Class Benefits
    ('10000000-0000-0000-0000-000000000013', false, 'Luxury Baggage', 'Three checked bags up to 32kg each', 'luxury-baggage.svg'),
    ('10000000-0000-0000-0000-000000000014', false, 'Private Suite', 'Private enclosed suite with door', 'suite.svg'),
    ('10000000-0000-0000-0000-000000000015', false, 'Chauffeur Service', 'Complimentary chauffeur service to/from airport', 'chauffeur.svg'),
    ('10000000-0000-0000-0000-000000000016', false, 'Exclusive Lounge', 'Access to first class exclusive lounges', 'exclusive-lounge.svg'),
    ('10000000-0000-0000-0000-000000000017', false, 'Gourmet Dining', 'Multi-course gourmet dining experience', 'gourmet.svg'),
    ('10000000-0000-0000-0000-000000000018', false, 'Full-flat Bed', 'Fully flat bed with premium mattress', 'bed.svg'),
    ('10000000-0000-0000-0000-000000000019', false, 'Luxury Amenity Kit', 'Premium amenity kit with luxury skincare products', 'amenity.svg'),
    ('10000000-0000-0000-0000-000000000020', false, 'Personal Wardrobe', 'Personal wardrobe for your clothes', 'wardrobe.svg'),
    ('10000000-0000-0000-0000-000000000021', false, 'Shower Spa', 'Access to onboard shower spa (on select aircraft)', 'shower.svg');

-- Routes to and from Vietnam (Hanoi - HAN and Ho Chi Minh City - SGN)
INSERT INTO routes (id, is_deleted, estimated_duration_minutes, origin_airport_id, destination_airport_id)
VALUES
    -- Hanoi (HAN) to various destinations
    ('20000000-0000-0000-0000-000000000001', false, 150, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003'), -- Hanoi to Bangkok
    ('20000000-0000-0000-0000-000000000002', false, 300, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000004'), -- Hanoi to Singapore
    ('20000000-0000-0000-0000-000000000003', false, 270, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000005'), -- Hanoi to Tokyo
    ('20000000-0000-0000-0000-000000000004', false, 240, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000006'), -- Hanoi to Seoul
    ('20000000-0000-0000-0000-000000000005', false, 180, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000007'), -- Hanoi to Beijing
    ('20000000-0000-0000-0000-000000000006', false, 900, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010'), -- Hanoi to London
    ('20000000-0000-0000-0000-000000000007', false, 480, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000014'), -- Hanoi to Dubai
    ('20000000-0000-0000-0000-000000000008', false, 180, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000025'), -- Hanoi to Delhi
    ('20000000-0000-0000-0000-000000000009', false, 60, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002'), -- Hanoi to Ho Chi Minh City
    
    -- Various destinations to Hanoi (HAN)
    ('20000000-0000-0000-0000-000000000010', false, 150, '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001'), -- Bangkok to Hanoi
    ('20000000-0000-0000-0000-000000000011', false, 300, '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001'), -- Singapore to Hanoi
    ('20000000-0000-0000-0000-000000000012', false, 270, '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001'), -- Tokyo to Hanoi
    ('20000000-0000-0000-0000-000000000013', false, 240, '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000001'), -- Seoul to Hanoi
    ('20000000-0000-0000-0000-000000000014', false, 180, '00000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000001'), -- Beijing to Hanoi
    ('20000000-0000-0000-0000-000000000015', false, 900, '00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000001'), -- London to Hanoi
    ('20000000-0000-0000-0000-000000000016', false, 480, '00000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-000000000001'), -- Dubai to Hanoi
    ('20000000-0000-0000-0000-000000000017', false, 180, '00000000-0000-0000-0000-000000000025', '00000000-0000-0000-0000-000000000001'), -- Delhi to Hanoi
    ('20000000-0000-0000-0000-000000000018', false, 60, '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001'), -- Ho Chi Minh City to Hanoi

    -- Ho Chi Minh City (SGN) to various destinations
    ('20000000-0000-0000-0000-000000000019', false, 90, '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003'), -- HCMC to Bangkok
    ('20000000-0000-0000-0000-000000000020', false, 120, '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000004'), -- HCMC to Singapore
    ('20000000-0000-0000-0000-000000000021', false, 300, '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005'), -- HCMC to Tokyo
    ('20000000-0000-0000-0000-000000000022', false, 270, '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000006'), -- HCMC to Seoul
    ('20000000-0000-0000-0000-000000000023', false, 240, '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000007'), -- HCMC to Beijing
    ('20000000-0000-0000-0000-000000000024', false, 840, '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000010'), -- HCMC to London
    ('20000000-0000-0000-0000-000000000025', false, 420, '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000014'), -- HCMC to Dubai
    ('20000000-0000-0000-0000-000000000026', false, 210, '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000025'), -- HCMC to Delhi
    
    -- Various destinations to Ho Chi Minh City (SGN)
    ('20000000-0000-0000-0000-000000000027', false, 90, '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002'), -- Bangkok to HCMC
    ('20000000-0000-0000-0000-000000000028', false, 120, '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000002'), -- Singapore to HCMC
    ('20000000-0000-0000-0000-000000000029', false, 300, '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000002'), -- Tokyo to HCMC
    ('20000000-0000-0000-0000-000000000030', false, 270, '00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000002'), -- Seoul to HCMC
    ('20000000-0000-0000-0000-000000000031', false, 240, '00000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000002'), -- Beijing to HCMC
    ('20000000-0000-0000-0000-000000000032', false, 840, '00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000002'), -- London to HCMC
    ('20000000-0000-0000-0000-000000000033', false, 420, '00000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-000000000002'), -- Dubai to HCMC
    ('20000000-0000-0000-0000-000000000034', false, 210, '00000000-0000-0000-0000-000000000025', '00000000-0000-0000-0000-000000000002'); -- Delhi to HCMC