# NSE EOD JSON — Read-only evaluation

Status: **Test-only parser added; not a production market-price provider.**

Candidate source: https://github.com/Kathanraj96/nse-eod-data

Raw snapshot: https://raw.githubusercontent.com/Kathanraj96/nse-eod-data/main/prices.json

## Scope approved for this evaluation

- Read and parse the published JSON snapshot without modifying or publishing it.
- Resolve a holding only by an exact normalized ticker symbol. No fuzzy company-name matching.
- Validate source metadata, ISO date, generation timestamp, positive decimal prices, duplicate normalized symbols, and declared record count.
- Treat snapshots more than six calendar days old as stale. Keep a stale price available for inspection, but do not treat it as a fresh quote.
- Preserve the BUY transaction's executed unit price/cost basis. Any later market quote is a separate valuation only.
- If the source is missing, malformed, stale, or has no exact symbol match, retain the last stored quote and surface stale/unavailable status.

## Important limitations

- The source README says the JSON contains daily closing prices for equity shares only. ETFs, mutual funds, and gold are not covered by this dataset.
- The repository README describes data as informational and as-is, but the repository does not provide a clear data redistribution/display licence. An open-source code licence is not a data licence.
- For that reason, the parser is isolated and is not bound to the app's market-data source, is not scheduled, and does not make network requests. Production use requires a separate licensing/terms decision.
- A public GitHub URL is not a service-level agreement. Freshness and availability must be checked on every future evaluation.

## Parser

NseEodJsonSnapshotParser is a pure read-only parser. It accepts raw JSON supplied by a test and returns validated metadata and positive prices. It performs no network access and cannot mutate holdings, BUY transactions, or price history.

## Verification

Unit tests cover valid parsing, case-normalized exact symbol mapping, no fuzzy matching, invalid/zero prices, duplicate normalized symbols, stale snapshots, future timestamps, missing metadata, and malformed JSON.
