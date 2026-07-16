DO
$$
BEGIN
    -- On a brand-new database Hibernate creates the table (and its unique
    -- constraint) after Flyway. Existing installations already have the table
    -- and need their historical duplicates consolidated first.
    IF
to_regclass('public.portfolio_holdings') IS NULL THEN
        RETURN;
END IF;

WITH rolled_up AS (SELECT user_id,
                          instrument_code,
                          MIN(id)       AS keep_id,
                          SUM(quantity) AS total_quantity,
                          CASE
                              WHEN SUM(quantity) = 0 THEN MAX(buy_price)
                              ELSE ROUND(SUM(buy_price * quantity) / SUM(quantity), 2)
                              END       AS average_buy_price,
                          (ARRAY_AGG(symbol ORDER BY acquired_at DESC, id DESC))[1] AS latest_symbol, (ARRAY_AGG(live_price ORDER BY acquired_at DESC, id DESC))[1] AS latest_live_price, MIN (acquired_at) AS first_acquired_at
FROM portfolio_holdings
GROUP BY user_id, instrument_code
HAVING COUNT (*)
     > 1
    )
UPDATE portfolio_holdings holding
SET quantity    = rolled_up.total_quantity,
    buy_price   = rolled_up.average_buy_price,
    symbol      = rolled_up.latest_symbol,
    live_price  = rolled_up.latest_live_price,
    acquired_at = rolled_up.first_acquired_at FROM rolled_up
WHERE holding.id = rolled_up.keep_id;

WITH canonical AS (SELECT user_id, instrument_code, MIN(id) AS keep_id
                   FROM portfolio_holdings
                   GROUP BY user_id, instrument_code
                   HAVING COUNT(*) > 1)
DELETE
FROM portfolio_holdings duplicate USING canonical
WHERE duplicate.user_id = canonical.user_id
  AND duplicate.instrument_code = canonical.instrument_code
  AND duplicate.id <> canonical.keep_id;

IF
NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_portfolio_user_instrument'
          AND conrelid = 'portfolio_holdings'::regclass
    ) THEN
ALTER TABLE portfolio_holdings
    ADD CONSTRAINT uk_portfolio_user_instrument
        UNIQUE (user_id, instrument_code);
END IF;
END
$$;
