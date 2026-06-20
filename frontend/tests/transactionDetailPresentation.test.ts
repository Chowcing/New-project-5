import { strict as assert } from 'node:assert'
import {
  displayTransactionDateTime,
  transactionChannelText,
  transactionImageCountText,
  transactionLedgerItems,
  transactionPlaceLabel,
  transactionPlaceValue,
  transactionSummaryChips,
  transactionTypeText
} from '../src/utils/transactionDetailPresentation.ts'
import type { TransactionRecord } from '../src/types.ts'

const offlineRecord: TransactionRecord = {
  id: 12,
  type: 'EXPENSE',
  itemName: '午餐',
  amount: 36.8,
  occurredAt: '2026-06-20T12:24:30',
  channel: 'OFFLINE',
  offlinePlace: '港式茶餐厅',
  paymentMethodId: 2,
  paymentMethodName: '招商银行卡',
  categoryId: 1,
  categoryName: '餐饮',
  categoryIcon: 'shop-o',
  note: '和同事吃午饭，含饮品。',
  images: [
    {
      id: 7,
      originalFilename: 'receipt.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 1024,
      url: '',
      sortOrder: 0
    }
  ]
}

const onlineIncomeRecord: TransactionRecord = {
  ...offlineRecord,
  id: 13,
  type: 'INCOME',
  itemName: '',
  amount: 8800,
  occurredAt: '2026-06-20 09:05:00',
  channel: 'ONLINE',
  onlineApp: '公司系统',
  offlinePlace: undefined,
  paymentMethodName: '工资卡',
  categoryName: '工资',
  note: '',
  images: []
}

assert.equal(displayTransactionDateTime('2026-06-20T12:24:30'), '2026年06月20日 12:24')
assert.equal(displayTransactionDateTime('2026-06-20 09:05:00'), '2026年06月20日 09:05')
assert.equal(displayTransactionDateTime('bad-value'), 'bad-value')

assert.equal(transactionTypeText(offlineRecord), '支出')
assert.equal(transactionTypeText(onlineIncomeRecord), '收入')
assert.equal(transactionChannelText(offlineRecord), '线下')
assert.equal(transactionChannelText(onlineIncomeRecord), '线上')

assert.equal(transactionPlaceLabel(offlineRecord), '线下地点')
assert.equal(transactionPlaceValue(offlineRecord), '港式茶餐厅')
assert.equal(transactionPlaceLabel(onlineIncomeRecord), '线上平台')
assert.equal(transactionPlaceValue(onlineIncomeRecord), '公司系统')

assert.equal(transactionImageCountText(offlineRecord), '1 张凭证')
assert.equal(transactionImageCountText(onlineIncomeRecord), '无凭证')

assert.deepEqual(transactionSummaryChips(offlineRecord), [
  '支出',
  '线下',
  '餐饮',
  '招商银行卡',
  '港式茶餐厅',
  '1 张凭证'
])

assert.deepEqual(transactionSummaryChips(onlineIncomeRecord), [
  '收入',
  '线上',
  '工资',
  '工资卡',
  '公司系统'
])

assert.deepEqual(transactionLedgerItems(offlineRecord), [
  {
    key: 'occurredAt',
    icon: 'clock-o',
    label: '发生时间',
    value: '2026年06月20日 12:24'
  },
  {
    key: 'category',
    icon: 'apps-o',
    label: '分类',
    value: '餐饮',
    description: '支出分类'
  },
  {
    key: 'payment',
    icon: 'balance-o',
    label: '支付方式',
    value: '招商银行卡'
  },
  {
    key: 'place',
    icon: 'location-o',
    label: '线下地点',
    value: '港式茶餐厅',
    description: '线下'
  },
  {
    key: 'note',
    icon: 'comment-o',
    label: '备注',
    value: '和同事吃午饭，含饮品。'
  }
])

assert.equal(transactionLedgerItems(onlineIncomeRecord)[4].value, '无备注')
