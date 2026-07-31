import { describe, expect, it } from 'vitest'
import { eventTone, redactTechnicalValue, rrfContribution } from './presentation'

describe('technical observability presentation', () => {
  it('explains one RRF contribution with the persisted rank constant', () => {
    expect(rrfContribution(5, 60)).toBeCloseTo(1 / 65, 10)
    expect(rrfContribution(null, 60)).toBe(0)
  })

  it('uses a warning tone for degraded or skipped stages', () => {
    expect(eventTone('DEGRADED')).toBe('warning')
    expect(eventTone('SKIPPED')).toBe('warning')
    expect(eventTone('COMPLETED')).toBe('success')
  })

  it('redacts secrets before raw technical data is rendered', () => {
    expect(redactTechnicalValue({ apiKey: 'secret', nested: { password: '123', limit: 20 } }))
      .toEqual({ apiKey: '***', nested: { password: '***', limit: 20 } })
  })
})
