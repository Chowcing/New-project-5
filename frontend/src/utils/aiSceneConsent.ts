export type AiSceneConsent = 'UNSET' | 'ENABLED' | 'DISABLED'

function storageKey(userId: number) {
  return `expense.aiSceneConsent.${userId}`
}

export function loadAiSceneConsent(userId: number): AiSceneConsent {
  const value = localStorage.getItem(storageKey(userId))
  return value === 'ENABLED' || value === 'DISABLED' ? value : 'UNSET'
}

export function saveAiSceneConsent(
  userId: number,
  state: AiSceneConsent
): AiSceneConsent {
  localStorage.setItem(storageKey(userId), state)
  return state
}
