import type { RetrievalJobSnapshot } from '@/types/rag'

export function startRetrievalJobPolling(
  load: () => Promise<RetrievalJobSnapshot>,
  onUpdate: (snapshot: RetrievalJobSnapshot) => void,
  onFailure: (error: unknown) => void,
  intervalMs = 500,
): () => void {
  let stopped = false
  let consecutiveErrors = 0
  let timer: ReturnType<typeof setTimeout> | null = null

  const schedule = () => {
    if (!stopped) timer = setTimeout(tick, intervalMs)
  }

  const tick = async () => {
    if (stopped) return
    try {
      const snapshot = await load()
      if (stopped) return
      consecutiveErrors = 0
      onUpdate(snapshot)
      if (snapshot.status !== 'COMPLETED' && snapshot.status !== 'FAILED') schedule()
    } catch (error) {
      if (stopped) return
      consecutiveErrors += 1
      if (consecutiveErrors >= 3) onFailure(error)
      else schedule()
    }
  }

  void tick()
  return () => {
    stopped = true
    if (timer !== null) clearTimeout(timer)
  }
}
