import type { AiSceneRecommendation, TransactionTemplate } from '@/types'

type HistoryScene = Pick<
  TransactionTemplate,
  'categoryId' | 'channel' | 'onlinePlatformId'
>

export type AiSceneDecision =
  | { kind: 'HISTORY_ONLY' }
  | { kind: 'AUTO_APPLY'; ai: AiSceneRecommendation }
  | { kind: 'AGREEMENT'; ai: AiSceneRecommendation }
  | { kind: 'CONFLICT'; ai: AiSceneRecommendation }
  | { kind: 'UNCERTAIN'; ai: AiSceneRecommendation }

export function resolveAiSceneDecision(
  history: HistoryScene | null | undefined,
  ai: AiSceneRecommendation | null | undefined
): AiSceneDecision {
  if (!ai) {
    return { kind: 'HISTORY_ONLY' }
  }
  if (ai.status === 'UNCERTAIN') {
    return { kind: 'UNCERTAIN', ai }
  }
  if (!history) {
    return { kind: 'AUTO_APPLY', ai }
  }

  const categoryConflicts =
    ai.categoryId != null && history.categoryId !== ai.categoryId
  const channelConflicts =
    ai.channel != null && history.channel !== ai.channel
  const platformConflicts =
    ai.onlinePlatformId != null &&
    history.onlinePlatformId != null &&
    history.onlinePlatformId !== ai.onlinePlatformId

  return categoryConflicts || channelConflicts || platformConflicts
    ? { kind: 'CONFLICT', ai }
    : { kind: 'AGREEMENT', ai }
}
