# Mock GDS providers

Simulates the three external providers the aggregator ingests. Tooling only: it is
not part of the solution under review and carries no test suite by decision. It is
verified manually with the commands at the end of this file.

The team did not answer our questions about the real providers, so these contracts
are our assumptions. The backend adapters are built against them.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/alpha/flights` | Full Alpha inventory as a JSON array, streamed |
| GET | `/beta/flights` | Full Beta inventory as nested XML, streamed |
| GET | `/gamma/flights` | Full Gamma inventory as CSV, one row per segment, streamed |
| GET | `/{provider}/meta` | Seed, size, share, expected row counts and current chaos settings |
| GET | `/admin/chaos` | Chaos settings of all providers |
| PUT | `/admin/chaos/{provider}` | Replace one provider's chaos settings at runtime |
| DELETE | `/admin/chaos/{provider}` | Reset one provider to its configured defaults |
| GET | `/actuator/health` | Liveness, used by the compose healthcheck |

`/flights` accepts optional query parameters `origin` and `destination` (IATA codes)
and `date` (`yyyy-MM-dd`, departure day at the origin airport). Invalid values give a
400 problem detail. No endpoint requires authentication.

## Inventory

The inventory is a pure function of `MOCK_SEED` and the itinerary index, so the same
configuration streams byte-identical data on every boot and nothing is held in memory.
Indices spread over a grid of day, departure slot, route and carrier for direct
flights, plus day, slot and route for one-stop itineraries through a hub, so every
`(carrier, flight number, departure)` is unique by construction. Cells interleave as
two direct flights then one one-stop itinerary, so a third of any inventory size has
a connection. The default 100 000 itineraries span 30 days; a larger size extends
the window.

Each provider carries a deterministic subset of that master inventory, ALPHA 70 %,
BETA 65 %, GAMMA 60 %, at its own price, the base times a factor in `[0.90, 1.15]`.
Roughly a third of the itineraries therefore appear in all three providers with
different prices, which is what the deduplication and cheapest-offer logic needs.

With probability `MOCK_CORRUPTION_RATE` a provider's copy is damaged in one of five
ways: a dead leg (destination equals origin), a bad connection (second leg departs
from an airport other than the first leg's arrival), a negative duration, a
ten-minute layover, or a missing carrier code. The master itinerary stays valid, so
the same itinerary can be clean from one provider and broken from another.

## Contracts

### Alpha, `application/json`

```json
[
  {"offerId":"ALPHA-000123","price":89.9,"currency":"EUR","legs":[
    {"carrier":"IB","flightNumber":"0845","from":"MAD","to":"BCN",
     "dep":"2026-11-02T07:15:00+01:00","arr":"2026-11-02T08:35:00+01:00"}]}
]
```

Quirks: price is a JSON number (double), flight numbers are zero-padded to four
digits, times are ISO-8601 with offset. Error body on a 500:
`{"error":"upstream failure","code":500}`.

### Beta, `application/xml`

```xml
<BetaResponse>
  <Header><Provider>Mock-Beta</Provider><GeneratedAt>…</GeneratedAt><Seed>42</Seed></Header>
  <Itineraries>
    <Itinerary ref="BETA-000123">
      <Pricing><Fare currency="EUR"><Base>79.11</Base><Taxes>10.79</Taxes><Total>89.90</Total></Fare></Pricing>
      <Legs>
        <Leg>
          <Carrier><Code>IB</Code><Name>Iberia</Name></Carrier>
          <FlightNumber>845</FlightNumber>
          <Departure><Airport>MAD</Airport><DateTime>2026-11-02T07:15:00+01:00</DateTime></Departure>
          <Arrival><Airport>BCN</Airport><DateTime>2026-11-02T08:35:00+01:00</DateTime></Arrival>
        </Leg>
      </Legs>
    </Itinerary>
  </Itineraries>
