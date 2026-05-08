export function moneyError(value: string | number | undefined | null) {
  const raw = String(value ?? '').trim()
  if (!raw) {
    return '请填写金额'
  }
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(raw)) {
    return '金额最多 10 位整数，并保留 2 位以内小数'
  }
  if (Number(raw) < 0.01) {
    return '金额必须大于 0'
  }
  return ''
}
