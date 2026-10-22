const operator = /^[+−\-×÷]$/

export function appendCalculatorKey(expression: string, key: string): string {
  if (expression.length >= 100 || !/^[\d.+\-×÷]$/.test(key)) return expression
  if (operator.test(key)) {
    if (!expression) return ''
    return operator.test(expression.slice(-1)) ? expression.slice(0, -1) + key : expression + key
  }
  const current = expression.split(/[+\-×÷]/).at(-1) || ''
  if (key === '.') return current.includes('.') ? expression : expression + (current ? '.' : '0.')
  return current === '0' ? expression.slice(0, -1) + key : expression + key
}

type Fraction = { numerator: bigint; denominator: bigint }

/** Keep decimal operations exact until the final rounding to cents. */
export function calculateAmount(expression: string): { value?: string; error?: string } {
  if (!expression || expression.length > 100 || !/^\d+(?:\.\d*)?(?:[+\-×÷]\d+(?:\.\d*)?)*$/.test(expression)) {
    return { error: '请完成计算式' }
  }
  const tokens = expression.match(/\d+(?:\.\d*)?|[+\-×÷]/g)!
  const values: Fraction[] = []
  const operators: string[] = []
  const precedence = (op: string) => op === '×' || op === '÷' ? 2 : 1
  function apply() {
    const right = values.pop()!
    const left = values.pop()!
    const op = operators.pop()!
    if (op === '÷' && right.numerator === 0n) throw new Error('除数不能为 0')
    if (op === '×') values.push({ numerator: left.numerator * right.numerator, denominator: left.denominator * right.denominator })
    else if (op === '÷') values.push({ numerator: left.numerator * right.denominator, denominator: left.denominator * right.numerator })
    else values.push({
      numerator: left.numerator * right.denominator + (op === '+' ? 1n : -1n) * right.numerator * left.denominator,
      denominator: left.denominator * right.denominator
    })
  }
  try {
    for (const token of tokens) {
      if (operator.test(token)) {
        while (operators.length && precedence(operators.at(-1)!) >= precedence(token)) apply()
        operators.push(token)
      } else {
        const decimals = token.split('.')[1]?.length || 0
        values.push({ numerator: BigInt(token.replace('.', '')), denominator: 10n ** BigInt(decimals) })
      }
    }
    while (operators.length) apply()
    let { numerator, denominator } = values[0]!
    if (denominator < 0n) { numerator = -numerator; denominator = -denominator }
    if (numerator <= 0n) return { error: '金额必须大于 0' }
    const cents = (numerator * 200n + denominator) / (denominator * 2n)
    if (cents < 1n) return { error: '金额至少为 0.01 元' }
    if (cents > 999999999999n) return { error: '金额最多 10 位整数' }
    return { value: `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}` }
  } catch (error) {
    return { error: error instanceof Error ? error.message : '无法计算，请检查输入' }
  }
}