</BetaResponse>
```

Quirks: price split into base and taxes, `Total` is the comparable amount; flight
numbers unpadded; times ISO-8601 with offset. Error body on a 500:
`<Error><Code>500</Code><Message>upstream failure</Message></Error>`.

### Gamma, `text/csv`

```
offer_id,flight,origin,destination,departure_utc,arrival_utc,price,currency,segment_index,segment_count
GAMMA-000123,IB845,MAD,BCN,2026-11-02 06:15,2026-11-02 07:35,89.90,EUR,1,1
GAMMA-000124,IB612,MAD,LHR,2026-11-02 08:40,2026-11-02 11:05,239.00,EUR,1,2
GAMMA-000124,BA2845,LHR,DUB,2026-11-02 13:20,2026-11-02 14:45,239.00,EUR,2,2
```

Quirks: `flight` is carrier and number concatenated; times are `yyyy-MM-dd HH:mm`
in UTC with no offset; the offer price is repeated on every row; rows of one offer
are contiguous and ordered by `segment_index`. Error body on a 500: plain text
`ERROR 500 upstream failure`.

The three quirk sets are intentional. Each backend adapter must normalise flight
numbers, time zones and price representation before building the deduplication
signature, which is exactly what an anti-corruption layer is for.

## Latency and failures

Before the first byte of any `/flights` response the provider sleeps for a uniform
random delay in `[latencyMinMs, latencyMaxMs]`, then with probability `errorRate`
answers 500 in its own format. Responses carry `X-Mock-Provider` and
`X-Mock-Delay-Ms`. `/meta`, `/admin` and `/actuator` are never delayed or failed.

Change settings while running:

```bash
curl -s localhost:8081/admin/chaos
curl -s -X PUT localhost:8081/admin/chaos/beta -H 'content-type: application/json' \
     -d '{"enabled":true,"latencyMinMs":0,"latencyMaxMs":0,"errorRate":1}'   # beta always fails
curl -s -X DELETE localhost:8081/admin/chaos/beta                               # back to defaults
```

## Configuration

| Env var | Default | Meaning |
|---|---|---|
| `MOCK_SEED` | `42` | Seed of every deterministic decision |
| `MOCK_INVENTORY_SIZE` | `100000` | Unique itineraries across all providers, up to 1 000 000 and beyond |
| `MOCK_INVENTORY_DAYS` | `30` | Departure window in days; grows automatically if the size needs it |
| `MOCK_BASE_DATE` | today (UTC) | First departure day, `yyyy-MM-dd`. Set it for byte-stable data across days |
| `MOCK_CORRUPTION_RATE` | `0.015` | Share of provider copies that are damaged |
| `MOCK_CHAOS_ENABLED` | `true` | Master switch for latency and failures |
| `MOCK_LATENCY_MIN_MS` | `100` | Lower bound of the artificial delay |
| `MOCK_LATENCY_MAX_MS` | `5000` | Upper bound of the artificial delay |
| `MOCK_ERROR_RATE` | `0.10` | Probability of a 500 per request |

Per-provider overrides go under `mock.chaos.providers.<alpha|beta|gamma>` in
`application.yml`.

## Measured, 2026-10-16

`MOCK_INVENTORY_SIZE=1000000`, chaos off, container limited to 256 MB with `docker run -m 256m`:

| Provider | Offers | Payload | Time | Container memory while streaming |
|---|---|---|---|---|
| Alpha JSON | 699 679 | 169 MB | 1.5 s | 141 MiB |
| Beta XML | 649 k | 344 MB | 4.0 s | 142 MiB |
| Gamma CSV | 598 k, 798 870 rows | 61 MB | 0.7 s | 143 MiB |

Memory is flat from boot to the end of the largest stream, which is what the
index-based generator is for. At the default 100 000 the three streams take
0.1 s to 0.5 s.

## Manual verification

```bash
docker compose up --build -d providers-mock
MOCK=localhost:8081

# disable chaos for the data checks
for p in alpha beta gamma; do curl -s -X PUT $MOCK/admin/chaos/$p -H 'content-type: application/json' \
  -d '{"enabled":false,"latencyMinMs":0,"latencyMaxMs":0,"errorRate":0}' > /dev/null; done

curl -s $MOCK/alpha/meta; echo
curl -s "$MOCK/alpha/flights?origin=MAD&destination=BCN" | head -c 600; echo
curl -s $MOCK/beta/flights | head -c 1500; echo
curl -s $MOCK/gamma/flights | head -5

# counts match meta, data is deterministic, offer ids are unique
curl -s $MOCK/gamma/flights | tail -n +2 | wc -l
curl -s $MOCK/alpha/flights | md5; curl -s $MOCK/alpha/flights | md5
curl -s $MOCK/gamma/flights | tail -n +2 | cut -d, -f1 | sort -u | wc -l

# corruption and quirks are visible
curl -s $MOCK/gamma/flights | awk -F, '$3 == $4' | head -3
curl -s $MOCK/alpha/flights | grep -o '"flightNumber":"0[0-9]*"' | head -3

# chaos
for p in alpha beta gamma; do curl -s -X DELETE $MOCK/admin/chaos/$p > /dev/null; done
for i in $(seq 1 10); do curl -s -o /dev/null -w '%{http_code} %{time_total}s\n' $MOCK/alpha/flights; done
```
