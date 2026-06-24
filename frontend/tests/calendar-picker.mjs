import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/utils/calendarPicker.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022
  }
})
const moduleUrl = `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
const {
  buildCalendarMonth,
  buildMonthGrid,
  buildYearGrid,
  clampDateParts,
  formatDateParts
} = await import(moduleUrl)

const june = buildCalendarMonth(2026, 6, {
  selectedDate: '2026-06-24',
  minDate: new Date(2026, 5, 3),
  maxDate: new Date(2026, 5, 25)
})

assert.deepEqual(june.weekdays, ['一', '二', '三', '四', '五', '六', '日'])
assert.equal(june.weeks[0][0].date, '2026-06-01')
assert.equal(june.weeks[0][0].inCurrentMonth, true)
assert.equal(june.weeks[0][0].disabled, true)
assert.equal(june.weeks[0][2].date, '2026-06-03')
assert.equal(june.weeks[0][2].disabled, false)
assert.equal(june.weeks[3][2].date, '2026-06-24')
assert.equal(june.weeks[3][2].selected, true)

const restrictedJune = buildCalendarMonth(2026, 6, {
  selectedDate: '2026-06-24',
  availableDates: ['2026-06-18', '2026-06-24']
})
assert.equal(restrictedJune.weeks[3][2].date, '2026-06-24')
assert.equal(restrictedJune.weeks[3][2].disabled, false)
assert.equal(restrictedJune.weeks[2][3].date, '2026-06-18')
assert.equal(restrictedJune.weeks[2][3].disabled, false)
assert.equal(restrictedJune.weeks[2][2].date, '2026-06-17')
assert.equal(restrictedJune.weeks[2][2].disabled, true)

assert.deepEqual(clampDateParts({ year: 2026, month: 2, day: 31 }), {
  year: 2026,
  month: 2,
  day: 28
})
assert.equal(formatDateParts({ year: 2026, month: 6, day: 4 }, 'date'), '2026-06-04')
assert.equal(formatDateParts({ year: 2026, month: 6, day: 4, hour: 9, minute: 5 }, 'datetime'), '2026-06-04T09:05')
assert.equal(formatDateParts({ year: 2026, month: 6, day: 4 }, 'month'), '2026-06')
assert.equal(formatDateParts({ year: 2026, month: 6, day: 4 }, 'year'), '2026')

const months = buildMonthGrid(2026, '2026-06', new Date(2026, 2, 1), new Date(2026, 8, 30))
assert.equal(months[0].value, '2026-01')
assert.equal(months[0].disabled, true)
assert.equal(months[5].selected, true)
assert.equal(months[8].disabled, false)
assert.equal(months[9].disabled, true)

const years = buildYearGrid(2026, '2026', new Date(2024, 0, 1), new Date(2028, 11, 31))
assert.equal(years[0].value, '2020')
assert.equal(years[0].disabled, true)
assert.equal(years[6].selected, true)
assert.equal(years[8].disabled, false)
assert.equal(years[9].disabled, true)

assert.equal(buildYearGrid(2036, '2036', new Date(2032, 0, 1), new Date(2038, 11, 31))[1].disabled, true)
assert.equal(buildYearGrid(2036, '2036', new Date(2032, 0, 1), new Date(2038, 11, 31))[2].disabled, false)
