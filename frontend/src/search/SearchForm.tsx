import { useState, type ChangeEvent, type FormEvent } from 'react'
import { EMPTY_FILTERS, normalize, validate, type SearchFilters } from './filters'

interface Props {
  initial: SearchFilters
  onSearch: (filters: SearchFilters) => void
  /** Problem detail from the API when it rejected the last search. */
  serverError?: string | null
}

/** The four filters of the assessment. Validation mirrors the API so a typo never leaves the browser. */
export default function SearchForm({ initial, onSearch, serverError }: Props) {
  const [draft, setDraft] = useState<SearchFilters>(initial)
  const [error, setError] = useState<string | null>(null)

  const update = (field: keyof SearchFilters) => (event: ChangeEvent<HTMLInputElement>) =>
    setDraft((current) => ({ ...current, [field]: event.target.value }))

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const filters = normalize(draft)
    const problem = validate(filters)
    setError(problem)
    if (!problem) onSearch(filters)
  }

  const clear = () => {
    setDraft(EMPTY_FILTERS)
    setError(null)
    onSearch(EMPTY_FILTERS)
  }

  const shown = error ?? serverError ?? null
  return (
    <form className="search-form" onSubmit={submit} noValidate aria-label="Search itineraries">
      <label>
        <span>Origin</span>
        <input name="origin" value={draft.origin} onChange={update('origin')} placeholder="MAD" maxLength={3}
          autoCapitalize="characters" spellCheck={false} />
      </label>
      <label>
        <span>Destination</span>
        <input name="destination" value={draft.destination} onChange={update('destination')} placeholder="BCN"
          maxLength={3} autoCapitalize="characters" spellCheck={false} />
      </label>
      <label>
        <span>Max price (EUR)</span>
        <input name="maxPrice" type="number" inputMode="decimal" min={1} step={0.01} value={draft.maxPrice}
          onChange={update('maxPrice')} placeholder="150" />
      </label>
      <label>
        <span>Carrier</span>
        <input name="carrier" value={draft.carrier} onChange={update('carrier')} placeholder="IB" maxLength={2}
          autoCapitalize="characters" spellCheck={false} />
      </label>
      <div className="actions">
        <button type="submit">Search</button>
        <button type="button" className="secondary" onClick={clear}>Clear</button>
      </div>
      {shown && <p role="alert" className="error">{shown}</p>}
    </form>
  )
}
