CREATE TABLE orders (
    id BIGINT NOT NULL PRIMARY KEY,
    sku VARCHAR(32),
    quantity INTEGER,
    amount DECIMAL(12, 2),
    shipped BOOLEAN,
    ordered_on DATE,
    created_at TIMESTAMP(6)
);
