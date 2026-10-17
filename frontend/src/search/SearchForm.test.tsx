import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import SearchForm from './SearchForm'
import { EMPTY_FILTERS } from './filters'

describe('SearchForm', () => {
  it('submits the four filters normalised', async () => {
    const onSearch = vi.fn()
    const user = userEvent.setup()
    render(<SearchForm initial={EMPTY_FILTERS} onSearch={onSearch} />)

    await user.type(screen.getByLabelText('Origin'), 'mad')
    await user.type(screen.getByLabelText('Destination'), 'bcn')
    await user.type(screen.getByLabelText('Max price (EUR)'), '150')
    await user.type(screen.getByLabelText('Carrier'), 'ib')
    await user.click(screen.getByRole('button', { name: 'Search' }))

    expect(onSearch).toHaveBeenCalledWith({ origin: 'MAD', destination: 'BCN', maxPrice: '150', carrier: 'IB' })
  })

  it('blocks a malformed code and says which field is wrong', async () => {
    const onSearch = vi.fn()
    const user = userEvent.setup()
    render(<SearchForm initial={EMPTY_FILTERS} onSearch={onSearch} />)

    await user.type(screen.getByLabelText('Carrier'), 'i')
    await user.click(screen.getByRole('button', { name: 'Search' }))

    expect(onSearch).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('Carrier must be a 2-character IATA code')
  })

  it('clear searches again with no filters', async () => {
    const onSearch = vi.fn()
    const user = userEvent.setup()
    render(<SearchForm initial={{ origin: 'MAD', destination: 'BCN', maxPrice: '150', carrier: 'IB' }} onSearch={onSearch} />)

    expect(screen.getByLabelText('Origin')).toHaveValue('MAD')
    await user.click(screen.getByRole('button', { name: 'Clear' }))

    expect(onSearch).toHaveBeenCalledWith(EMPTY_FILTERS)
    expect(screen.getByLabelText('Origin')).toHaveValue('')
  })

  it('shows the problem the API answered with', () => {
    render(<SearchForm initial={EMPTY_FILTERS} onSearch={vi.fn()} serverError="Invalid IATA airport code: MADRID" />)

    expect(screen.getByRole('alert')).toHaveTextContent('Invalid IATA airport code: MADRID')
  })
})
