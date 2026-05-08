export function requiredText(value: string | undefined | null, label: string) {
  if (!String(value ?? '').trim()) {
    return `请填写${label}`
  }
  return ''
}

export function textLength(value: string, label: string, min: number, max: number) {
  const length = value.trim().length
  if (length < min || length > max) {
    return `${label}长度需为 ${min}-${max} 位`
  }
  return ''
}

export function maxTextLength(value: string | undefined | null, label: string, max: number) {
  if (String(value ?? '').trim().length > max) {
    return `${label}最多 ${max} 个字符`
  }
  return ''
}
