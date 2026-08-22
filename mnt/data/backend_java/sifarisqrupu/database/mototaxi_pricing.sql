-- Only needed when Hibernate schema auto-update is disabled.
-- MySQL / MariaDB

CREATE TABLE IF NOT EXISTS mototaxi_pricing (
    id BIGINT NOT NULL,
    man_minimum_price DECIMAL(10,2) NOT NULL,
    man_price_per_km DECIMAL(10,2) NOT NULL,
    woman_minimum_price DECIMAL(10,2) NOT NULL,
    woman_price_per_km DECIMAL(10,2) NOT NULL,
    delivery_minimum_price DECIMAL(10,2) NOT NULL,
    delivery_price_per_km DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO mototaxi_pricing (
    id,
    man_minimum_price,
    man_price_per_km,
    woman_minimum_price,
    woman_price_per_km,
    delivery_minimum_price,
    delivery_price_per_km
)
VALUES (1, 2.00, 0.60, 2.00, 0.60, 2.00, 0.60)
ON DUPLICATE KEY UPDATE id = id;
