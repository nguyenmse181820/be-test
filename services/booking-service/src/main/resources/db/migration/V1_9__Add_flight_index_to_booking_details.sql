-- Add flight_index column to booking_details table
-- This column will store the flight index for proper ordering of flight segments
-- Positive indices (1, 2, 3, ...) for outbound flights
-- Negative indices (-1, -2, -3, ...) for return flights
-- This supports round-trip and connecting flight detection

ALTER TABLE booking_details 
ADD COLUMN flight_index INTEGER;

-- Add index for better performance when querying by flight index
CREATE INDEX idx_booking_details_flight_index 
ON booking_details(flight_index);

-- Add comment to explain the purpose
COMMENT ON COLUMN booking_details.flight_index IS 'Index for flight ordering: positive (1,2,3...) for outbound, negative (-1,-2,-3...) for return flights';
