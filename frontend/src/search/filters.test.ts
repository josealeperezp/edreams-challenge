import { describe, expect, it } from 'vitest'
import { EMPTY_FILTERS, fromSearchParams, normalize, readFiltersFromLocation, toSearchParams, validate, writeFiltersToLocation } from './filters'

describe('search filters', () => {
  it('normalises codes and keeps the price as typed', () => {
    expect(normalize({ origin: ' mad', destination: 'bcn ', maxPrice: ' 150 ', carrier: 'ib' })).toEqual({
      origin: 'MAD', destination: 'BCN', maxPrice: '150', carrier: 'IB',
    })
  })

  it('round-trips through URL search params, omitting empty filters', () => {
    const filters = { origin: 'MAD', destination: '', maxPrice: '150', carrier: 'IB' }
    const params = toSearchParams(filters)
    expect(params.toString()).toBe('origin=MAD&maxPrice=150&carrier=IB')
    expect(fromSearchParams(params)).toEqual(filters)
    expect(toSearchParams(EMPTY_FILTERS).toString()).toBe('')
  })

  it('rejects malformed input with a message naming the field', () => {
    expect(validate({ ...EMPTY_FILTERS, origin: 'MADR' })).toMatch(/^Origin/)
    expect(validate({ ...EMPTY_FILTERS, destination: 'B1' })).toMatch(/^Destination/)
    expect(validate({ ...EMPTY_FILTERS, maxPrice: '-5' })).toMatch(/^Max price/)
    expect(validate({ ...EMPTY_FILTERS, carrier: 'IBE' })).toMatch(/^Carrier/)
    expect(validate({ origin: 'MAD', destination: 'BCN', maxPrice: '99.5', carrier: 'U2' })).toBeNull()
    expect(validate(EMPTY_FILTERS)).toBeNull()
  })

  it('writes the filters to the location and reads them back', () => {
    writeFiltersToLocation({ origin: 'MAD', destination: 'BCN', maxPrice: '', carrier: '' })
    expect(window.location.search).toBe('?origin=MAD&destination=BCN')
    expect(readFiltersFromLocation()).toEqual({ origin: 'MAD', destination: 'BCN', maxPrice: '', carrier: '' })

    writeFiltersToLocation(EMPTY_FILTERS)
    expect(window.location.search).toBe('')
  })
})
